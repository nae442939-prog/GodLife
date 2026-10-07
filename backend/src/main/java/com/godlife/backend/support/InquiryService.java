package com.godlife.backend.support;

import com.godlife.backend.common.error.BusinessException;
import com.godlife.backend.common.error.ErrorCode;
import com.godlife.backend.common.ratelimit.RequestThrottle;
import com.godlife.backend.notification.Notification;
import com.godlife.backend.notification.NotificationService;
import lombok.RequiredArgsConstructor;
import org.springframework.jdbc.core.RowMapper;
import org.springframework.jdbc.core.namedparam.MapSqlParameterSource;
import org.springframework.jdbc.core.namedparam.NamedParameterJdbcTemplate;
import org.springframework.jdbc.support.GeneratedKeyHolder;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.sql.Timestamp;
import java.time.Clock;
import java.time.Duration;
import java.time.LocalDateTime;
import java.util.List;

/**
 * 고객센터 1:1 문의. 회원이 남기고 관리자가 답변한다. 본인 문의만 볼 수 있다.
 * 도배 방지: 10분에 5건.
 */
@Service
@RequiredArgsConstructor
public class InquiryService {

    private static final int LIMIT = 5;
    private static final Duration WINDOW = Duration.ofMinutes(10);

    /**
     * 문의 한 건.
     * @param category ACCOUNT / CHALLENGE / POINT / BUG / ETC
     * @param status   WAITING(답변 대기) / ANSWERED(답변 완료)
     */
    public record Inquiry(Long id, String category, String title, String content, String status, String answer,
                          LocalDateTime createdAt, LocalDateTime answeredAt) {
    }

    private static final RowMapper<Inquiry> MAPPER = (rs, i) -> {
        Timestamp answered = rs.getTimestamp("answered_at");
        return new Inquiry(rs.getLong("id"), rs.getString("category"), rs.getString("title"),
                rs.getString("content"), rs.getString("status"), rs.getString("answer"),
                rs.getTimestamp("created_at").toLocalDateTime(), answered == null ? null : answered.toLocalDateTime());
    };

    private final NamedParameterJdbcTemplate jdbc;
    private final RequestThrottle throttle;
    private final NotificationService notificationService;
    private final Clock clock;

    @Transactional
    public Long create(Long userId, String category, String title, String content) {
        throttle.check("inquiry:" + userId, LIMIT, WINDOW);
        GeneratedKeyHolder keys = new GeneratedKeyHolder();
        jdbc.update("""
                INSERT INTO inquiries (user_id, category, title, content) VALUES (:user, :category, :title, :content)
                """, new MapSqlParameterSource("user", userId).addValue("category", category)
                .addValue("title", title.strip()).addValue("content", content.strip()), keys);
        return keys.getKey().longValue();
    }

    /** 내 문의 (최근 것부터) */
    @Transactional(readOnly = true)
    public List<Inquiry> mine(Long userId) {
        return jdbc.query("""
                SELECT id, category, title, content, status, answer, created_at, answered_at
                FROM inquiries WHERE user_id = :user ORDER BY id DESC LIMIT 100
                """, new MapSqlParameterSource("user", userId), MAPPER);
    }

    /** 관리자가 보는 문의: 문의 + 남긴 회원 */
    public record AdminInquiry(Inquiry inquiry, Long userId, String nickname) {
    }

    /** 관리자: 문의 목록 (status 를 주면 그 상태만). 답변 대기는 오래된 것부터, 그 밖은 최근 것부터 */
    @Transactional(readOnly = true)
    public List<AdminInquiry> all(String status) {
        return jdbc.query("""
                SELECT q.id, q.category, q.title, q.content, q.status, q.answer, q.created_at, q.answered_at,
                       u.id AS user_id, u.nickname
                FROM inquiries q JOIN users u ON u.id = q.user_id
                WHERE (:status IS NULL OR q.status = :status)
                ORDER BY (q.status = 'WAITING') DESC, IF(q.status = 'WAITING', q.id, -q.id)
                LIMIT 300
                """, new MapSqlParameterSource("status", status),
                (rs, i) -> new AdminInquiry(MAPPER.mapRow(rs, i), rs.getLong("user_id"), rs.getString("nickname")));
    }

    /** 관리자: 답변 달기 (다시 달면 고친다) */
    @Transactional
    public void answer(Long id, String answer) {
        int updated = jdbc.update("""
                UPDATE inquiries SET answer = :answer, status = 'ANSWERED', answered_at = :now WHERE id = :id
                """, new MapSqlParameterSource("id", id).addValue("answer", answer.strip())
                .addValue("now", LocalDateTime.now(clock)));
        if (updated == 0) {
            throw new BusinessException(ErrorCode.VALIDATION_ERROR, "문의를 찾을 수 없어요.");
        }
        jdbc.query("SELECT user_id, title FROM inquiries WHERE id = :id", new MapSqlParameterSource("id", id), rs -> {
            notificationService.notify(rs.getLong("user_id"), Notification.Type.INQUIRY_ANSWER, "문의에 답변이 달렸어요",
                    rs.getString("title"), "/settings?tab=support", "inquiry:" + id);
        });
    }
}
