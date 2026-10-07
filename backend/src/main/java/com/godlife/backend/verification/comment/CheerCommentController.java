package com.godlife.backend.verification.comment;

import com.godlife.backend.auth.AuthUser;
import com.godlife.backend.verification.comment.CheerCommentService.CheerComment;
import jakarta.validation.Valid;
import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.Size;
import lombok.RequiredArgsConstructor;
import org.springframework.http.HttpStatus;
import org.springframework.http.ResponseEntity;
import org.springframework.security.core.annotation.AuthenticationPrincipal;
import org.springframework.web.bind.annotation.DeleteMapping;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RestController;

import java.util.List;
import java.util.Map;

/** 인증 사진 응원 댓글 (그 챌린지의 개설자 · 참가자만) */
@RestController
@RequiredArgsConstructor
public class CheerCommentController {

    private final CheerCommentService cheerService;

    public record CheerRequest(
            @NotBlank(message = "응원 댓글을 입력해 주세요.")
            @Size(max = CheerCommentService.MAX_CONTENT, message = "응원 댓글은 300자까지 쓸 수 있어요.")
            String content) {
    }

    @GetMapping("/api/challenges/{id}/verifications/{verificationId}/comments")
    public List<CheerComment> list(@PathVariable Long id, @PathVariable Long verificationId,
                                   @AuthenticationPrincipal AuthUser authUser) {
        return cheerService.list(id, verificationId, authUser.id());
    }

    @PostMapping("/api/challenges/{id}/verifications/{verificationId}/comments")
    public ResponseEntity<Map<String, Long>> write(@PathVariable Long id, @PathVariable Long verificationId,
                                                   @AuthenticationPrincipal AuthUser authUser,
                                                   @Valid @RequestBody CheerRequest request) {
        Long commentId = cheerService.write(id, verificationId, authUser.id(), request.content());
        return ResponseEntity.status(HttpStatus.CREATED).body(Map.of("id", commentId));
    }

    @DeleteMapping("/api/challenges/{id}/verification-comments/{commentId}")
    public ResponseEntity<Void> delete(@PathVariable Long id, @PathVariable Long commentId,
                                       @AuthenticationPrincipal AuthUser authUser) {
        cheerService.delete(id, commentId, authUser.id());
        return ResponseEntity.noContent().build();
    }
}
