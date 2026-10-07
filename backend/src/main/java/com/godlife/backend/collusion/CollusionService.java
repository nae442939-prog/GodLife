package com.godlife.backend.collusion;

import com.godlife.backend.common.error.BusinessException;
import com.godlife.backend.common.error.ErrorCode;
import lombok.RequiredArgsConstructor;
import org.springframework.jdbc.core.namedparam.MapSqlParameterSource;
import org.springframework.jdbc.core.namedparam.NamedParameterJdbcTemplate;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.math.BigDecimal;
import java.time.LocalDateTime;
import java.util.List;

/**
 * 담합(짜고 지기) 의심 표시 — 기초 버전.
 * 정산이 끝난 포인트 챌린지에서 "같은 두 사람이 여러 번 함께 참여했고, 늘 한쪽만 실패해 다른 쪽이 보상을 받은" 조합을 찾는다.
 * - 참가자가 적은 챌린지({@value #SMALL_CHALLENGE}명 이하)만 본다. 사람이 많은 공개 챌린지에서 겹치는 것은 흔한 일이라서.
 * - 함께한 횟수 {@value #MIN_TOGETHER}번 이상, 그중 한쪽만 실패한 것이 {@value #MIN_ONE_SIDED}번 이상이면 표시한다.
 * - 표시만 한다. 포인트를 되돌리거나 계정을 막지 않고, 관리자가 보고 판단한다.
 */
@Service
@RequiredArgsConstructor
public class CollusionService {

    static final int SMALL_CHALLENGE = 6;
    static final int MIN_TOGETHER = 3;
    static final int MIN_ONE_SIDED = 3;

    /**
     * @param coMatchCount 두 사람이 함께한 (작은, 정산된) 포인트 챌린지 수
     * @param score        그중 한쪽만 실패한 비율 (0~100)
     * @param status       OPEN / CONFIRMED / DISMISSED
     */
    public record Flag(Long id, Long challengeId, String challengeTitle, Long userAId, String userANickname,
                       Long userBId, String userBNickname, int coMatchCount, BigDecimal score, String status,
                       LocalDateTime detectedAt) {
    }

    private final NamedParameterJdbcTemplate jdbc;

    /**
     * 의심 조합을 찾아 표시한다 (자정 진행 관리가 정산 뒤에 부른다). 가장 최근에 함께한 챌린지에 붙여 기록하므로
     * 같은 조합이 새 챌린지에서 또 걸리면 새 줄이 생기고, 이미 있는 줄은 횟수만 고친다 (관리자가 내린 판단은 그대로).
     */
    @Transactional
    public int scan() {
        return jdbc.update("""
                INSERT INTO collusion_flags (challenge_id, user_a_id, user_b_id, co_match_count, score)
                SELECT t.last_challenge, t.ua, t.ub, t.together, ROUND(100 * t.one_sided / t.together, 2)
                FROM (
                    SELECT a.user_id AS ua, b.user_id AS ub, COUNT(*) AS together, MAX(c.id) AS last_challenge,
                           GREATEST(SUM(a.status IN ('FAILED', 'GAVE_UP') AND b.status = 'COMPLETED'),
                                    SUM(b.status IN ('FAILED', 'GAVE_UP') AND a.status = 'COMPLETED')) AS one_sided
                    FROM challenge_participants a
                      JOIN challenge_participants b ON b.challenge_id = a.challenge_id AND a.user_id < b.user_id
                      JOIN challenges c ON c.id = a.challenge_id
                    WHERE c.mode = 'BET' AND c.status = 'SETTLED'
                      AND a.deposit_amount > 0 AND b.deposit_amount > 0
                      AND a.status IN ('COMPLETED', 'FAILED', 'GAVE_UP') AND b.status IN ('COMPLETED', 'FAILED', 'GAVE_UP')
                      AND (SELECT COUNT(*) FROM challenge_participants x
                           WHERE x.challenge_id = c.id AND x.deposit_amount > 0
                             AND x.status IN ('COMPLETED', 'FAILED', 'GAVE_UP')) <= :small
                    GROUP BY a.user_id, b.user_id
                    HAVING together >= :together AND one_sided >= :oneSided
                ) t
                ON DUPLICATE KEY UPDATE co_match_count = VALUES(co_match_count), score = VALUES(score)
                """, new MapSqlParameterSource("small", SMALL_CHALLENGE).addValue("together", MIN_TOGETHER)
                .addValue("oneSided", MIN_ONE_SIDED));
    }

    /** 관리자: 의심 조합 목록 (status 를 주면 그 상태만), 최근 것부터 */
    @Transactional(readOnly = true)
    public List<Flag> list(String status) {
        return jdbc.query("""
                SELECT f.id, f.challenge_id, c.title, f.user_a_id, ua.nickname AS a_nickname, f.user_b_id,
                       ub.nickname AS b_nickname, f.co_match_count, f.score, f.status, f.detected_at
                FROM collusion_flags f
                  JOIN challenges c ON c.id = f.challenge_id
                  JOIN users ua ON ua.id = f.user_a_id
                  JOIN users ub ON ub.id = f.user_b_id
                WHERE (:status IS NULL OR f.status = :status)
                ORDER BY f.id DESC LIMIT 300
                """, new MapSqlParameterSource("status", status),
                (rs, i) -> new Flag(rs.getLong("id"), rs.getLong("challenge_id"), rs.getString("title"),
                        rs.getLong("user_a_id"), rs.getString("a_nickname"), rs.getLong("user_b_id"),
                        rs.getString("b_nickname"), rs.getInt("co_match_count"), rs.getBigDecimal("score"),
                        rs.getString("status"), rs.getTimestamp("detected_at").toLocalDateTime()));
    }

    /** 관리자 판단: CONFIRMED(담합으로 봄) / DISMISSED(문제없음). 기록만 남긴다 */
    @Transactional
    public void decide(Long id, String status) {
        int updated = jdbc.update("UPDATE collusion_flags SET status = :status WHERE id = :id",
                new MapSqlParameterSource("id", id).addValue("status", status));
        if (updated == 0) {
            throw new BusinessException(ErrorCode.VALIDATION_ERROR, "없는 기록이에요.");
        }
    }
}
