package com.godlife.backend.auth.dto;

import com.godlife.backend.common.validation.InputRules;
import jakarta.validation.constraints.Email;
import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.Pattern;
import jakarta.validation.constraints.Size;

public record SignupRequest(
        @NotBlank(message = "이메일을 입력해 주세요.")
        @Email(message = "이메일 형식이 올바르지 않습니다.")
        @Size(max = 255, message = "이메일은 255자 이하여야 합니다.")
        String email,

        @NotBlank(message = "비밀번호를 입력해 주세요.")
        @Size(min = 8, message = InputRules.PASSWORD_MIN_MESSAGE)
        @Size(max = 20, message = InputRules.PASSWORD_MAX_MESSAGE)
        @Pattern(regexp = InputRules.PASSWORD, message = InputRules.PASSWORD_MESSAGE)
        String password,

        @NotBlank(message = "닉네임을 입력해 주세요.")
        @Size(min = 2, max = 20, message = "닉네임은 2~20자여야 합니다.")
        @Pattern(regexp = InputRules.NICKNAME, message = InputRules.NICKNAME_MESSAGE)
        String nickname,

        /** 휴대폰 인증 완료 증표 (POST /api/phone-verifications/confirm 의 응답) */
        @NotBlank(message = "휴대폰 인증을 완료해 주세요.")
        String phoneProof) {

    /** 로그에 비밀번호/증표가 찍히지 않도록 마스킹한다. */
    @Override
    public String toString() {
        return "SignupRequest[email=" + email + ", password=***, nickname=" + nickname + ", phoneProof=***]";
    }
}
