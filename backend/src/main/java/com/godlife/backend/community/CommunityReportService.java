package com.godlife.backend.community;

import com.godlife.backend.common.error.BusinessException;
import com.godlife.backend.common.error.ErrorCode;
import com.godlife.backend.community.dto.CommunityDtos.ReportedItem;
import com.godlife.backend.notification.Notification;
import com.godlife.backend.notification.NotificationService;
import lombok.RequiredArgsConstructor;
import org.springframework.dao.DuplicateKeyException;
import org.springframework.jdbc.core.namedparam.MapSqlParameterSource;
import org.springframework.jdbc.core.namedparam.NamedParameterJdbcTemplate;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.time.Clock;
import java.time.LocalDate;
import java.time.LocalDateTime;
import java.util.List;
import java.util.Map;

/**
 * 커뮤니티 글 · 댓글 신고.
 * - 신고만으로는 가려지지 않는다. 관리자가 보고 가리거나(ACCEPTED) 문제없음(DISMISSED)으로 처리한다.
 * - 내 글 · 댓글은 신고할 수 없고, 같은 대상은 한 번만, 하루 {@value #DAILY_LIMIT}건까지만 신고할 수 있다 (신고 남발 방지).
 * - 처리되면 신고한 사람들에게 결과를 알리고, 가려지면 쓴 사람에게도 알린다.
 */
@Service
@RequiredArgsConstructor
public class CommunityReportService {

    static final int DAILY_LIMIT = 10;

    public enum Target {
        POST("posts", "글"), COMMENT("post_comments", "댓글");

        private final String table;
        private final String label;

        Target(String table, String label) {
            this.table = table;
            this.label = label;
        }
    }

    private final NamedParameterJdbcTemplate jdbc;
    private final NotificationService notificationService;
    private final Clock clock;

    @Transactional
    public void report(Long reporterId, Target target, Long targetId, String reason) {
        Long authorId = jdbc.queryForList(
                        "SELECT user_id FROM " + target.table + " WHERE id = :id AND status = 'VISIBLE'",
                        new MapSqlParameterSource("id", targetId), Long.class).stream().findFirst()
                .orElseThrow(() -> new BusinessException(
                        target == Target.POST ? ErrorCode.POST_NOT_FOUND : ErrorCode.COMMENT_NOT_FOUND));
        if (authorId.equals(reporterId)) {
            throw new BusinessException(ErrorCode.VALIDATION_ERROR, "내가 쓴 " + target.label + "은 신고할 수 없어요.");
        }
        Integer today = jdbc.queryForObject("""
                SELECT COUNT(*) FROM community_reports WHERE reporter_id = :reporter AND created_at >= :from
                """, new MapSqlParameterSource("reporter", reporterId)
                .addValue("from", LocalDate.now(clock).atStartOfDay()), Integer.class);
        if (today != null && today >= DAILY_LIMIT) {
            throw new BusinessException(ErrorCode.TOO_MANY_REQUESTS, "오늘은 더 신고할 수 없어요. 내일 다시 시도해 주세요.");
        }
        try {
            jdbc.update("""
                    INSERT INTO community_reports (target_type, target_id, reporter_id, reason)
                    VALUES (:type, :id, :reporter, :reason)
                    """, new MapSqlParameterSource("type", target.name()).addValue("id", targetId)
                    .addValue("reporter", reporterId).addValue("reason", reason.strip()));
        } catch (DuplicateKeyException e) {
            throw new BusinessException(ErrorCode.ALREADY_REPORTED, "이미 신고한 " + target.label + "이에요.");
        }
    }

    /** 관리자: 아직 처리하지 않은 신고 (같은 글 · 댓글에 온 신고는 한 줄로 묶는다, 오래된 것부터) */
    @Transactional(readOnly = true)
    public List<ReportedItem> open() {
        return jdbc.query("""
                SELECT r.target_type, r.target_id, COUNT(*) AS reports, MIN(r.created_at) AS first_at,
                       GROUP_CONCAT(r.reason ORDER BY r.id SEPARATOR '\n') AS reasons,
                       MAX(COALESCE(p.id, c.post_id)) AS post_id,
                       MAX(COALESCE(p.title, cp.title)) AS title,
                       MAX(COALESCE(p.content, c.content)) AS content,
                       MAX(u.nickname) AS author
                FROM community_reports r
                  LEFT JOIN posts p ON r.target_type = 'POST' AND p.id = r.target_id
                  LEFT JOIN post_comments c ON r.target_type = 'COMMENT' AND c.id = r.target_id
                  LEFT JOIN posts cp ON cp.id = c.post_id
                  LEFT JOIN users u ON u.id = COALESCE(p.user_id, c.user_id)
                WHERE r.status = 'OPEN'
                GROUP BY r.target_type, r.target_id
                ORDER BY first_at
                LIMIT 100
                """, new MapSqlParameterSource(),
                (rs, i) -> new ReportedItem(rs.getString("target_type"), rs.getLong("target_id"),
                        rs.getObject("post_id", Long.class), rs.getString("title"), rs.getString("content"),
                        rs.getString("author"), rs.getInt("reports"), List.of(rs.getString("reasons").split("\n")),
                        rs.getTimestamp("first_at").toLocalDateTime()));
    }

    /**
     * 관리자: 신고 처리. hide = true 면 글 · 댓글을 가리고, false 면 문제없음으로 닫는다.
     * 열려 있는 신고 줄을 잠그고 처리하므로 두 관리자가 동시에 눌러도 한 번만 처리된다.
     */
    @Transactional
    public void handle(Target target, Long targetId, boolean hide) {
        MapSqlParameterSource params = new MapSqlParameterSource("type", target.name()).addValue("id", targetId);
        List<Long> reporters = jdbc.queryForList("""
                SELECT reporter_id FROM community_reports
                WHERE target_type = :type AND target_id = :id AND status = 'OPEN' FOR UPDATE
                """, params, Long.class);
        if (reporters.isEmpty()) {
            throw new BusinessException(ErrorCode.REVIEW_CLOSED, "이미 처리했거나 없는 신고예요.");
        }
        jdbc.update("""
                UPDATE community_reports SET status = :status, handled_at = :now
                WHERE target_type = :type AND target_id = :id AND status = 'OPEN'
                """, params.addValue("status", hide ? "ACCEPTED" : "DISMISSED")
                .addValue("now", LocalDateTime.now(clock)));

        Map<String, Object> row = jdbc.queryForList("SELECT * FROM " + target.table + " WHERE id = :id", params)
                .stream().findFirst().orElse(null);
        Long postId = row == null ? null
                : ((Number) row.get(target == Target.POST ? "id" : "post_id")).longValue();
        boolean hidden = false;
        if (hide && row != null) {
            hidden = jdbc.update("UPDATE " + target.table + " SET status = 'HIDDEN' WHERE id = :id AND status = 'VISIBLE'",
                    params) > 0;
            if (hidden && target == Target.COMMENT) {
                jdbc.update("UPDATE posts SET comment_count = comment_count - 1 WHERE id = :post AND comment_count > 0",
                        new MapSqlParameterSource("post", postId));
            }
        }

        String result = hide ? "그 " + target.label + "은 가려졌어요." : "검토 결과 문제가 없었어요.";
        String key = "community-report:" + target.name() + ":" + targetId;
        for (Long reporter : reporters) {
            notificationService.notify(reporter, Notification.Type.REPORT_RESULT, "신고 결과를 알려 드려요",
                    "커뮤니티에서 신고하신 " + target.label + "을 확인했어요. " + result,
                    hide || postId == null ? "/community" : "/community/" + postId, key);
        }
        if (hidden) {
            notificationService.notify(((Number) row.get("user_id")).longValue(), Notification.Type.REPORT_RESULT,
                    "내 " + target.label + "이 가려졌어요",
                    "커뮤니티에 쓴 " + target.label + "이 신고를 받아 관리자 확인 뒤 가려졌어요.",
                    target == Target.POST ? "/community" : "/community/" + postId, key);
        }
    }
}
