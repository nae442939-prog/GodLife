package com.godlife.backend.verification.comment;

import com.godlife.backend.challenge.ChallengeService;
import com.godlife.backend.common.error.BusinessException;
import com.godlife.backend.common.error.ErrorCode;
import com.godlife.backend.common.ratelimit.RequestThrottle;
import com.godlife.backend.notification.Notification;
import com.godlife.backend.notification.NotificationService;
import com.godlife.backend.user.UserService;
import lombok.RequiredArgsConstructor;
import org.springframework.jdbc.core.namedparam.MapSqlParameterSource;
import org.springframework.jdbc.core.namedparam.NamedParameterJdbcTemplate;
import org.springframework.jdbc.support.GeneratedKeyHolder;
import org.springframework.jdbc.support.KeyHolder;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.time.Duration;
import java.time.LocalDateTime;
import java.util.List;
import java.util.Map;

/**
 * 인증 사진 응원 댓글. 인증 사진처럼 그 챌린지의 개설자 · 참가자끼리만 보고 쓴다.
 * 내가 차단한 사람의 댓글은 보이지 않는다. 도배 방지: 1분에 10개.
 */
@Service
@RequiredArgsConstructor
public class CheerCommentService {

    public static final int MAX_CONTENT = 300;
    private static final int LIMIT = 10;
    private static final Duration WINDOW = Duration.ofMinutes(1);

    public record CheerComment(Long id, Long userId, String nickname, String profileImageUrl, String content,
                               LocalDateTime createdAt, boolean mine) {
    }

    private final NamedParameterJdbcTemplate jdbc;
    private final ChallengeService challengeService;
    private final UserService userService;
    private final NotificationService notificationService;
    private final RequestThrottle throttle;

    @Transactional(readOnly = true)
    public List<CheerComment> list(Long challengeId, Long verificationId, Long viewerId) {
        challengeService.requireMember(challengeId, viewerId);
        verification(challengeId, verificationId);
        return jdbc.query("""
                SELECT c.id, c.content, c.created_at, u.id AS uid, u.nickname, u.profile_image_url
                FROM comments c JOIN users u ON u.id = c.user_id
                WHERE c.verification_id = :id AND c.is_deleted = FALSE
                  AND NOT EXISTS (SELECT 1 FROM user_blocks b WHERE b.blocker_id = :viewer AND b.blocked_id = c.user_id)
                ORDER BY c.id
                """, new MapSqlParameterSource("id", verificationId).addValue("viewer", viewerId),
                (rs, i) -> new CheerComment(rs.getLong("id"), rs.getLong("uid"), rs.getString("nickname"),
                        rs.getString("profile_image_url"), rs.getString("content"),
                        rs.getTimestamp("created_at").toLocalDateTime(), rs.getLong("uid") == viewerId));
    }

    @Transactional
    public Long write(Long challengeId, Long verificationId, Long userId, String content) {
        challengeService.requireMember(challengeId, userId);
        Map<String, Object> verification = verification(challengeId, verificationId);
        throttle.check("cheer:" + userId, LIMIT, WINDOW);
        KeyHolder key = new GeneratedKeyHolder();
        jdbc.update("INSERT INTO comments (verification_id, user_id, content) VALUES (:id, :user, :content)",
                new MapSqlParameterSource("id", verificationId).addValue("user", userId)
                        .addValue("content", content.strip()), key);
        Long commentId = key.getKey().longValue();
        Long ownerId = ((Number) verification.get("user_id")).longValue();
        if (!ownerId.equals(userId)) {
            notificationService.notify(ownerId, Notification.Type.COMMENT, "응원 댓글이 달렸어요",
                    userService.getActive(userId).getNickname() + "님이 '" + verification.get("title")
                            + "' 챌린지의 내 인증에 응원을 남겼어요.",
                    "/challenges/" + challengeId + "/verify", "cheer:" + commentId);
        }
        return commentId;
    }

    /** 내 응원 댓글 지우기 */
    @Transactional
    public void delete(Long challengeId, Long commentId, Long userId) {
        int deleted = jdbc.update("""
                UPDATE comments c
                  JOIN verifications v ON v.id = c.verification_id
                  JOIN challenge_participants p ON p.id = v.participant_id
                SET c.is_deleted = TRUE
                WHERE c.id = :id AND c.user_id = :user AND p.challenge_id = :challenge AND c.is_deleted = FALSE
                """, new MapSqlParameterSource("id", commentId).addValue("user", userId)
                .addValue("challenge", challengeId));
        if (deleted == 0) {
            throw new BusinessException(ErrorCode.COMMENT_NOT_FOUND);
        }
    }

    /** 이 챌린지에서 보이는 인증인지 확인하고 올린 사람 · 챌린지 제목을 돌려준다 (인증 목록에 나오는 것과 같은 조건) */
    private Map<String, Object> verification(Long challengeId, Long verificationId) {
        return jdbc.queryForList("""
                SELECT p.user_id, c.title
                FROM verifications v
                  JOIN challenge_participants p ON p.id = v.participant_id
                  JOIN challenges c ON c.id = p.challenge_id
                WHERE v.id = :id AND p.challenge_id = :challenge AND v.status <> 'REJECTED'
                  AND p.status NOT IN ('KICKED', 'GAVE_UP')
                """, new MapSqlParameterSource("id", verificationId).addValue("challenge", challengeId))
                .stream().findFirst()
                .orElseThrow(() -> new BusinessException(ErrorCode.VERIFICATION_NOT_FOUND));
    }
}
