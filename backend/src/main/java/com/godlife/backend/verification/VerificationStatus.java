package com.godlife.backend.verification;

/**
 * APPROVED = 인정 (AI 통과, 또는 AI 서버가 꺼져 있어 판정 없이 받음),
 * IN_REVIEW = AI 가 애매하다고 봐서 관리자 검토 중 (그동안은 인증한 것으로 친다),
 * REJECTED = 관리자 검토에서 거절 (인증으로 치지 않는다).
 * AI 가 바로 거절한 사진은 저장하지 않으므로 줄이 남지 않는다. PENDING 은 쓰지 않는다 (판정을 올릴 때 바로 한다).
 */
public enum VerificationStatus {
    PENDING, APPROVED, REJECTED, IN_REVIEW
}
