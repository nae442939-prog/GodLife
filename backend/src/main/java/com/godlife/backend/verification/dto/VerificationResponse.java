package com.godlife.backend.verification.dto;

import com.godlife.backend.verification.VerificationStatus;

import java.time.LocalDateTime;
import java.util.Set;

/**
 * 인증 사진 목록 한 줄. 사진은 /verifications/{id}/image 로 따로 받는다.
 * status: APPROVED(인정) / IN_REVIEW(관리자 검토 중 — 그동안은 인증한 것으로 친다)
 * reported: 보는 사람이 이 인증을 이미 신고했는지
 */
public record VerificationResponse(Long id, Long userId, String nickname, String profileImageUrl,
                                   LocalDateTime receivedAt, VerificationStatus status, boolean mine,
                                   boolean reported) {

    /** 목록 쿼리(JPQL)가 쓰는 생성자. mine · reported 는 서비스에서 채운다 */
    public VerificationResponse(Long id, Long userId, String nickname, String profileImageUrl,
                                LocalDateTime receivedAt, VerificationStatus status) {
        this(id, userId, nickname, profileImageUrl, receivedAt, status, false, false);
    }

    public VerificationResponse withMine(Long viewerId) {
        return withViewer(viewerId, Set.of());
    }

    /** reportedIds = 보는 사람이 신고한 인증 id 들 */
    public VerificationResponse withViewer(Long viewerId, Set<Long> reportedIds) {
        return new VerificationResponse(id, userId, nickname, profileImageUrl, receivedAt, status,
                userId.equals(viewerId), reportedIds.contains(id));
    }
}
