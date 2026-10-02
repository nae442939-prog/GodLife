package com.godlife.backend.verification.report;

import com.godlife.backend.challenge.ChallengeService;
import com.godlife.backend.common.error.BusinessException;
import com.godlife.backend.common.error.ErrorCode;
import lombok.RequiredArgsConstructor;
import org.springframework.dao.DuplicateKeyException;
import org.springframework.jdbc.core.namedparam.MapSqlParameterSource;
import org.springframework.jdbc.core.namedparam.NamedParameterJdbcTemplate;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.time.Clock;
import java.time.LocalDate;
import java.util.HashSet;
import java.util.List;
import java.util.Map;
import java.util.Set;

/**
 * 인증 신고: 같은 챌린지 참가자가 의심스러운 인증 사진을 신고하면 관리자 검토 큐(review_queue, REPORTED)에 올라간다.
 * - 신고만으로는 인증이 취소되지 않는다. 관리자가 사진을 보고 승인(신고 기각) / 거절(신고 인정)한다.
 * - 내 인증은 신고할 수 없고, 같은 인증은 한 번만, 하루 {@value #DAILY_LIMIT}건까지만 신고할 수 있다 (신고 남발 방지).
 * - 그날(주 N회는 그 주) 결과가 이미 기록된 인증은 되돌릴 수 없어 신고를 받지 않는다.
 */
@Service
@RequiredArgsConstructor
public class VerificationReportService {

    static final int DAILY_LIMIT = 10;

    private final NamedParameterJdbcTemplate jdbc;
    private final ChallengeService challengeService;
    private final Clock clock;

    @Transactional
    public void report(Long challengeId, Long verificationId, Long reporterId, String reason) {
        challengeService.requireMember(challengeId, reporterId);
        // 검토 큐에 올리는 동안 인증 줄을 잠근다 (여러 명이 동시에 신고해도 큐에는 한 줄만)
        List<Map<String, Object>> rows = jdbc.queryForList("""
                SELECT v.verify_date, p.user_id, p.status AS participant_status, c.status AS challenge_status
                FROM verifications v
                  JOIN challenge_participants p ON p.id = v.participant_id
                  JOIN challenges c ON c.id = p.challenge_id
                WHERE v.id = :id AND p.challenge_id = :challenge AND v.status <> 'REJECTED'
                  AND p.status NOT IN ('KICKED', 'GAVE_UP')
                FOR UPDATE
                """, new MapSqlParameterSource("id", verificationId).addValue("challenge", challengeId));
        if (rows.isEmpty()) {
            throw new BusinessException(ErrorCode.VERIFICATION_NOT_FOUND);
        }
        Map<String, Object> row = rows.get(0);
        if (((Number) row.get("user_id")).longValue() == reporterId) {
            throw new BusinessException(ErrorCode.VALIDATION_ERROR, "내 인증은 신고할 수 없어요.");
        }
        if (!"ACTIVE".equals(row.get("participant_status")) || settled(challengeId, row.get("verify_date"))
                || "ENDED".equals(row.get("challenge_status")) || "SETTLED".equals(row.get("challenge_status"))) {
            throw new BusinessException(ErrorCode.REVIEW_TOO_LATE, "이미 결과가 나온 인증은 신고할 수 없어요.");
        }

        List<String> review = jdbc.queryForList("SELECT status FROM review_queue WHERE verification_id = :id",
                new MapSqlParameterSource("id", verificationId), String.class);
        if (!review.isEmpty() && !"OPEN".equals(review.get(0))) {
            throw new BusinessException(ErrorCode.REVIEW_CLOSED, "이미 관리자가 확인한 인증이에요.");
        }
        Integer today = jdbc.queryForObject("""
                SELECT COUNT(*) FROM reports WHERE reporter_id = :reporter AND created_at >= :from
                """, new MapSqlParameterSource("reporter", reporterId)
                .addValue("from", LocalDate.now(clock).atStartOfDay()), Integer.class);
        if (today != null && today >= DAILY_LIMIT) {
            throw new BusinessException(ErrorCode.TOO_MANY_REQUESTS, "오늘은 더 신고할 수 없어요. 내일 다시 시도해 주세요.");
        }

        try {
            jdbc.update("INSERT INTO reports (verification_id, reporter_id, reason) VALUES (:id, :reporter, :reason)",
                    new MapSqlParameterSource("id", verificationId).addValue("reporter", reporterId)
                            .addValue("reason", reason.strip()));
        } catch (DuplicateKeyException e) {
            throw new BusinessException(ErrorCode.ALREADY_REPORTED, "이미 신고한 인증이에요.");
        }
        // AI 가 이미 검토에 올려 둔 인증이면 그 줄을 그대로 쓴다 (신고 내용은 검토 화면에 함께 보인다)
        if (review.isEmpty()) {
            jdbc.update("INSERT INTO review_queue (verification_id, reason) VALUES (:id, 'REPORTED')",
                    new MapSqlParameterSource("id", verificationId));
        }
    }

    /** 이 챌린지에서 내가 신고한 인증들 (화면에서 '신고함'으로 표시) */
    @Transactional(readOnly = true)
    public Set<Long> reportedBy(Long challengeId, Long reporterId) {
        return new HashSet<>(jdbc.queryForList("""
                SELECT r.verification_id FROM reports r
                  JOIN verifications v ON v.id = r.verification_id
                  JOIN challenge_participants p ON p.id = v.participant_id
                WHERE r.reporter_id = :reporter AND p.challenge_id = :challenge
                """, new MapSqlParameterSource("reporter", reporterId).addValue("challenge", challengeId), Long.class));
    }

    private boolean settled(Long challengeId, Object verifyDate) {
        Integer count = jdbc.queryForObject("""
                SELECT COUNT(*) FROM daily_settlements
                WHERE challenge_id = :challenge AND period_start <= :day AND period_end >= :day
                """, new MapSqlParameterSource("challenge", challengeId).addValue("day", verifyDate), Integer.class);
        return count != null && count > 0;
    }
}
