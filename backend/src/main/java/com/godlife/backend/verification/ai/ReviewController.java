package com.godlife.backend.verification.ai;

import com.godlife.backend.auth.AuthUser;
import com.godlife.backend.verification.ai.ReviewService.Review;
import jakarta.validation.Valid;
import jakarta.validation.constraints.Size;
import lombok.RequiredArgsConstructor;
import org.springframework.core.io.FileSystemResource;
import org.springframework.core.io.Resource;
import org.springframework.http.CacheControl;
import org.springframework.http.MediaType;
import org.springframework.http.ResponseEntity;
import org.springframework.security.core.annotation.AuthenticationPrincipal;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestParam;
import org.springframework.web.bind.annotation.RestController;

import java.util.List;

/** 관리자: AI 가 넘긴 인증 검토 (/api/admin/** 는 관리자만 들어올 수 있다) */
@RestController
@RequiredArgsConstructor
public class ReviewController {

    private final ReviewService reviewService;

    public record DecisionRequest(@Size(max = 100, message = "메모는 100자까지 쓸 수 있어요.") String memo) {
    }

    /** 검토 목록 (?status=OPEN 이면 대기만) */
    @GetMapping("/api/admin/reviews")
    public List<Review> list(@RequestParam(required = false) String status) {
        return reviewService.list(status);
    }

    /** 검토할 인증 사진 (비교할 예전 사진도 같은 주소로 받는다) */
    @GetMapping("/api/admin/reviews/images/{verificationId}")
    public ResponseEntity<Resource> image(@PathVariable Long verificationId) {
        return ResponseEntity.ok()
                .contentType(MediaType.IMAGE_JPEG)
                .cacheControl(CacheControl.noStore())
                .body(new FileSystemResource(reviewService.image(verificationId)));
    }

    @PostMapping("/api/admin/reviews/{id}/approve")
    public ResponseEntity<Void> approve(@PathVariable Long id, @AuthenticationPrincipal AuthUser authUser,
                                        @Valid @RequestBody(required = false) DecisionRequest request) {
        reviewService.approve(id, authUser.id(), request == null ? null : request.memo());
        return ResponseEntity.noContent().build();
    }

    @PostMapping("/api/admin/reviews/{id}/reject")
    public ResponseEntity<Void> reject(@PathVariable Long id, @AuthenticationPrincipal AuthUser authUser,
                                       @Valid @RequestBody(required = false) DecisionRequest request) {
        reviewService.reject(id, authUser.id(), request == null ? null : request.memo());
        return ResponseEntity.noContent().build();
    }
}
