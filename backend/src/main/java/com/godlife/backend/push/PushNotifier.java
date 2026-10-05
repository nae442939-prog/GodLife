package com.godlife.backend.push;

import com.godlife.backend.common.crypto.Hashing;
import com.godlife.backend.common.error.BusinessException;
import com.godlife.backend.common.error.ErrorCode;
import com.godlife.backend.notification.NotificationPushEvent;
import jakarta.annotation.PreDestroy;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.jdbc.core.namedparam.MapSqlParameterSource;
import org.springframework.jdbc.core.namedparam.NamedParameterJdbcTemplate;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;
import org.springframework.transaction.event.TransactionalEventListener;
import tools.jackson.databind.ObjectMapper;

import java.net.URI;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.concurrent.ExecutorService;
import java.util.concurrent.Executors;
import java.util.regex.Pattern;

/**
 * 푸시 알림(웹 푸시). 알림함에 알림이 생길 때, 그 회원이 푸시를 켜 둔 브라우저가 있으면 같은 내용을 푸시로도 보낸다.
 * - 구독: 브라우저가 만든 구독(endpoint · 키 2개)을 회원별로 남긴다. 한 회원이 기기 여러 대를 켤 수 있다({@value #MAX_PER_USER}개까지).
 * - endpoint 는 서버가 요청을 보낼 주소라서, 알려진 브라우저 푸시 서비스 주소만 받는다 (아무 주소나 받으면 서버가 엉뚱한 곳을 찌르게 된다).
 * - 전송은 알림이 저장(커밋)된 뒤 따로 도는 스레드에서 한다 — 푸시 서비스가 느려도 요청 · 자정 배치가 기다리지 않는다.
 * - 푸시 서비스가 404 · 410 을 주면 사라진 구독이라 지운다.
 * 알림 종류별 켜기/끄기는 알림함과 같다 (끈 종류는 알림 자체가 안 만들어지므로 푸시도 안 간다).
 */
@Slf4j
@Service
@RequiredArgsConstructor
public class PushNotifier {

    static final int MAX_PER_USER = 10;
    private static final List<String> PUSH_HOSTS = List.of("fcm.googleapis.com", ".push.services.mozilla.com",
            ".notify.windows.com", ".push.apple.com");
    private static final Pattern KEY = Pattern.compile("[A-Za-z0-9_-]{16,200}={0,2}");

    private final NamedParameterJdbcTemplate jdbc;
    private final PushGateway gateway;
    private final ObjectMapper objectMapper;
    private final ExecutorService sender = Executors.newVirtualThreadPerTaskExecutor();

    /** @param publicKey 브라우저가 구독할 때 쓰는 서버 공개 키 (꺼져 있으면 빈 문자열) */
    public record Config(boolean enabled, String publicKey) {
    }

    private record Target(Long id, String endpoint, String p256dh, String auth) {
    }

    public Config config() {
        return new Config(gateway.enabled(), gateway.publicKey());
    }

    /** 이 브라우저의 구독을 내 것으로 남긴다. 같은 브라우저를 다른 회원이 쓰게 되면 그 회원 것으로 바뀐다. */
    @Transactional
    public void subscribe(Long userId, String endpoint, String p256dh, String auth) {
        if (!gateway.enabled()) {
            throw new BusinessException(ErrorCode.VALIDATION_ERROR, "지금은 푸시 알림을 쓸 수 없어요.");
        }
        if (!allowed(endpoint) || p256dh == null || auth == null || !KEY.matcher(p256dh).matches()
                || !KEY.matcher(auth).matches()) {
            throw new BusinessException(ErrorCode.VALIDATION_ERROR, "푸시 알림 정보를 확인할 수 없어요.");
        }
        jdbc.update("""
                INSERT INTO push_subscriptions (user_id, endpoint, endpoint_hash, p256dh, auth)
                VALUES (:user, :endpoint, :hash, :p256dh, :auth)
                ON DUPLICATE KEY UPDATE user_id = VALUES(user_id), p256dh = VALUES(p256dh), auth = VALUES(auth)
                """, new MapSqlParameterSource("user", userId).addValue("endpoint", endpoint)
                .addValue("hash", Hashing.sha256Hex(endpoint)).addValue("p256dh", p256dh).addValue("auth", auth));
        // 기기를 계속 바꿔 가며 쌓지 못하게, 오래된 구독부터 지운다
        jdbc.update("""
                DELETE FROM push_subscriptions WHERE user_id = :user AND id NOT IN (
                    SELECT id FROM (SELECT id FROM push_subscriptions WHERE user_id = :user
                                    ORDER BY id DESC LIMIT :max) recent)
                """, new MapSqlParameterSource("user", userId).addValue("max", MAX_PER_USER));
    }

    /** 이 브라우저의 푸시 끄기 (내 구독이 아니면 아무 일도 없다) */
    @Transactional
    public void unsubscribe(Long userId, String endpoint) {
        if (endpoint == null) {
            return;
        }
        jdbc.update("DELETE FROM push_subscriptions WHERE user_id = :user AND endpoint_hash = :hash",
                new MapSqlParameterSource("user", userId).addValue("hash", Hashing.sha256Hex(endpoint)));
    }

    /** 알림이 저장(커밋)된 뒤에 온다. 보내는 일은 따로 도는 스레드에 맡긴다. */
    @TransactionalEventListener(fallbackExecution = true)
    public void onNotification(NotificationPushEvent event) {
        if (gateway.enabled()) {
            sender.execute(() -> deliver(event));
        }
    }

    /** 이 회원의 구독 모두에 보낸다. 보낸(푸시 서비스가 받은) 수를 돌려준다. */
    int deliver(NotificationPushEvent event) {
        try {
            List<Target> targets = jdbc.query("""
                    SELECT id, endpoint, p256dh, auth FROM push_subscriptions WHERE user_id = :user
                    """, new MapSqlParameterSource("user", event.userId()),
                    (rs, i) -> new Target(rs.getLong("id"), rs.getString("endpoint"), rs.getString("p256dh"),
                            rs.getString("auth")));
            if (targets.isEmpty()) {
                return 0;
            }
            Map<String, String> payload = new LinkedHashMap<>();
            payload.put("title", event.title());
            payload.put("body", event.body());
            payload.put("link", event.link() == null ? "/" : event.link());
            String json = objectMapper.writeValueAsString(payload);
            int sent = 0;
            for (Target t : targets) {
                int status = gateway.send(t.endpoint(), t.p256dh(), t.auth(), json);
                if (status == 404 || status == 410) {
                    jdbc.update("DELETE FROM push_subscriptions WHERE id = :id",
                            new MapSqlParameterSource("id", t.id()));
                } else if (status >= 200 && status < 300) {
                    sent++;
                } else {
                    log.warn("푸시 전송이 받아들여지지 않음 (subscription={}, status={})", t.id(), status);
                }
            }
            return sent;
        } catch (RuntimeException e) {
            log.warn("푸시 전송 실패 (user={})", event.userId(), e);
            return 0;
        }
    }

    /** https 이고 알려진 브라우저 푸시 서비스 주소인지 */
    private static boolean allowed(String endpoint) {
        if (endpoint == null || endpoint.length() > 1000) {
            return false;
        }
        try {
            URI uri = URI.create(endpoint);
            String host = uri.getHost();
            if (!"https".equals(uri.getScheme()) || host == null || uri.getUserInfo() != null
                    || (uri.getPort() != -1 && uri.getPort() != 443)) {
                return false;
            }
            return PUSH_HOSTS.stream().anyMatch(h -> h.startsWith(".") ? host.endsWith(h) : host.equals(h));
        } catch (IllegalArgumentException e) {
            return false;
        }
    }

    @PreDestroy
    void shutdown() {
        sender.shutdown();
    }
}
