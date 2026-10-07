package com.godlife.backend.verification.ai;

import com.godlife.backend.common.error.BusinessException;
import com.godlife.backend.common.error.ErrorCode;
import com.godlife.backend.common.upload.ImageStore;
import com.godlife.backend.notification.Notification;
import com.godlife.backend.notification.NotificationService;
import com.godlife.backend.tier.TierService;
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
import java.util.ArrayList;
import java.util.HashMap;
import java.util.List;
import java.util.Map;

/**
 * 관리자 검토: AI 가 애매하다고 넘긴 인증(확신도 낮음 · 예전 사진과 거의 같음)과 참가자가 신고한 인증을
 * 사람이 승인하거나 거절한다. 처리하면 신고한 사람들에게 결과를 알린다.
 * - 승인: 인증이 그대로 인정된다. (신고는 기각)
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
     * @param reports        이 인증에 들어온 신고들 (먼저 온 순)
     */
    public record Review(Long id, Long verificationId, String reason, String status, Long challengeId,
                         String challengeTitle, String categoryName, String expectedLabel, Long userId,
                         String nickname, LocalDate verifyDate, String predictedLabel, BigDecimal confidence,
                         BigDecimal maxSimilarity, Long duplicateOfId, String memo, List<ReportLine> reports) {

        Review withReports(List<ReportLine> reports) {
            return new Review(id, verificationId, reason, status, challengeId, challengeTitle, categoryName,
                    expectedLabel, userId, nickname, verifyDate, predictedLabel, confidence, maxSimilarity,
                    duplicateOfId, memo, reports);
        }
    }

    /** 신고 한 건: 누가 어떤 이유로 */
    public record ReportLine(Long reporterId, String nickname, String reason, LocalDateTime createdAt) {
    }

    private final NamedParameterJdbcTemplate jdbc;
    private final ImageStore imageStore;
    private final NotificationService notificationService;
    private final TierService tierService;
    private final Clock clock;

    /** 검토 목록 (status 를 주면 그 상태만). 대기는 오래된 것부터, 그 밖은 최근 것부터 */
    @Transactional(readOnly = true)
    public List<Review> list(String status) {
        List<Review> reviews = jdbc.query("""
                SELECT q.id, q.verification_id, q.reason, q.status, q.memo, c.id AS challenge_id, c.title,
                       COALESCE(st.name, cat.name) AS category_name, COALESCE(st.ai_label, cat.ai_label) AS ai_label,
                       u.id AS user_id, u.nickname, v.verify_date,
                       r.predicted_label, r.confidence, r.max_similarity, r.duplicate_of_id
                FROM review_queue q
                  JOIN verifications v ON v.id = q.verification_id
                  JOIN challenge_participants p ON p.id = v.participant_id
                  JOIN challenges c ON c.id = p.challenge_id
                  JOIN categories cat ON cat.id = c.category_id
                  LEFT JOIN category_sub_types st ON st.id = c.sub_type_id
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
                        rs.getString("memo"), List.of()));
        if (reviews.isEmpty()) {
            return reviews;
        }
        Map<Long, List<ReportLine>> reports = new HashMap<>();
        jdbc.query("""
                SELECT r.verification_id, r.reporter_id, u.nickname, r.reason, r.created_at
                FROM reports r JOIN users u ON u.id = r.reporter_id
                WHERE r.verification_id IN (:ids)
                ORDER BY r.id
                """, new MapSqlParameterSource("ids", reviews.stream().map(Review::verificationId).toList()), rs -> {
            reports.computeIfAbsent(rs.getLong("verification_id"), k -> new ArrayList<>())
                    .add(new ReportLine(rs.getLong("reporter_id"), rs.getString("nickname"), rs.getString("reason"),
                            rs.getTimestamp("created_at").toLocalDateTime()));
        });
        return reviews.stream().map(r -> r.withReports(reports.getOrDefault(r.verificationId(), List.of()))).toList();
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
        closeReports(row, false);
        // 검토에서 인정된 인증은 점수에 들어간다
        tierService.refresh(((Number) row.get("user_id")).longValue());
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
        closeReports(row, true);
        // 취소된 인증만큼 점수가 줄어 칭호가 내려갈 수 있다
        tierService.refresh(((Number) row.get("user_id")).longValue());
    }

    /** 이 인증에 들어와 있던 신고를 닫고(인정 / 기각) 신고한 사람들에게 결과를 알린다 */
    private void closeReports(Map<String, Object> row, boolean accepted) {
        Object verificationId = row.get("verification_id");
        List<Long> reporters = jdbc.queryForList(
                "SELECT reporter_id FROM reports WHERE verification_id = :id AND status = 'OPEN'",
                new MapSqlParameterSource("id", verificationId), Long.class);
        if (reporters.isEmpty()) {
            return;
        }
        jdbc.update("UPDATE reports SET status = :status WHERE verification_id = :id AND status = 'OPEN'",
                new MapSqlParameterSource("id", verificationId).addValue("status", accepted ? "ACCEPTED" : "DISMISSED"));
        String body = "'" + row.get("title") + "' 챌린지에서 신고하신 인증을 확인했어요. "
                + (accepted ? "그 인증은 취소됐어요." : "검토 결과 문제가 없었어요.");
        for (Long reporter : reporters) {
            notificationService.notify(reporter, Notification.Type.REPORT_RESULT, "신고 결과를 알려 드려요", body,
                    "/challenges/" + row.get("challenge_id"), "report-result:" + verificationId);
        }
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
