package com.godlife.backend.verification;

/**
 * PENDING = AI 판정 대기, IN_REVIEW = 사람 검토 중 (2차 AI 검증에서 쓴다).
 * 지금은 AI 서버가 붙기 전이라 올리면 바로 APPROVED 로 저장한다.
 */
public enum VerificationStatus {
    PENDING, APPROVED, REJECTED, IN_REVIEW
}
