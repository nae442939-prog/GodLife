package com.godlife.backend.user.dto;

import com.godlife.backend.user.User;

import java.time.LocalDateTime;

/**
 * @param phoneVerified   false 면 프론트가 휴대폰 인증 화면으로 보낸다. (소셜 가입 직후)
 * @param profileImageUrl 없으면 null. 프론트가 기본 아이콘을 보여준다.
 * @param bio             자기소개. 없으면 null
 * @param phone           인증한 휴대폰 번호 (010-1234-5678). 본인에게만 주고, 번호를 보관하기 전에 인증한 회원은 null
 */
public record UserResponse(Long id, String email, String nickname, String profileImageUrl, String bio, String role,
                           boolean phoneVerified, String phone, LocalDateTime createdAt) {

    public static UserResponse from(User user) {
        return from(user, null);
    }

    public static UserResponse from(User user, String phone) {
        return new UserResponse(user.getId(), user.getEmail(), user.getNickname(), user.getProfileImageUrl(),
                user.getBio(), user.getRole().name(), user.hasPhone(), phone, user.getCreatedAt());
    }
}
