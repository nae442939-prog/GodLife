package com.godlife.backend.device;

import com.godlife.backend.common.crypto.Hashing;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.jdbc.core.namedparam.MapSqlParameterSource;
import org.springframework.jdbc.core.namedparam.NamedParameterJdbcTemplate;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.time.Clock;
import java.time.LocalDateTime;
import java.util.ArrayList;
import java.util.Comparator;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.Set;
import java.util.regex.Pattern;

/**
 * 기기 핑거프린팅 — 같은 기기 · 같은 IP 로 여러 계정을 만드는 것(다중 계정)을 찾는 기초 버전.
 * 브라우저가 화면 · 시간대 · 캔버스 같은 값으로 만든 지문을 가입 · 로그인 · 토큰 재발급 때 보내면,
 * 그 해시만 회원별로 남긴다(원문은 저장하지 않는다).
 * - 같은 지문을 쓴 계정이 {@value #MIN_PER_DEVICE}개 이상, 같은 IP 를 쓴 계정이 {@value #MIN_PER_IP}개 이상이면 관리자 화면에 올린다.
 * - 표시만 한다. 같은 기종끼리 지문이 겹칠 수 있고 가족 · 학교가 IP 를 같이 쓰기도 해서, 가입 · 로그인을 막지 않고 관리자가 보고 판단한다.
 * - 지문이 없거나 이상한 요청도 그대로 통과시킨다(기록만 안 남는다).
 */
@Slf4j
@Service
@RequiredArgsConstructor
public class DeviceService {

    public static final String HEADER = "X-Device-Fingerprint";
    static final int MIN_PER_DEVICE = 3;
    static final int MIN_PER_IP = 5;
    private static final Pattern FINGERPRINT = Pattern.compile("[A-Za-z0-9_-]{16,128}");
    /** 개발 PC 에서는 모두 같은 주소라 IP 묶음에서 뺀다 */
    private static final Set<String> LOOPBACK = Set.of("127.0.0.1", "::1", "0:0:0:0:0:0:0:1");

    private final NamedParameterJdbcTemplate jdbc;
    private final Clock clock;

    /** @param email 가운데를 가린 이메일 */
    public record Member(Long userId, String nickname, String email, String status, LocalDateTime joinedAt,
                         LocalDateTime lastSeenAt) {
    }

    /**
     * @param kind DEVICE(같은 기기 지문) / IP(같은 IP)
     * @param key  지문 해시 앞 12자 또는 IP 주소
     */
    public record Group(String kind, String key, int accounts, LocalDateTime lastSeenAt, List<Member> members) {
    }

    /** 이 회원이 이 기기를 썼다고 남긴다. 실패해도 가입 · 로그인은 그대로 되어야 해서 예외를 밖으로 내지 않는다. */
    public void record(Long userId, String fingerprint, String ip, String userAgent) {
        if (userId == null || fingerprint == null || !FINGERPRINT.matcher(fingerprint).matches()) {
            return;
        }
        try {
            jdbc.update("""
                    INSERT INTO devices (user_id, fingerprint_hash, ip_address, user_agent, first_seen_at, last_seen_at)
                    VALUES (:user, :hash, :ip, :agent, :now, :now)
                    ON DUPLICATE KEY UPDATE ip_address = VALUES(ip_address), user_agent = VALUES(user_agent),
                        last_seen_at = VALUES(last_seen_at)
                    """, new MapSqlParameterSource("user", userId).addValue("hash", Hashing.sha256Hex(fingerprint))
                    .addValue("ip", cut(ip == null ? "" : ip, 45)).addValue("agent", cut(userAgent, 255))
                    .addValue("now", LocalDateTime.now(clock)));
        } catch (RuntimeException e) {
            log.warn("기기 기록 실패 (user={})", userId, e);
        }
    }

    /** 관리자: 여러 계정이 함께 쓴 기기 · IP. 계정이 많은 묶음부터 */
    @Transactional(readOnly = true)
    public List<Group> suspicious() {
        List<Group> groups = new ArrayList<>();
        groups.addAll(groups("DEVICE", "fingerprint_hash", MIN_PER_DEVICE));
        groups.addAll(groups("IP", "ip_address", MIN_PER_IP));
        groups.sort(Comparator.comparingInt(Group::accounts).reversed()
                .thenComparing(Group::lastSeenAt, Comparator.reverseOrder()));
        return groups;
    }

    /** column 은 이 클래스 안에서만 넘기는 고정 값이다 (사용자 입력이 아님) */
    private List<Group> groups(String kind, String column, int min) {
        Map<String, Map<Long, Member>> byKey = new LinkedHashMap<>();
        jdbc.query("""
                SELECT d.%1$s AS k, u.id, u.nickname, u.email, u.status, u.created_at, d.last_seen_at
                FROM devices d JOIN users u ON u.id = d.user_id
                WHERE d.%1$s IN (SELECT k FROM (SELECT %1$s AS k FROM devices GROUP BY %1$s
                                              HAVING COUNT(DISTINCT user_id) >= :min) flagged)
                ORDER BY d.%1$s, u.created_at, u.id
                """.formatted(column), new MapSqlParameterSource("min", min), rs -> {
            String key = rs.getString("k");
            if ("IP".equals(kind) && LOOPBACK.contains(key)) {
                return;
            }
            Member member = new Member(rs.getLong("id"), rs.getString("nickname"), mask(rs.getString("email")),
                    rs.getString("status"), rs.getTimestamp("created_at").toLocalDateTime(),
                    rs.getTimestamp("last_seen_at").toLocalDateTime());
            // 한 회원이 같은 IP 에서 기기를 여러 개 썼으면 가장 최근 것만 남긴다
            byKey.computeIfAbsent(key, k -> new LinkedHashMap<>()).merge(member.userId(), member,
                    (a, b) -> a.lastSeenAt().isAfter(b.lastSeenAt()) ? a : b);
        });
        List<Group> groups = new ArrayList<>();
        byKey.forEach((key, members) -> groups.add(new Group(kind, "DEVICE".equals(kind) ? key.substring(0, 12) : key,
                members.size(), members.values().stream().map(Member::lastSeenAt).max(LocalDateTime::compareTo)
                .orElseThrow(), List.copyOf(members.values()))));
        return groups;
    }

    /** ab***@example.com */
    private static String mask(String email) {
        int at = email.indexOf('@');
        if (at <= 0) {
            return "***";
        }
        return email.substring(0, Math.min(2, at)) + "***" + email.substring(at);
    }

    private static String cut(String value, int max) {
        return value == null || value.length() <= max ? value : value.substring(0, max);
    }
}
