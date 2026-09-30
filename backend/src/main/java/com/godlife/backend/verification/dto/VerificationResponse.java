package com.godlife.backend.verification.dto;

import java.time.LocalDateTime;

/** 인증 사진 목록 한 줄. 사진은 /verifications/{id}/image 로 따로 받는다. */
public record VerificationResponse(Long id, Long userId, String nickname, String profileImageUrl,
                                   LocalDateTime receivedAt, boolean mine) {

    public VerificationResponse withMine(Long viewerId) {
        return new VerificationResponse(id, userId, nickname, profileImageUrl, receivedAt, userId.equals(viewerId));
    }
}
