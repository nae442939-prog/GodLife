package com.godlife.backend.block.dto;

import java.time.LocalDateTime;

public record BlockedUserResponse(Long userId, String nickname, String profileImageUrl, LocalDateTime blockedAt) {
}
