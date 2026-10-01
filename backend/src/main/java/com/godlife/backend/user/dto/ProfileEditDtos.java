package com.godlife.backend.user.dto;

import com.godlife.backend.common.validation.InputRules;
import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.Pattern;
import jakarta.validation.constraints.Size;

import java.util.List;

public final class ProfileEditDtos {

    private ProfileEditDtos() {
    }

    /** 닉네임 · 자기소개 고치기. 닉네임 규칙은 가입할 때와 같다. 자기소개는 비우면 지운다. */
    public record ProfileUpdateRequest(
            @NotBlank(message = "닉네임을 입력해 주세요.")
            @Size(min = 2, max = 20, message = "닉네임은 2~20자여야 합니다.")
            @Pattern(regexp = InputRules.NICKNAME, message = InputRules.NICKNAME_MESSAGE)
            String nickname,

            @Size(max = 200, message = "자기소개는 200자까지 쓸 수 있어요.")
            String bio) {
    }

    /** 비밀번호 바꾸기. 새 비밀번호 규칙은 가입할 때와 같다. */
    public record PasswordChangeRequest(
            @NotBlank(message = "현재 비밀번호를 입력해 주세요.")
            String currentPassword,

            @NotBlank(message = "새 비밀번호를 입력해 주세요.")
            @Size(min = 8, message = InputRules.PASSWORD_MIN_MESSAGE)
            @Size(max = 20, message = InputRules.PASSWORD_MAX_MESSAGE)
            @Pattern(regexp = InputRules.PASSWORD, message = InputRules.PASSWORD_MESSAGE)
            String newPassword) {

        /** 로그에 비밀번호가 찍히지 않도록 가린다. */
        @Override
        public String toString() {
            return "PasswordChangeRequest[currentPassword=***, newPassword=***]";
        }
    }

    /**
     * 설정의 '계정' 칸.
     * @param hasPassword     비밀번호가 있는지 (소셜로만 가입했으면 false → 비밀번호 바꾸기 대신 안내)
     * @param socialProviders 연결된 소셜 계정 — KAKAO / GOOGLE / NAVER
     */
    public record AccountResponse(String email, boolean hasPassword, List<String> socialProviders) {
    }
}
