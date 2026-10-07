package com.godlife.backend.community.dto;

import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.NotNull;
import jakarta.validation.constraints.Size;

import java.time.LocalDate;
import java.time.LocalDateTime;
import java.util.List;

/** 커뮤니티 요청·응답 */
public final class CommunityDtos {

    public static final int MAX_TITLE = 100;
    public static final int MAX_CONTENT = 2000;
    public static final int MAX_COMMENT = 300;
    public static final int MAX_REASON = 200;

    private CommunityDtos() {
    }

    /** 말머리 */
    public enum Topic {
        FREE, REVIEW, TIP, QUESTION
    }

    /** 글쓴이 · 댓글 쓴 사람의 짧은 정보 */
    public record Author(Long id, String nickname, String profileImageUrl) {
    }

    /**
     * 글에 붙은 인증 결과. 쓴 시점에 서버가 인증 기록으로 정해 굳힌 값이다 (글쓴이가 고칠 수 없다).
     * @param success 그날 인증을 했는지
     */
    public record VerifyBadge(String challengeTitle, LocalDate date, boolean success) {
    }

    /**
     * 목록 한 줄.
     * @param thumbnailId 첫 사진 (/api/post-images/{id}), 없으면 null
     */
    public record PostSummary(Long id, Topic topic, String title, String excerpt, Author author,
                              VerifyBadge verify, Long thumbnailId, int imageCount, int likeCount, int commentCount,
                              LocalDateTime createdAt) {
    }

    /**
     * 글 상세.
     * @param imageIds 사진들 (/api/post-images/{id})
     * @param liked    보는 사람이 좋아요를 눌렀는지
     * @param mine     보는 사람이 쓴 글인지
     * @param reported 보는 사람이 이미 신고했는지
     */
    public record PostDetail(Long id, Topic topic, String title, String content, Author author, VerifyBadge verify,
                             List<Long> imageIds, int likeCount, int commentCount, boolean liked, boolean mine,
                             boolean reported, LocalDateTime createdAt, LocalDateTime updatedAt,
                             List<CommentResponse> comments) {
    }

    /**
     * @param parentId 답글(대댓글)이면 원 댓글, 아니면 null
     * @param liked    보는 사람이 이 댓글에 좋아요를 눌렀는지
     * @param removed  지웠거나 가려진 댓글 (답글이 달려 있어 자리만 남김: 글쓴이 · 내용 없음)
     */
    public record CommentResponse(Long id, Long parentId, Author author, String content, LocalDateTime createdAt,
                                  int likeCount, boolean liked, boolean mine, boolean reported, boolean removed) {
    }

    /**
     * 글에 인증 결과를 붙일 수 있는 챌린지 (지금 참여해 진행 중인 것).
     * @param verifiedToday 오늘 인증했는지 (붙이면 이 값이 글에 굳는다)
     */
    public record Attachable(Long challengeId, String title, boolean verifiedToday) {
    }

    public record LikeResponse(boolean liked, int likeCount) {
    }

    public record Created(Long id) {
    }

    /** @param challengeId 인증 결과를 붙일 챌린지 (없으면 null). 고칠 때는 쓰지 않는다 */
    public record PostRequest(
            @NotNull(message = "말머리를 골라 주세요.")
            Topic topic,
            @NotBlank(message = "제목을 입력해 주세요.")
            @Size(max = MAX_TITLE, message = "제목은 100자까지 쓸 수 있어요.")
            String title,
            @NotBlank(message = "내용을 입력해 주세요.")
            @Size(max = MAX_CONTENT, message = "내용은 2000자까지 쓸 수 있어요.")
            String content,
            Long challengeId) {
    }

    /** @param parentId 답글을 달 댓글 (보통 댓글이면 null) */
    public record CommentRequest(
            @NotBlank(message = "댓글을 입력해 주세요.")
            @Size(max = MAX_COMMENT, message = "댓글은 300자까지 쓸 수 있어요.")
            String content,
            Long parentId) {
    }

    public record ReportRequest(
            @NotBlank(message = "신고하는 이유를 적어 주세요.")
            @Size(max = MAX_REASON, message = "신고 이유는 200자까지 쓸 수 있어요.")
            String reason) {
    }

    /**
     * 관리자 화면의 신고 한 건 (같은 글 · 댓글에 온 신고를 묶는다).
     * @param targetType POST / COMMENT
     * @param postId     글 id (댓글이면 그 댓글이 달린 글)
     * @param reasons    신고 이유들
     */
    public record ReportedItem(String targetType, Long targetId, Long postId, String title, String content,
                               String authorNickname, int reportCount, List<String> reasons,
                               LocalDateTime firstReportedAt) {
    }
}
