package com.godlife.backend.community;

import com.godlife.backend.auth.AuthUser;
import com.godlife.backend.challenge.dto.PageResponse;
import com.godlife.backend.community.CommunityReportService.Target;
import com.godlife.backend.community.CommunityService.SortOption;
import com.godlife.backend.community.dto.CommunityDtos.Attachable;
import com.godlife.backend.community.dto.CommunityDtos.CommentRequest;
import com.godlife.backend.community.dto.CommunityDtos.Created;
import com.godlife.backend.community.dto.CommunityDtos.LikeResponse;
import com.godlife.backend.community.dto.CommunityDtos.PostDetail;
import com.godlife.backend.community.dto.CommunityDtos.PostRequest;
import com.godlife.backend.community.dto.CommunityDtos.PostSummary;
import com.godlife.backend.community.dto.CommunityDtos.ReportRequest;
import com.godlife.backend.community.dto.CommunityDtos.ReportedItem;
import com.godlife.backend.community.dto.CommunityDtos.Topic;
import jakarta.validation.Valid;
import lombok.RequiredArgsConstructor;
import org.springframework.core.io.FileSystemResource;
import org.springframework.core.io.Resource;
import org.springframework.http.CacheControl;
import org.springframework.http.HttpStatus;
import org.springframework.http.MediaType;
import org.springframework.http.ResponseEntity;
import org.springframework.security.core.annotation.AuthenticationPrincipal;
import org.springframework.web.bind.annotation.DeleteMapping;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.PutMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestParam;
import org.springframework.web.bind.annotation.RequestPart;
import org.springframework.web.bind.annotation.RestController;
import org.springframework.web.multipart.MultipartFile;

import java.time.Duration;
import java.util.List;
import java.util.Locale;

/** 커뮤니티. 글 목록 · 상세 · 사진은 비로그인도 볼 수 있고, 쓰기 · 좋아요 · 댓글 · 신고는 로그인 회원만. */
@RestController
@RequiredArgsConstructor
public class CommunityController {

    private final CommunityService communityService;
    private final CommunityReportService reportService;

    /** 글 목록 (?topic=FREE|REVIEW|TIP|QUESTION, ?q= 검색, ?sort=latest|popular, ?page=) */
    @GetMapping("/api/posts")
    public PageResponse<PostSummary> list(@RequestParam(required = false) Topic topic,
                                          @RequestParam(required = false) String q,
                                          @RequestParam(defaultValue = "latest") String sort,
                                          @RequestParam(defaultValue = "0") int page,
                                          @AuthenticationPrincipal AuthUser authUser) {
        return communityService.list(idOf(authUser), topic, q, parseSort(sort), page);
    }

    @GetMapping("/api/posts/{id}")
    public PostDetail detail(@PathVariable Long id, @AuthenticationPrincipal AuthUser authUser) {
        return communityService.detail(idOf(authUser), id);
    }

    /** 글 사진. 글이 공개라 사진도 누구나 받을 수 있다 (가려지거나 지운 글의 사진은 404). */
    @GetMapping("/api/post-images/{imageId}")
    public ResponseEntity<Resource> image(@PathVariable Long imageId) {
        return ResponseEntity.ok()
                .contentType(MediaType.IMAGE_JPEG)
                .cacheControl(CacheControl.maxAge(Duration.ofDays(1)))
                .body(new FileSystemResource(communityService.image(imageId)));
    }

    /** 글에 인증 결과를 붙일 수 있는 내 챌린지 */
    @GetMapping("/api/community/attachable")
    public List<Attachable> attachable(@AuthenticationPrincipal AuthUser authUser) {
        return communityService.attachable(authUser.id());
    }

    @PostMapping("/api/posts")
    public ResponseEntity<Created> create(@AuthenticationPrincipal AuthUser authUser,
                                          @Valid @RequestBody PostRequest request) {
        Long id = communityService.create(authUser.id(), request.topic(), request.title(), request.content(),
                request.challengeId());
        return ResponseEntity.status(HttpStatus.CREATED).body(new Created(id));
    }

    @PutMapping("/api/posts/{id}")
    public ResponseEntity<Void> update(@PathVariable Long id, @AuthenticationPrincipal AuthUser authUser,
                                       @Valid @RequestBody PostRequest request) {
        communityService.update(authUser.id(), id, request.topic(), request.title(), request.content());
        return ResponseEntity.noContent().build();
    }

    @DeleteMapping("/api/posts/{id}")
    public ResponseEntity<Void> delete(@PathVariable Long id, @AuthenticationPrincipal AuthUser authUser) {
        communityService.delete(authUser.id(), id);
        return ResponseEntity.noContent().build();
    }

    /** 글에 사진 한 장 붙이기 (multipart: file = JPG/PNG 5MB 이하, 글마다 4장까지) */
    @PostMapping(value = "/api/posts/{id}/images", consumes = MediaType.MULTIPART_FORM_DATA_VALUE)
    public ResponseEntity<Created> addImage(@PathVariable Long id, @AuthenticationPrincipal AuthUser authUser,
                                            @RequestPart("file") MultipartFile file) {
        return ResponseEntity.status(HttpStatus.CREATED)
                .body(new Created(communityService.addImage(authUser.id(), id, file)));
    }

    @DeleteMapping("/api/posts/{id}/images/{imageId}")
    public ResponseEntity<Void> removeImage(@PathVariable Long id, @PathVariable Long imageId,
                                            @AuthenticationPrincipal AuthUser authUser) {
        communityService.removeImage(authUser.id(), id, imageId);
        return ResponseEntity.noContent().build();
    }

    /** 좋아요 / 좋아요 취소 (여러 번 눌러도 결과가 같다) */
    @PutMapping("/api/posts/{id}/like")
    public LikeResponse like(@PathVariable Long id, @AuthenticationPrincipal AuthUser authUser) {
        return communityService.like(authUser.id(), id, true);
    }

    @DeleteMapping("/api/posts/{id}/like")
    public LikeResponse unlike(@PathVariable Long id, @AuthenticationPrincipal AuthUser authUser) {
        return communityService.like(authUser.id(), id, false);
    }

    @PostMapping("/api/posts/{id}/comments")
    public ResponseEntity<Created> comment(@PathVariable Long id, @AuthenticationPrincipal AuthUser authUser,
                                           @Valid @RequestBody CommentRequest request) {
        return ResponseEntity.status(HttpStatus.CREATED)
                .body(new Created(communityService.comment(authUser.id(), id, request.content(), request.parentId())));
    }

    @DeleteMapping("/api/post-comments/{id}")
    public ResponseEntity<Void> deleteComment(@PathVariable Long id, @AuthenticationPrincipal AuthUser authUser) {
        communityService.deleteComment(authUser.id(), id);
        return ResponseEntity.noContent().build();
    }

    /** 댓글 좋아요 / 좋아요 취소 */
    @PutMapping("/api/post-comments/{id}/like")
    public LikeResponse likeComment(@PathVariable Long id, @AuthenticationPrincipal AuthUser authUser) {
        return communityService.likeComment(authUser.id(), id, true);
    }

    @DeleteMapping("/api/post-comments/{id}/like")
    public LikeResponse unlikeComment(@PathVariable Long id, @AuthenticationPrincipal AuthUser authUser) {
        return communityService.likeComment(authUser.id(), id, false);
    }

    @PostMapping("/api/posts/{id}/reports")
    public ResponseEntity<Void> reportPost(@PathVariable Long id, @AuthenticationPrincipal AuthUser authUser,
                                           @Valid @RequestBody ReportRequest request) {
        reportService.report(authUser.id(), Target.POST, id, request.reason());
        return ResponseEntity.noContent().build();
    }

    @PostMapping("/api/post-comments/{id}/reports")
    public ResponseEntity<Void> reportComment(@PathVariable Long id, @AuthenticationPrincipal AuthUser authUser,
                                              @Valid @RequestBody ReportRequest request) {
        reportService.report(authUser.id(), Target.COMMENT, id, request.reason());
        return ResponseEntity.noContent().build();
    }

    /** 관리자: 처리 안 한 신고 */
    @GetMapping("/api/admin/community-reports")
    public List<ReportedItem> reports() {
        return reportService.open();
    }

    /** 관리자: 신고 처리 (type = POST|COMMENT, action = hide 가리기 | dismiss 문제없음) */
    @PostMapping("/api/admin/community-reports/{type}/{targetId}/{action}")
    public ResponseEntity<Void> handle(@PathVariable Target type, @PathVariable Long targetId,
                                       @PathVariable String action) {
        if (!action.equals("hide") && !action.equals("dismiss")) {
            return ResponseEntity.notFound().build();
        }
        reportService.handle(type, targetId, action.equals("hide"));
        return ResponseEntity.noContent().build();
    }

    private static Long idOf(AuthUser authUser) {
        return authUser == null ? null : authUser.id();
    }

    private static SortOption parseSort(String sort) {
        try {
            return SortOption.valueOf(sort.toUpperCase(Locale.ROOT));
        } catch (IllegalArgumentException e) {
            return SortOption.LATEST;
        }
    }
}
