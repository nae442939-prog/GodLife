package com.godlife.backend.verification.dto;

import com.godlife.backend.verification.VerificationStatus;

import java.time.LocalDateTime;

/**
 * 인증 사진 목록 한 줄. 사진은 /verifications/{id}/image 로 따로 받는다.
 * status: APPROVED(인정) / IN_REVIEW(관리자 검토 중 — 그동안은 인증한 것으로 친다)
 */
public record VerificationResponse(Long id, Long userId, String nickname, String profileImageUrl,
                                   LocalDateTime receivedAt, VerificationStatus status, boolean mine) {

    public VerificationResponse withMine(Long viewerId) {
        return new VerificationResponse(id, userId, nickname, profileImageUrl, receivedAt, status,
                userId.equals(viewerId));
    }
}
