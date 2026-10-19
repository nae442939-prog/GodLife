package com.godlife.backend.push;

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

import java.util.List;
import java.util.concurrent.ExecutorService;
import java.util.concurrent.Executors;
import java.util.regex.Pattern;

/**
 * 앱 푸시(안드로이드, FCM). 웹 푸시({@link PushNotifier})와 같은 알림을 앱을 설치한 휴대폰으로도 보낸다.
 * - 토큰: 앱이 로그인하면 FCM 이 준 기기 토큰을 push_tokens 에 남기고, 로그아웃하면 지운다.
 *   같은 휴대폰을 다른 회원이 로그인해 쓰면 그 회원 것으로 바뀐다. 한 회원 {@value #MAX_PER_USER}대까지.
 * - 전송은 알림이 저장(커밋)된 뒤 따로 도는 스레드에서 한다. FCM 이 없어진 토큰이라고 하면 지운다.
 * 알림 종류별 켜기/끄기는 알림함과 같다 (끈 종류는 알림 자체가 안 만들어지므로 푸시도 안 간다).
 */
@Slf4j
@Service
@RequiredArgsConstructor
public class AppPushNotifier {

    static final int MAX_PER_USER = 10;
    /** FCM 등록 토큰 모양 (보통 150~170자) */
    private static final Pattern TOKEN = Pattern.compile("[A-Za-z0-9:_-]{20,255}");

    private final NamedParameterJdbcTemplate jdbc;
    private final AppPushGateway gateway;
    private final ExecutorService sender = Executors.newVirtualThreadPerTaskExecutor();

    private record Target(Long id, String token) {
    }

    /** 이 휴대폰의 토큰을 내 것으로 남긴다. 서버에 키가 아직 없어도 받아 둔다 (키를 넣으면 바로 간다). */
    @Transactional
    public void register(Long userId, String token) {
        if (token == null || !TOKEN.matcher(token).matches()) {
            throw new BusinessException(ErrorCode.VALIDATION_ERROR, "푸시 알림 정보를 확인할 수 없어요.");
        }
        jdbc.update("""
                INSERT INTO push_tokens (user_id, token, platform) VALUES (:user, :token, 'ANDROID')
                ON DUPLICATE KEY UPDATE user_id = VALUES(user_id)
                """, new MapSqlParameterSource("user", userId).addValue("token", token));
        // 기기를 계속 바꿔 가며 쌓지 못하게, 오래된 토큰부터 지운다
        jdbc.update("""
                DELETE FROM push_tokens WHERE user_id = :user AND platform = 'ANDROID' AND id NOT IN (
                    SELECT id FROM (SELECT id FROM push_tokens WHERE user_id = :user AND platform = 'ANDROID'
                                    ORDER BY id DESC LIMIT :max) recent)
                """, new MapSqlParameterSource("user", userId).addValue("max", MAX_PER_USER));
    }

    /** 이 휴대폰의 앱 푸시 끄기 (로그아웃할 때. 내 토큰이 아니면 아무 일도 없다) */
    @Transactional
    public void unregister(Long userId, String token) {
        if (token == null) {
            return;
        }
        jdbc.update("DELETE FROM push_tokens WHERE user_id = :user AND token = :token",
                new MapSqlParameterSource("user", userId).addValue("token", token));
    }

    /** 알림이 저장(커밋)된 뒤에 온다. 보내는 일은 따로 도는 스레드에 맡긴다. */
    @TransactionalEventListener(fallbackExecution = true)
    public void onNotification(NotificationPushEvent event) {
        if (gateway.enabled()) {
            sender.execute(() -> deliver(event));
        }
    }

    /** 이 회원의 휴대폰 모두에 보낸다. 보낸(FCM 이 받은) 수를 돌려준다. */
    int deliver(NotificationPushEvent event) {
        try {
            List<Target> targets = jdbc.query("""
                    SELECT id, token FROM push_tokens WHERE user_id = :user AND platform = 'ANDROID'
                    """, new MapSqlParameterSource("user", event.userId()),
                    (rs, i) -> new Target(rs.getLong("id"), rs.getString("token")));
            int sent = 0;
            for (Target t : targets) {
                switch (gateway.send(t.token(), event.title(), event.body(), event.link())) {
                    case SENT -> sent++;
                    case GONE -> jdbc.update("DELETE FROM push_tokens WHERE id = :id",
                            new MapSqlParameterSource("id", t.id()));
                    case FAILED -> log.warn("앱 푸시 전송 실패 (token id={})", t.id());
                }
            }
            return sent;
        } catch (RuntimeException e) {
            log.warn("앱 푸시 전송 실패 (user={})", event.userId(), e);
            return 0;
        }
    }

    @PreDestroy
    void shutdown() {
        sender.shutdown();
    }
}
