package com.godlife.backend.user.dto;

import com.godlife.backend.user.User;

import java.time.LocalDateTime;

/**
 * @param phoneVerified   false 면 프론트가 휴대폰 인증 화면으로 보낸다. (소셜 가입 직후)
 * @param profileImageUrl 없으면 null. 프론트가 기본 아이콘을 보여준다.
 */
public record UserResponse(Long id, String email, String nickname, String profileImageUrl, String role,
                           boolean phoneVerified, LocalDateTime createdAt) {

    public static UserResponse from(User user) {
        return new UserResponse(user.getId(), user.getEmail(), user.getNickname(), user.getProfileImageUrl(),
                user.getRole().name(), user.hasPhone(), user.getCreatedAt());
    }
}
