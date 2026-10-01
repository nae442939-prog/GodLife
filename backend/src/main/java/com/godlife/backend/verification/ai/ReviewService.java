package com.godlife.backend.verification.ai;

import com.godlife.backend.common.error.BusinessException;
import com.godlife.backend.common.error.ErrorCode;
import com.godlife.backend.common.upload.ImageStore;
import com.godlife.backend.notification.Notification;
import com.godlife.backend.notification.NotificationService;
import lombok.RequiredArgsConstructor;
import org.springframework.jdbc.core.namedparam.MapSqlParameterSource;
import org.springframework.jdbc.core.namedparam.NamedParameterJdbcTemplate;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.math.BigDecimal;
import java.nio.file.Files;
import java.nio.file.Path;
import java.time.Clock;
import java.time.LocalDate;
import java.time.LocalDateTime;
import java.util.List;
import java.util.Map;

/**
 * 관리자 검토: AI 가 애매하다고 넘긴 인증(확신도 낮음 · 예전 사진과 거의 같음)을 사람이 승인하거나 거절한다.
 * - 승인: 인증이 그대로 인정된다.
 * - 거절: 그 인증을 취소한다 (성공 일수 · 연속 기록에서 하루를 빼고, 회원에게 알린다).
 *   그날(주 N회는 그 주) 결과가 이미 기록됐거나 챌린지가 끝났으면 거절할 수 없다
 *   (결과 · 포인트 계산이 그 인증을 넣고 끝난 뒤라서). 그때는 승인만 할 수 있다.
 */
@Service
@RequiredArgsConstructor
public class ReviewService {

    /**
     * 검토할 인증 한 건.
     * @param reason         LOW_CONFIDENCE(AI 확신도 낮음) / DUPLICATE_SUSPECT(예전 사진과 거의 같음) / REPORTED
     * @param status         OPEN / APPROVED / REJECTED
     * @param predictedLabel AI 가 본 라벨, expectedLabel = 챌린지 카테고리의 라벨
     * @param duplicateOfId  가장 닮았던 예전 인증 (비교 사진)
     */
    public record Review(Long id, Long verificationId, String reason, String status, Long challengeId,
                         String challengeTitle, String categoryName, String expectedLabel, Long userId,
                         String nickname, LocalDate verifyDate, String predictedLabel, BigDecimal confidence,
                         BigDecimal maxSimilarity, Long duplicateOfId, String memo) {
    }

    private final NamedParameterJdbcTemplate jdbc;
    private final ImageStore imageStore;
    private final NotificationService notificationService;
    private final Clock clock;

    /** 검토 목록 (status 를 주면 그 상태만). 대기는 오래된 것부터, 그 밖은 최근 것부터 */
    @Transactional(readOnly = true)
    public List<Review> list(String status) {
        return jdbc.query("""
                SELECT q.id, q.verification_id, q.reason, q.status, q.memo, c.id AS challenge_id, c.title,
                       cat.name AS category_name, cat.ai_label, u.id AS user_id, u.nickname, v.verify_date,
                       r.predicted_label, r.confidence, r.max_similarity, r.duplicate_of_id
                FROM review_queue q
                  JOIN verifications v ON v.id = q.verification_id
                  JOIN challenge_participants p ON p.id = v.participant_id
                  JOIN challenges c ON c.id = p.challenge_id
                  JOIN categories cat ON cat.id = c.category_id
                  JOIN users u ON u.id = p.user_id
                  LEFT JOIN ai_inference_results r ON r.verification_id = v.id
                WHERE (:status IS NULL OR q.status = :status)
                ORDER BY (q.status = 'OPEN') DESC, IF(q.status = 'OPEN', q.id, -q.id)
                LIMIT 300
                """, new MapSqlParameterSource("status", status),
                (rs, i) -> new Review(rs.getLong("id"), rs.getLong("verification_id"), rs.getString("reason"),
                        rs.getString("status"), rs.getLong("challenge_id"), rs.getString("title"),
                        rs.getString("category_name"), rs.getString("ai_label"), rs.getLong("user_id"),
                        rs.getString("nickname"), rs.getDate("verify_date").toLocalDate(),
                        rs.getString("predicted_label"), rs.getBigDecimal("confidence"),
                        rs.getBigDecimal("max_similarity"),
                        rs.getObject("duplicate_of_id") == null ? null : rs.getLong("duplicate_of_id"),
                        rs.getString("memo")));
    }

    /** 검토할 사진(또는 비교할 예전 사진) 파일 — 관리자만 부른다 */
    @Transactional(readOnly = true)
    public Path image(Long verificationId) {
        List<String> keys = jdbc.queryForList("SELECT image_url FROM verifications WHERE id = :id",
                new MapSqlParameterSource("id", verificationId), String.class);
        if (keys.isEmpty()) {
            throw new BusinessException(ErrorCode.VERIFICATION_NOT_FOUND);
        }
        Path path = imageStore.resolve(keys.get(0));
        if (!Files.isRegularFile(path)) {
            throw new BusinessException(ErrorCode.VERIFICATION_NOT_FOUND);
        }
        return path;
    }

    @Transactional
    public void approve(Long reviewId, Long reviewerId, String memo) {
        Map<String, Object> row = open(reviewId);
        jdbc.update("UPDATE verifications SET status = 'APPROVED' WHERE id = :id",
                new MapSqlParameterSource("id", row.get("verification_id")));
        close(reviewId, "APPROVED", reviewerId, memo);
    }

    @Transactional
    public void reject(Long reviewId, Long reviewerId, String memo) {
        Map<String, Object> row = open(reviewId);
        Long verificationId = ((Number) row.get("verification_id")).longValue();
        Integer settled = jdbc.queryForObject("""
                SELECT COUNT(*) FROM daily_settlements
                WHERE challenge_id = :challenge AND period_start <= :day AND period_end >= :day
                """, new MapSqlParameterSource("challenge", row.get("challenge_id"))
                .addValue("day", row.get("verify_date")), Integer.class);
        boolean finished = !"ACTIVE".equals(row.get("participant_status"))
                || "ENDED".equals(row.get("challenge_status")) || "SETTLED".equals(row.get("challenge_status"));
        if (finished || (settled != null && settled > 0)) {
            throw new BusinessException(ErrorCode.REVIEW_TOO_LATE);
        }
        jdbc.update("UPDATE verifications SET status = 'REJECTED', reject_reason = :reason WHERE id = :id",
                new MapSqlParameterSource("id", verificationId)
                        .addValue("reason", memo == null || memo.isBlank() ? "관리자 검토에서 거절" : cut(memo.strip())));
        // 그 인증으로 올라간 성공 일수 · 연속 기록에서 하루를 뺀다 (이미 세운 최장 기록은 그대로 둔다)
        jdbc.update("""
                UPDATE challenge_participants
                SET success_days = GREATEST(success_days - 1, 0), current_streak = GREATEST(current_streak - 1, 0)
                WHERE id = :participant
                """, new MapSqlParameterSource("participant", row.get("participant_id")));
        close(reviewId, "REJECTED", reviewerId, memo);
        notificationService.notify(((Number) row.get("user_id")).longValue(), Notification.Type.VERIFY_REJECTED,
                "인증이 취소됐어요",
                "'" + row.get("title") + "' " + row.get("verify_date") + " 인증 사진이 검토에서 인정되지 않았어요.",
                "/challenges/" + row.get("challenge_id"), "verify-rejected:" + verificationId);
    }

    /** 아직 검토 전(OPEN)인 건만 처리한다 (두 관리자가 동시에 눌러도 한 번만) */
    private Map<String, Object> open(Long reviewId) {
        List<Map<String, Object>> rows = jdbc.queryForList("""
                SELECT q.verification_id, v.participant_id, v.verify_date, p.user_id, p.status AS participant_status,
                       c.id AS challenge_id, c.title, c.status AS challenge_status
                FROM review_queue q
                  JOIN verifications v ON v.id = q.verification_id
                  JOIN challenge_participants p ON p.id = v.participant_id
                  JOIN challenges c ON c.id = p.challenge_id
                WHERE q.id = :id AND q.status = 'OPEN'
                FOR UPDATE
                """, new MapSqlParameterSource("id", reviewId));
        if (rows.isEmpty()) {
            throw new BusinessException(ErrorCode.REVIEW_CLOSED);
        }
        return rows.get(0);
    }

    private void close(Long reviewId, String status, Long reviewerId, String memo) {
        jdbc.update("""
                UPDATE review_queue SET status = :status, reviewer_id = :reviewer, reviewed_at = :now, memo = :memo
                WHERE id = :id
                """, new MapSqlParameterSource("id", reviewId).addValue("status", status)
                .addValue("reviewer", reviewerId).addValue("now", LocalDateTime.now(clock))
                .addValue("memo", memo == null || memo.isBlank() ? null : cut(memo.strip())));
    }

    /** review_queue.memo · verifications.reject_reason 길이에 맞춘다 */
    private static String cut(String value) {
        return value.length() <= 100 ? value : value.substring(0, 100);
    }
}
