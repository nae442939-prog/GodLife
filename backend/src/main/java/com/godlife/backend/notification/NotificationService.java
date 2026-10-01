package com.godlife.backend.notification;

import com.godlife.backend.notification.Notification.Type;
import lombok.RequiredArgsConstructor;
import org.springframework.jdbc.core.namedparam.MapSqlParameterSource;
import org.springframework.jdbc.core.namedparam.NamedParameterJdbcTemplate;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.sql.Timestamp;
import java.time.Clock;
import java.time.LocalDate;
import java.time.LocalDateTime;
import java.util.List;

/**
 * 알림함. 알림은 서버가 만들어 쌓아 두고, 화면(헤더의 종)이 불러와 보여 준다. (휴대폰 푸시는 앱을 만들 때 붙인다)
 * - 같은 알림은 한 번만: (회원, dedupe_key) 가 같으면 다시 만들지 않는다.
 * - 회원이 설정에서 끈 종류는 만들지 않는다 (인증 알림 / 챌린지 결과 / 팔로우·메시지). 문의 답변 · 신고 알림은 항상 간다.
 */
@Service
@RequiredArgsConstructor
public class NotificationService {

    public static final int PAGE = 50;

    /** @param link 누르면 갈 화면 주소 (없으면 null) */
    public record NotificationItem(Long id, String type, String title, String body, String link, boolean read,
                                   LocalDateTime sentAt) {
    }

    /** 알림 설정: 종류별 켜기/끄기 */
    public record Settings(boolean verifyReminder, boolean challengeResult, boolean social) {
    }

    private final NamedParameterJdbcTemplate jdbc;
    private final Clock clock;

    /** 알림 하나 만들기. 회원이 그 종류를 꺼 두었거나 같은 알림이 이미 있으면 만들지 않는다. */
    @Transactional
    public void notify(Long userId, Type type, String title, String body, String link, String dedupeKey) {
        if (!enabled(userId, type)) {
            return;
        }
        jdbc.update("""
                INSERT IGNORE INTO notifications (user_id, type, title, body, link, dedupe_key)
                VALUES (:user, :type, :title, :body, :link, :key)
                """, new MapSqlParameterSource("user", userId).addValue("type", type.name())
                .addValue("title", title).addValue("body", cut(body)).addValue("link", link)
                .addValue("key", dedupeKey));
    }

    /**
     * 오늘 인증하는 날 알림: 진행 중인 챌린지의 참가자 중 오늘 아직 인증하지 않은 사람에게 챌린지마다 하나씩.
     * 주 N회 챌린지는 이번 주 횟수를 이미 채운 사람을 뺀다. 하루에 여러 번 불러도 한 번만 만들어진다.
     */
    @Transactional
    public int remindToday(LocalDate today) {
        return jdbc.update("""
                INSERT IGNORE INTO notifications (user_id, type, title, body, link, dedupe_key)
                SELECT p.user_id, 'VERIFY_REMINDER', '오늘 인증하는 날이에요!',
                       CONCAT('''', c.title, ''' 챌린지 인증을 잊지 마세요.'),
                       CONCAT('/challenges/', c.id),
                       CONCAT('verify:', c.id, ':', :today)
                FROM challenge_participants p
                  JOIN challenges c ON c.id = p.challenge_id
                  JOIN users u ON u.id = p.user_id AND u.status = 'ACTIVE'
                  LEFT JOIN notification_settings s ON s.user_id = p.user_id
                WHERE p.status = 'ACTIVE' AND c.status IN ('RECRUITING', 'ONGOING')
                  AND c.start_date <= :today AND c.end_date >= :today
                  AND COALESCE(s.verify_reminder, TRUE)
                  AND NOT EXISTS (SELECT 1 FROM verifications v
                                  WHERE v.participant_id = p.id AND v.verify_date = :today)
                  AND (c.frequency_type = 'DAILY'
                       OR (SELECT COUNT(*) FROM verifications v
                           WHERE v.participant_id = p.id
                             AND v.verify_date >= DATE_ADD(c.start_date,
                                     INTERVAL FLOOR(DATEDIFF(:today, c.start_date) / 7) * 7 DAY)) < c.weekly_count)
                """, new MapSqlParameterSource("today", today.toString()));
    }

    /** 할 일을 끝낸 알림은 읽음으로 바꾼다 (오늘 인증을 올리면 그 챌린지의 인증 알림이 사라지게) */
    @Transactional
    public void resolve(Long userId, String dedupeKey) {
        jdbc.update("""
                UPDATE notifications SET read_at = :now
                WHERE user_id = :user AND dedupe_key = :key AND read_at IS NULL
                """, new MapSqlParameterSource("user", userId).addValue("key", dedupeKey)
                .addValue("now", LocalDateTime.now(clock)));
    }

    /** 내 알림 (최근 것부터 50개) */
    @Transactional(readOnly = true)
    public List<NotificationItem> list(Long userId) {
        return jdbc.query("""
                SELECT id, type, title, body, link, read_at, sent_at FROM notifications
                WHERE user_id = :user ORDER BY id DESC LIMIT :limit
                """, new MapSqlParameterSource("user", userId).addValue("limit", PAGE),
                (rs, i) -> {
                    Timestamp read = rs.getTimestamp("read_at");
                    return new NotificationItem(rs.getLong("id"), rs.getString("type"), rs.getString("title"),
                            rs.getString("body"), rs.getString("link"), read != null,
                            rs.getTimestamp("sent_at").toLocalDateTime());
                });
    }

    @Transactional(readOnly = true)
    public long unreadCount(Long userId) {
        Long n = jdbc.queryForObject("SELECT COUNT(*) FROM notifications WHERE user_id = :user AND read_at IS NULL",
                new MapSqlParameterSource("user", userId), Long.class);
        return n == null ? 0 : n;
    }

    /** 알림 하나 읽음 (내 것이 아니면 아무 일도 없다) */
    @Transactional
    public void read(Long userId, Long id) {
        jdbc.update("UPDATE notifications SET read_at = :now WHERE id = :id AND user_id = :user AND read_at IS NULL",
                new MapSqlParameterSource("user", userId).addValue("id", id)
                        .addValue("now", LocalDateTime.now(clock)));
    }

    @Transactional
    public void readAll(Long userId) {
        jdbc.update("UPDATE notifications SET read_at = :now WHERE user_id = :user AND read_at IS NULL",
                new MapSqlParameterSource("user", userId).addValue("now", LocalDateTime.now(clock)));
    }

    /** 알림 설정 (바꾼 적이 없으면 모두 켜짐) */
    @Transactional(readOnly = true)
    public Settings settings(Long userId) {
        List<Settings> rows = jdbc.query("""
                SELECT verify_reminder, challenge_result, social FROM notification_settings WHERE user_id = :user
                """, new MapSqlParameterSource("user", userId),
                (rs, i) -> new Settings(rs.getBoolean("verify_reminder"), rs.getBoolean("challenge_result"),
                        rs.getBoolean("social")));
        return rows.isEmpty() ? new Settings(true, true, true) : rows.get(0);
    }

    @Transactional
    public Settings updateSettings(Long userId, Settings settings) {
        jdbc.update("""
                INSERT INTO notification_settings (user_id, verify_reminder, challenge_result, social)
                VALUES (:user, :verify, :result, :social)
                ON DUPLICATE KEY UPDATE verify_reminder = VALUES(verify_reminder),
                    challenge_result = VALUES(challenge_result), social = VALUES(social)
                """, new MapSqlParameterSource("user", userId).addValue("verify", settings.verifyReminder())
                .addValue("result", settings.challengeResult()).addValue("social", settings.social()));
        return settings;
    }

    private boolean enabled(Long userId, Type type) {
        Settings s = settings(userId);
        return switch (type) {
            case VERIFY_REMINDER -> s.verifyReminder();
            case SETTLEMENT -> s.challengeResult();
            case FOLLOW, MESSAGE_REQUEST -> s.social();
            default -> true;
        };
    }

    /** notifications.body 는 300자 */
    private static String cut(String body) {
        return body.length() <= 300 ? body : body.substring(0, 299) + "…";
    }
}
