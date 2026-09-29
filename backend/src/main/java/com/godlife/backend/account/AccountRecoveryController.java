package com.godlife.backend.account;

import com.godlife.backend.account.dto.FindIdRequest;
import com.godlife.backend.account.dto.FindIdResponse;
import com.godlife.backend.account.dto.PasswordResetCompleteRequest;
import com.godlife.backend.account.dto.PasswordResetConfirmRequest;
import com.godlife.backend.account.dto.PasswordResetRequest;
import com.godlife.backend.account.dto.ResetTokenResponse;
import com.godlife.backend.phone.dto.SendCodeResponse;
import jakarta.servlet.http.HttpServletRequest;
import jakarta.validation.Valid;
import lombok.RequiredArgsConstructor;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;

/** 로그인 없이 쓴다. */
@RestController
@RequestMapping("/api/account")
@RequiredArgsConstructor
public class AccountRecoveryController {

    private final AccountRecoveryService service;

    @PostMapping("/find-id")
    public FindIdResponse findId(@Valid @RequestBody FindIdRequest request) {
        return service.findId(request.phoneProof());
    }

    /** 가입 여부와 관계없이 200. demoCode 는 데모 모드에서만 채워진다. */
    @PostMapping("/password-reset")
    public SendCodeResponse requestPasswordReset(@Valid @RequestBody PasswordResetRequest request,
                                                 HttpServletRequest http) {
        return new SendCodeResponse(service.requestPasswordReset(request.email(), http.getRemoteAddr()));
    }

    @PostMapping("/password-reset/confirm")
    public ResetTokenResponse confirmPasswordReset(@Valid @RequestBody PasswordResetConfirmRequest request) {
        return new ResetTokenResponse(service.confirmPasswordReset(request.email(), request.code()));
    }

    @PostMapping("/password-reset/complete")
    public ResponseEntity<Void> completePasswordReset(@Valid @RequestBody PasswordResetCompleteRequest request) {
        service.completePasswordReset(request.resetToken(), request.newPassword());
        return ResponseEntity.noContent().build();
    }
}
