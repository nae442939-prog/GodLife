package com.godlife.backend.user.dto;

import jakarta.validation.constraints.NotBlank;

public record PhoneRegisterRequest(
        @NotBlank(message = "휴대폰 인증을 완료해 주세요.")
        String phoneProof) {
}
