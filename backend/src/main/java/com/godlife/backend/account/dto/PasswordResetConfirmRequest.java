package com.godlife.backend.account.dto;

import com.godlife.backend.common.validation.InputRules;
import jakarta.validation.constraints.Email;
import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.Pattern;

public record PasswordResetConfirmRequest(
        @NotBlank(message = "이메일을 입력해 주세요.")
        @Email(message = "이메일 형식이 올바르지 않습니다.")
        String email,

        @NotBlank(message = InputRules.CODE_MESSAGE)
        @Pattern(regexp = InputRules.CODE, message = InputRules.CODE_MESSAGE)
        String code) {
}
