package com.godlife.backend.wallet.dto;

import java.util.List;

/**
 * 내 지갑.
 * @param chargedBalance 직접 충전한 포인트 (챌린지 참가비로 쓴다)
 * @param rewardBalance  챌린지 보상 포인트 (포인트 상점 전용)
 * @param refundable     지금 환불(결제 취소)할 수 있는 충전 포인트 (쓰지 않은 충전 포인트 중 아직 취소하지 않은 결제 금액까지)
 * @param betToday       오늘 챌린지에 건 포인트 (취소로 돌려받은 것은 뺌)
 * @param chargedToday   오늘 충전한 포인트
 * @param newbie         가입 30일 이내라 베팅 한도가 낮은지
 * @param tier           칭호 (BRONZE ~ DIAMOND, 관리자는 ADMIN). 가입 30일이 지나면 베팅 한도가 칭호에 따라 정해진다
 */
public record WalletResponse(long balance, long chargedBalance, long rewardBalance, long refundable,
                             long betDailyLimit, long betToday, long betMonthlyLimit, long betThisMonth,
                             long chargeDailyLimit, long chargedToday, boolean newbie, String tier,
                             List<PointTransactionResponse> transactions) {
}
