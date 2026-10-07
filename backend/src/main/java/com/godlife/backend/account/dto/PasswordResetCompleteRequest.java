package com.godlife.backend.account.dto;

import com.godlife.backend.common.validation.InputRules;
import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.Pattern;
import jakarta.validation.constraints.Size;

public record PasswordResetCompleteRequest(
        @NotBlank(message = "처음부터 다시 진행해 주세요.")
        String resetToken,

        @NotBlank(message = "비밀번호를 입력해 주세요.")
        @Size(min = 8, message = InputRules.PASSWORD_MIN_MESSAGE)
        @Size(max = 20, message = InputRules.PASSWORD_MAX_MESSAGE)
        @Pattern(regexp = InputRules.PASSWORD, message = InputRules.PASSWORD_MESSAGE)
        String newPassword) {

    /** 로그에 토큰/비밀번호가 찍히지 않도록 마스킹한다. */
    @Override
    public String toString() {
        return "PasswordResetCompleteRequest[resetToken=***, newPassword=***]";
    }
}
