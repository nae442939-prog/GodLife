package com.godlife.backend.message;

import org.springframework.jdbc.core.namedparam.MapSqlParameterSource;
import org.springframework.jdbc.core.namedparam.NamedParameterJdbcTemplate;
import org.springframework.stereotype.Repository;

import java.util.List;
import java.util.Optional;

/**
 * 메시지 요청(dm_threads)과 '같은 챌린지 참가자인지' 확인. 두 사람 사이 한 줄이라 엔티티 없이 SQL 로 다룬다.
 */
@Repository
public class DmThreadRepository {

    /** 요청 상태 */
    public enum Status {
        PENDING, ACCEPTED, DECLINED
    }

    public record Thread(Long requesterId, Status status) {
    }

    private final NamedParameterJdbcTemplate jdbc;

    public DmThreadRepository(NamedParameterJdbcTemplate jdbc) {
        this.jdbc = jdbc;
    }

    public Optional<Thread> find(Long a, Long b) {
        List<Thread> rows = jdbc.query("""
                SELECT requester_id, status FROM dm_threads WHERE low_id = :low AND high_id = :high
                """, pair(a, b), (rs, i) -> new Thread(rs.getLong("requester_id"), Status.valueOf(rs.getString("status"))));
        return rows.stream().findFirst();
    }

    /** 첫 요청. 이미 있으면 그대로 (동시에 두 번 보내도 한 줄) */
    public void request(Long requester, Long other) {
        jdbc.update("""
                INSERT IGNORE INTO dm_threads (low_id, high_id, requester_id, status)
                VALUES (:low, :high, :requester, 'PENDING')
                """, pair(requester, other).addValue("requester", requester));
    }

    public void setStatus(Long a, Long b, Status status) {
        jdbc.update("UPDATE dm_threads SET status = :status WHERE low_id = :low AND high_id = :high",
                pair(a, b).addValue("status", status.name()));
    }

    /** 내가 낀 대기 중(PENDING) 요청의 상대 id 들 (보낸 것 · 받은 것 모두) */
    public List<Long> pendingPartners(Long me) {
        return jdbc.queryForList("""
                SELECT IF(low_id = :me, high_id, low_id) FROM dm_threads
                WHERE (low_id = :me OR high_id = :me) AND status = 'PENDING'
                """, new MapSqlParameterSource("me", me), Long.class);
    }

    /**
     * 수락 전에 보낸 사람이 보낸 메시지 수 (요청은 3개까지).
     * 요청을 시작한 뒤의 것만 센다 (예전에 맞팔로우였을 때 나눈 대화는 빼고).
     */
    public long pendingSent(Long sender, Long receiver) {
        Long n = jdbc.queryForObject("""
                SELECT COUNT(*) FROM direct_messages m
                  JOIN dm_threads t ON t.low_id = m.low_id AND t.high_id = m.high_id
                WHERE m.sender_id = :s AND m.receiver_id = :r AND m.created_at >= t.created_at
                """, new MapSqlParameterSource("s", sender).addValue("r", receiver), Long.class);
        return n == null ? 0 : n;
    }

    /**
     * 두 사람이 같은 챌린지의 멤버인지 (참가 중·성공·실패, 또는 개설자).
     * 같은 챌린지 사이는 맞팔로우가 아니어도 바로 대화할 수 있다.
     */
    public boolean sharesChallenge(Long a, Long b) {
        Boolean shared = jdbc.queryForObject("""
                SELECT EXISTS (
                    SELECT 1 FROM challenge_participants x
                      JOIN challenge_participants y ON y.challenge_id = x.challenge_id
                    WHERE x.user_id = :a AND y.user_id = :b
                      AND x.status IN ('ACTIVE', 'COMPLETED', 'FAILED')
                      AND y.status IN ('ACTIVE', 'COMPLETED', 'FAILED')
                ) OR EXISTS (
                    SELECT 1 FROM challenges c JOIN challenge_participants y ON y.challenge_id = c.id
                    WHERE ((c.host_id = :a AND y.user_id = :b) OR (c.host_id = :b AND y.user_id = :a))
                      AND y.status IN ('ACTIVE', 'COMPLETED', 'FAILED')
                )
                """, new MapSqlParameterSource("a", a).addValue("b", b), Boolean.class);
        return Boolean.TRUE.equals(shared);
    }

    private static MapSqlParameterSource pair(Long a, Long b) {
        return new MapSqlParameterSource("low", Math.min(a, b)).addValue("high", Math.max(a, b));
    }
}
