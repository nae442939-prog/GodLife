package com.godlife.backend.account.dto;

import jakarta.validation.constraints.NotBlank;

public record FindIdRequest(
        @NotBlank(message = "휴대폰 인증을 완료해 주세요.")
        String phoneProof) {
}
