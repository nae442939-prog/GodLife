package com.godlife.backend.verification.report;

import com.godlife.backend.auth.AuthUser;
import jakarta.validation.Valid;
import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.Size;
import lombok.RequiredArgsConstructor;
import org.springframework.http.ResponseEntity;
import org.springframework.security.core.annotation.AuthenticationPrincipal;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RestController;

/** 인증 신고. 로그인 + 그 챌린지의 개설자/참가자만 (아니면 404). */
@RestController
@RequiredArgsConstructor
public class VerificationReportController {

    private final VerificationReportService reportService;

    public record ReportRequest(
            @NotBlank(message = "신고 이유를 적어 주세요.")
            @Size(max = 200, message = "신고 이유는 200자까지 쓸 수 있어요.") String reason) {
    }

    /** 의심스러운 인증 사진 신고 → 관리자 검토 큐에 올라간다 */
    @PostMapping("/api/challenges/{id}/verifications/{verificationId}/reports")
    public ResponseEntity<Void> report(@PathVariable Long id, @PathVariable Long verificationId,
                                       @AuthenticationPrincipal AuthUser authUser,
                                       @Valid @RequestBody ReportRequest request) {
        reportService.report(id, verificationId, authUser.id(), request.reason());
        return ResponseEntity.noContent().build();
    }
}
