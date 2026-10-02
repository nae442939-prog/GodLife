package com.godlife.backend.community;

import com.godlife.backend.challenge.dto.PageResponse;
import com.godlife.backend.common.error.BusinessException;
import com.godlife.backend.common.error.ErrorCode;
import com.godlife.backend.common.ratelimit.RequestThrottle;
import com.godlife.backend.common.upload.ImageStore;
import com.godlife.backend.community.dto.CommunityDtos.Attachable;
import com.godlife.backend.community.dto.CommunityDtos.Author;
import com.godlife.backend.community.dto.CommunityDtos.CommentResponse;
import com.godlife.backend.community.dto.CommunityDtos.LikeResponse;
import com.godlife.backend.community.dto.CommunityDtos.PostDetail;
import com.godlife.backend.community.dto.CommunityDtos.PostSummary;
import com.godlife.backend.community.dto.CommunityDtos.Topic;
import com.godlife.backend.community.dto.CommunityDtos.VerifyBadge;
import com.godlife.backend.notification.Notification;
import com.godlife.backend.notification.NotificationService;
import com.godlife.backend.user.User;
import com.godlife.backend.user.UserService;
import lombok.RequiredArgsConstructor;
import org.springframework.jdbc.core.namedparam.MapSqlParameterSource;
import org.springframework.jdbc.core.namedparam.NamedParameterJdbcTemplate;
import org.springframework.jdbc.support.GeneratedKeyHolder;
import org.springframework.jdbc.support.KeyHolder;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;
import org.springframework.web.multipart.MultipartFile;

import java.nio.file.Path;
import java.sql.Date;
import java.sql.ResultSet;
import java.sql.SQLException;
import java.sql.Timestamp;
import java.time.Clock;
import java.time.Duration;
import java.time.LocalDate;
import java.time.LocalDateTime;
import java.util.HashSet;
import java.util.List;
import java.util.Map;
import java.util.Set;

/**
 * 커뮤니티: 게시판 하나에 말머리(자유 · 인증 후기 · 팁 · 질문)로 나눈다.
 * - 읽기는 누구나, 글 · 댓글 쓰기는 휴대폰 인증을 마친 회원만 (한 사람이 계정 여러 개로 도배하지 못하게).
 * - 글에는 지금 참여 중인 챌린지를 골라 그날 인증 결과를 붙일 수 있다. 결과는 글쓴이가 입력하지 않고
 *   서버가 인증 기록을 조회해 붙이며, 쓴 시점 값으로 굳혀 저장한다 (나중에 고칠 수 없다).
 * - 내가 차단한 사람의 글 · 댓글은 보이지 않는다. 지운 글 · 신고로 가려진 글은 없는 것처럼 보인다.
 * - 좋아요 · 댓글 수는 posts 에 숫자로 같이 두고, 줄을 넣고 뺀 것이 실제로 반영됐을 때만 올리고 내린다.
 */
@Service
@RequiredArgsConstructor
public class CommunityService {

    public static final int PAGE_SIZE = 15;
    public static final int MAX_IMAGES = 4;
    private static final int EXCERPT = 120;
    private static final int POST_LIMIT = 5;
    private static final Duration POST_WINDOW = Duration.ofMinutes(10);
    private static final int COMMENT_LIMIT = 10;
    private static final Duration COMMENT_WINDOW = Duration.ofMinutes(1);

    /** 정렬: latest(최신순, 기본) / popular(좋아요 많은 순) */
    public enum SortOption {
        LATEST("p.id DESC"),
        POPULAR("p.like_count DESC, p.comment_count DESC, p.id DESC");

        private final String orderBy;

        SortOption(String orderBy) {
            this.orderBy = orderBy;
        }
    }

    private static final String NOT_BLOCKED = """
            NOT EXISTS (SELECT 1 FROM user_blocks b WHERE b.blocker_id = :viewer AND b.blocked_id = %s)
            """;

    private final NamedParameterJdbcTemplate jdbc;
    private final UserService userService;
    private final ImageStore imageStore;
    private final RequestThrottle throttle;
    private final NotificationService notificationService;
    private final Clock clock;

    // ---------- 읽기 ----------

    /** 글 목록. viewerId 는 비로그인이면 null. */
    @Transactional(readOnly = true)
    public PageResponse<PostSummary> list(Long viewerId, Topic topic, String keyword, SortOption sort, int page) {
        int pageNo = Math.max(page, 0);
        MapSqlParameterSource params = new MapSqlParameterSource("viewer", viewerId)
                .addValue("topic", topic == null ? null : topic.name())
                .addValue("q", likePattern(keyword))
                .addValue("size", PAGE_SIZE).addValue("offset", (long) pageNo * PAGE_SIZE);
        String where = """
                WHERE p.status = 'VISIBLE'
                  AND (:topic IS NULL OR p.topic = :topic)
                  AND (:q IS NULL OR p.title LIKE :q ESCAPE '!' OR p.content LIKE :q ESCAPE '!')
                  AND\s""" + NOT_BLOCKED.formatted("p.user_id");
        Long total = jdbc.queryForObject("SELECT COUNT(*) FROM posts p " + where, params, Long.class);
        long count = total == null ? 0 : total;
        List<PostSummary> items = jdbc.query("""
                SELECT p.id, p.topic, p.title, LEFT(p.content, :excerpt) AS excerpt, p.challenge_title, p.verify_date,
                       p.verify_result, p.like_count, p.comment_count, p.created_at,
                       u.id AS uid, u.nickname, u.profile_image_url,
                       (SELECT MIN(i.id) FROM post_images i WHERE i.post_id = p.id) AS thumbnail,
                       (SELECT COUNT(*) FROM post_images i WHERE i.post_id = p.id) AS images
                FROM posts p JOIN users u ON u.id = p.user_id
                """ + where + " ORDER BY " + sort.orderBy + " LIMIT :size OFFSET :offset",
                params.addValue("excerpt", EXCERPT),
                (rs, i) -> new PostSummary(rs.getLong("id"), Topic.valueOf(rs.getString("topic")),
                        rs.getString("title"), rs.getString("excerpt"), author(rs), verify(rs),
                        rs.getObject("thumbnail", Long.class), rs.getInt("images"), rs.getInt("like_count"),
                        rs.getInt("comment_count"), rs.getTimestamp("created_at").toLocalDateTime()));
        return new PageResponse<>(items, pageNo, (int) ((count + PAGE_SIZE - 1) / PAGE_SIZE), count);
    }

    /** 글 상세 + 댓글. 지웠거나 가려진 글, 내가 차단한 사람의 글은 없는 것처럼 404. */
    @Transactional(readOnly = true)
    public PostDetail detail(Long viewerId, Long postId) {
        MapSqlParameterSource params = new MapSqlParameterSource("id", postId).addValue("viewer", viewerId);
        Set<Long> reportedComments = viewerId == null ? Set.of() : new HashSet<>(jdbc.queryForList("""
                SELECT r.target_id FROM community_reports r JOIN post_comments c ON c.id = r.target_id
                WHERE r.target_type = 'COMMENT' AND r.reporter_id = :viewer AND c.post_id = :id
                """, params, Long.class));
        Set<Long> likedComments = viewerId == null ? Set.of() : new HashSet<>(jdbc.queryForList("""
                SELECT l.comment_id FROM post_comment_likes l JOIN post_comments c ON c.id = l.comment_id
                WHERE l.user_id = :viewer AND c.post_id = :id
                """, params, Long.class));
        // 지웠거나 가려진 댓글이라도 보이는 답글이 달려 있으면 자리만 남긴다 (removed: 내용 · 글쓴이 없이)
        List<CommentResponse> comments = jdbc.query("""
                SELECT c.id, c.parent_id, c.content, c.status, c.created_at, c.like_count,
                       u.id AS uid, u.nickname, u.profile_image_url
                FROM post_comments c JOIN users u ON u.id = c.user_id
                WHERE c.post_id = :id
                  AND (c.status = 'VISIBLE'
                       OR (c.parent_id IS NULL AND EXISTS (SELECT 1 FROM post_comments r
                                                           WHERE r.parent_id = c.id AND r.status = 'VISIBLE')))
                  AND\s""" + NOT_BLOCKED.formatted("c.user_id")
                + " ORDER BY c.id", params,
                (rs, i) -> {
                    Long parentId = rs.getObject("parent_id", Long.class);
                    LocalDateTime createdAt = rs.getTimestamp("created_at").toLocalDateTime();
                    if (!"VISIBLE".equals(rs.getString("status"))) {
                        return new CommentResponse(rs.getLong("id"), parentId, null, "", createdAt, 0, false, false,
                                false, true);
                    }
                    return new CommentResponse(rs.getLong("id"), parentId, author(rs), rs.getString("content"),
                            createdAt, rs.getInt("like_count"), likedComments.contains(rs.getLong("id")),
                            Long.valueOf(rs.getLong("uid")).equals(viewerId),
                            reportedComments.contains(rs.getLong("id")), false);
                });
        List<Long> imageIds = jdbc.queryForList("SELECT id FROM post_images WHERE post_id = :id ORDER BY id", params,
                Long.class);
        boolean liked = viewerId != null && exists(
                "SELECT COUNT(*) FROM post_likes WHERE post_id = :id AND user_id = :viewer", params);
        boolean reported = viewerId != null && exists("""
                SELECT COUNT(*) FROM community_reports
                WHERE target_type = 'POST' AND target_id = :id AND reporter_id = :viewer
                """, params);
        return jdbc.query("""
                SELECT p.id, p.topic, p.title, p.content, p.challenge_title, p.verify_date, p.verify_result,
                       p.like_count, p.comment_count, p.created_at, p.updated_at,
                       u.id AS uid, u.nickname, u.profile_image_url
                FROM posts p JOIN users u ON u.id = p.user_id
                WHERE p.id = :id AND p.status = 'VISIBLE' AND\s""" + NOT_BLOCKED.formatted("p.user_id"), params,
                (rs, i) -> {
                    Timestamp updated = rs.getTimestamp("updated_at");
                    return new PostDetail(rs.getLong("id"), Topic.valueOf(rs.getString("topic")),
                            rs.getString("title"), rs.getString("content"), author(rs), verify(rs), imageIds,
                            rs.getInt("like_count"), rs.getInt("comment_count"), liked,
                            Long.valueOf(rs.getLong("uid")).equals(viewerId), reported,
                            rs.getTimestamp("created_at").toLocalDateTime(),
                            updated == null ? null : updated.toLocalDateTime(), comments);
                }).stream().findFirst().orElseThrow(() -> new BusinessException(ErrorCode.POST_NOT_FOUND));
    }

    /** 글에 인증 결과를 붙일 수 있는 챌린지: 내가 참여해 오늘 진행 중인 것 */
    @Transactional(readOnly = true)
    public List<Attachable> attachable(Long userId) {
        return jdbc.query("""
                SELECT c.id, c.title,
                       EXISTS (SELECT 1 FROM verifications v WHERE v.participant_id = p.id
                                 AND v.verify_date = :today AND v.status <> 'REJECTED') AS verified
                FROM challenge_participants p JOIN challenges c ON c.id = p.challenge_id
                WHERE p.user_id = :me AND p.status = 'ACTIVE' AND c.start_date <= :today AND c.end_date >= :today
                ORDER BY c.id DESC
                """, new MapSqlParameterSource("me", userId).addValue("today", today()),
                (rs, i) -> new Attachable(rs.getLong("id"), rs.getString("title"), rs.getBoolean("verified")));
    }

    /** 글 사진 파일. 보이는 글의 사진만 준다. */
    @Transactional(readOnly = true)
    public Path image(Long imageId) {
        return jdbc.queryForList("""
                SELECT i.photo_key FROM post_images i JOIN posts p ON p.id = i.post_id
                WHERE i.id = :id AND p.status = 'VISIBLE'
                """, new MapSqlParameterSource("id", imageId), String.class).stream().findFirst()
                .map(imageStore::resolve)
                .orElseThrow(() -> new BusinessException(ErrorCode.POST_NOT_FOUND));
    }

    // ---------- 글 ----------

    @Transactional
    public Long create(Long userId, Topic topic, String title, String content, Long challengeId) {
        requireWriter(userId);
        throttle.check("post:" + userId, POST_LIMIT, POST_WINDOW);
        MapSqlParameterSource params = new MapSqlParameterSource("user", userId).addValue("topic", topic.name())
                .addValue("title", title.strip()).addValue("content", content.strip())
                .addValue("challenge", null).addValue("challengeTitle", null)
                .addValue("verifyDate", null).addValue("verifyResult", null);
        if (challengeId != null) {
            // 인증 결과는 서버가 인증 기록으로 정한다 (글쓴이가 보낸 값을 믿지 않는다)
            Attachable attached = attachable(userId).stream()
                    .filter(a -> a.challengeId().equals(challengeId)).findFirst()
                    .orElseThrow(() -> new BusinessException(ErrorCode.VALIDATION_ERROR,
                            "지금 참여해 진행 중인 챌린지의 인증 결과만 붙일 수 있어요."));
            params.addValue("challenge", challengeId).addValue("challengeTitle", attached.title())
                    .addValue("verifyDate", today()).addValue("verifyResult", attached.verifiedToday() ? "SUCCESS" : "FAIL");
        }
        KeyHolder key = new GeneratedKeyHolder();
        jdbc.update("""
                INSERT INTO posts (user_id, topic, title, content, challenge_id, challenge_title, verify_date,
                                   verify_result)
                VALUES (:user, :topic, :title, :content, :challenge, :challengeTitle, :verifyDate, :verifyResult)
                """, params, key);
        return key.getKey().longValue();
    }

    /** 글 고치기 (글쓴이만). 붙인 인증 결과는 쓴 시점 값이라 바꿀 수 없다. */
    @Transactional
    public void update(Long userId, Long postId, Topic topic, String title, String content) {
        requireMine(userId, postId);
        jdbc.update("""
                UPDATE posts SET topic = :topic, title = :title, content = :content, updated_at = :now
                WHERE id = :id
                """, new MapSqlParameterSource("id", postId).addValue("topic", topic.name())
                .addValue("title", title.strip()).addValue("content", content.strip())
                .addValue("now", LocalDateTime.now(clock)));
    }

    /** 글 지우기 (글쓴이만). 사진 파일도 지운다. 댓글 · 좋아요는 글과 함께 보이지 않게 된다. */
    @Transactional
    public void delete(Long userId, Long postId) {
        requireMine(userId, postId);
        MapSqlParameterSource params = new MapSqlParameterSource("id", postId);
        List<String> keys = jdbc.queryForList("SELECT photo_key FROM post_images WHERE post_id = :id", params,
                String.class);
        jdbc.update("DELETE FROM post_images WHERE post_id = :id", params);
        jdbc.update("UPDATE posts SET status = 'DELETED' WHERE id = :id", params);
        keys.forEach(imageStore::delete);
    }

    /** 글에 사진 한 장 붙이기 (글쓴이만, 4장까지). 붙인 사진의 id 를 돌려준다. */
    @Transactional
    public Long addImage(Long userId, Long postId, MultipartFile file) {
        requireMine(userId, postId); // 글 줄을 잠가서 동시에 올려도 4장을 넘지 않는다
        MapSqlParameterSource params = new MapSqlParameterSource("id", postId);
        if (exists("SELECT COUNT(*) >= " + MAX_IMAGES + " FROM post_images WHERE post_id = :id", params)) {
            throw new BusinessException(ErrorCode.TOO_MANY_IMAGES);
        }
        KeyHolder key = new GeneratedKeyHolder();
        jdbc.update("INSERT INTO post_images (post_id, photo_key) VALUES (:id, :key)",
                params.addValue("key", imageStore.storePostImage(postId, file)), key);
        return key.getKey().longValue();
    }

    @Transactional
    public void removeImage(Long userId, Long postId, Long imageId) {
        requireMine(userId, postId);
        MapSqlParameterSource params = new MapSqlParameterSource("id", imageId).addValue("post", postId);
        List<String> keys = jdbc.queryForList("SELECT photo_key FROM post_images WHERE id = :id AND post_id = :post",
                params, String.class);
        jdbc.update("DELETE FROM post_images WHERE id = :id AND post_id = :post", params);
        keys.forEach(imageStore::delete);
    }

    // ---------- 좋아요 ----------

    /** 좋아요 누르기 / 취소. 여러 번 눌러도 결과가 같다 (좋아요 줄이 실제로 생기거나 없어졌을 때만 숫자를 바꾼다). */
    @Transactional
    public LikeResponse like(Long userId, Long postId, boolean on) {
        requireVisible(postId);
        MapSqlParameterSource params = new MapSqlParameterSource("id", postId).addValue("user", userId);
        if (on) {
            if (jdbc.update("INSERT IGNORE INTO post_likes (post_id, user_id) VALUES (:id, :user)", params) > 0) {
                jdbc.update("UPDATE posts SET like_count = like_count + 1 WHERE id = :id", params);
            }
        } else if (jdbc.update("DELETE FROM post_likes WHERE post_id = :id AND user_id = :user", params) > 0) {
            jdbc.update("UPDATE posts SET like_count = like_count - 1 WHERE id = :id AND like_count > 0", params);
        }
        Integer count = jdbc.queryForObject("SELECT like_count FROM posts WHERE id = :id", params, Integer.class);
        return new LikeResponse(on, count == null ? 0 : count);
    }

    /** 댓글 좋아요 누르기 / 취소. 글 좋아요와 같은 방식이다. */
    @Transactional
    public LikeResponse likeComment(Long userId, Long commentId, boolean on) {
        MapSqlParameterSource params = new MapSqlParameterSource("id", commentId).addValue("user", userId);
        if (!exists("""
                SELECT COUNT(*) FROM post_comments c JOIN posts p ON p.id = c.post_id
                WHERE c.id = :id AND c.status = 'VISIBLE' AND p.status = 'VISIBLE'
                """, params)) {
            throw new BusinessException(ErrorCode.COMMENT_NOT_FOUND);
        }
        if (on) {
            if (jdbc.update("INSERT IGNORE INTO post_comment_likes (comment_id, user_id) VALUES (:id, :user)", params) > 0) {
                jdbc.update("UPDATE post_comments SET like_count = like_count + 1 WHERE id = :id", params);
            }
        } else if (jdbc.update("DELETE FROM post_comment_likes WHERE comment_id = :id AND user_id = :user", params) > 0) {
            jdbc.update("UPDATE post_comments SET like_count = like_count - 1 WHERE id = :id AND like_count > 0", params);
        }
        Integer count = jdbc.queryForObject("SELECT like_count FROM post_comments WHERE id = :id", params,
                Integer.class);
        return new LikeResponse(on, count == null ? 0 : count);
    }

    // ---------- 댓글 ----------

    /**
     * 댓글 달기. parentId 가 있으면 그 댓글의 답글(대댓글)이다.
     * 답글은 한 단계만 둔다: 답글에 답글을 달면 같은 원 댓글 아래에 이어 붙는다.
     * 글쓴이에게, 답글이면 원 댓글을 쓴 사람에게도 알린다.
     */
    @Transactional
    public Long comment(Long userId, Long postId, String content, Long parentId) {
        User writer = requireWriter(userId);
        throttle.check("post-comment:" + userId, COMMENT_LIMIT, COMMENT_WINDOW);
        Map<String, Object> post = requireVisible(postId);
        MapSqlParameterSource params = new MapSqlParameterSource("id", postId).addValue("user", userId)
                .addValue("content", content.strip()).addValue("parent", null);
        Long parentAuthorId = null;
        if (parentId != null) {
            Map<String, Object> parent = jdbc.queryForList("""
                    SELECT COALESCE(c.parent_id, c.id) AS root_id, c.user_id FROM post_comments c
                    WHERE c.id = :parent AND c.post_id = :id AND c.status = 'VISIBLE'
                    """, params.addValue("parent", parentId)).stream().findFirst()
                    .orElseThrow(() -> new BusinessException(ErrorCode.COMMENT_NOT_FOUND));
            params.addValue("parent", ((Number) parent.get("root_id")).longValue());
            parentAuthorId = ((Number) parent.get("user_id")).longValue();
        }
        KeyHolder key = new GeneratedKeyHolder();
        jdbc.update("""
                INSERT INTO post_comments (post_id, parent_id, user_id, content) VALUES (:id, :parent, :user, :content)
                """, params, key);
        jdbc.update("UPDATE posts SET comment_count = comment_count + 1 WHERE id = :id", params);
        Long commentId = key.getKey().longValue();
        Long authorId = ((Number) post.get("user_id")).longValue();
        if (parentAuthorId != null && !parentAuthorId.equals(userId)) {
            notificationService.notify(parentAuthorId, Notification.Type.COMMENT, "답글이 달렸어요",
                    writer.getNickname() + "님이 '" + post.get("title") + "' 글의 내 댓글에 답글을 남겼어요.",
                    "/community/" + postId, "post-comment:" + commentId);
        }
        if (!authorId.equals(userId) && !authorId.equals(parentAuthorId)) {
            notificationService.notify(authorId, Notification.Type.COMMENT, "새 댓글이 달렸어요",
                    writer.getNickname() + "님이 '" + post.get("title") + "' 글에 댓글을 남겼어요.",
                    "/community/" + postId, "post-comment:" + commentId);
        }
        return commentId;
    }

    /** 내 댓글 지우기 */
    @Transactional
    public void deleteComment(Long userId, Long commentId) {
        MapSqlParameterSource params = new MapSqlParameterSource("id", commentId).addValue("user", userId);
        List<Long> postIds = jdbc.queryForList("""
                SELECT post_id FROM post_comments WHERE id = :id AND user_id = :user AND status = 'VISIBLE' FOR UPDATE
                """, params, Long.class);
        if (postIds.isEmpty()) {
            throw new BusinessException(ErrorCode.COMMENT_NOT_FOUND);
        }
        jdbc.update("UPDATE post_comments SET status = 'DELETED' WHERE id = :id", params);
        jdbc.update("UPDATE posts SET comment_count = comment_count - 1 WHERE id = :post AND comment_count > 0",
                params.addValue("post", postIds.get(0)));
    }

    // ---------- 공통 ----------

    /** 글 · 댓글은 휴대폰 인증을 마친 회원만 쓴다 */
    private User requireWriter(Long userId) {
        User user = userService.getActive(userId);
        if (!user.hasPhone()) {
            throw new BusinessException(ErrorCode.PHONE_NOT_REGISTERED);
        }
        return user;
    }

    /** 보이는 글인지 확인하고 글쓴이 · 제목을 돌려준다 */
    private Map<String, Object> requireVisible(Long postId) {
        return jdbc.queryForList("SELECT user_id, title FROM posts WHERE id = :id AND status = 'VISIBLE'",
                        new MapSqlParameterSource("id", postId)).stream().findFirst()
                .orElseThrow(() -> new BusinessException(ErrorCode.POST_NOT_FOUND));
    }

    /** 내 글인지 확인하면서 글 줄을 잠근다. 없는 글이면 404, 남의 글이면 403. */
    private void requireMine(Long userId, Long postId) {
        Long authorId = jdbc.queryForList("SELECT user_id FROM posts WHERE id = :id AND status = 'VISIBLE' FOR UPDATE",
                        new MapSqlParameterSource("id", postId), Long.class).stream().findFirst()
                .orElseThrow(() -> new BusinessException(ErrorCode.POST_NOT_FOUND));
        if (!authorId.equals(userId)) {
            throw new BusinessException(ErrorCode.FORBIDDEN);
        }
    }

    private boolean exists(String sql, MapSqlParameterSource params) {
        Long n = jdbc.queryForObject(sql, params, Long.class);
        return n != null && n > 0;
    }

    private LocalDate today() {
        return LocalDate.now(clock);
    }

    private static Author author(ResultSet rs) throws SQLException {
        return new Author(rs.getLong("uid"), rs.getString("nickname"), rs.getString("profile_image_url"));
    }

    private static VerifyBadge verify(ResultSet rs) throws SQLException {
        String result = rs.getString("verify_result");
        Date date = rs.getDate("verify_date");
        if (result == null || date == null) {
            return null;
        }
        return new VerifyBadge(rs.getString("challenge_title"), date.toLocalDate(), "SUCCESS".equals(result));
    }

    /** 검색어를 LIKE 패턴으로. %, _ 는 글자 그대로 찾도록 ! 로 이스케이프한다. 비어 있으면 null(조건 없음). */
    private static String likePattern(String keyword) {
        if (keyword == null || keyword.isBlank()) {
            return null;
        }
        return "%" + keyword.strip().replace("!", "!!").replace("%", "!%").replace("_", "!_") + "%";
    }
}
