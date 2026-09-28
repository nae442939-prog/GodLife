package com.godlife.backend.user.dto;

import com.godlife.backend.user.User;

import java.time.LocalDateTime;

public record UserResponse(Long id, String email, String nickname, String role, LocalDateTime createdAt) {

    public static UserResponse from(User user) {
        return new UserResponse(user.getId(), user.getEmail(), user.getNickname(),
                user.getRole().name(), user.getCreatedAt());
    }
}
