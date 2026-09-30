package com.godlife.backend.wallet.dto;

import java.util.List;

/**
 * 내 지갑.
 * @param chargedBalance 직접 충전한 포인트 (챌린지 참가비로 쓴다)
 * @param rewardBalance  챌린지 보상 포인트 (포인트 상점 전용)
 * @param betToday       오늘 챌린지에 건 포인트 (취소로 돌려받은 것은 뺌)
 * @param chargedToday   오늘 테스트 충전한 포인트
 * @param newbie         가입 30일 이내라 베팅 한도가 낮은지
 */
public record WalletResponse(long balance, long chargedBalance, long rewardBalance,
                             long betDailyLimit, long betToday, long betMonthlyLimit, long betThisMonth,
                             long chargeDailyLimit, long chargedToday, boolean newbie,
                             List<PointTransactionResponse> transactions) {
}
