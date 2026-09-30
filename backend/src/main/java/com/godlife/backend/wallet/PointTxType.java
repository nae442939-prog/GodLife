package com.godlife.backend.wallet;

/**
 * CHARGE = 충전(지금은 테스트 충전) / CHARGE_CANCEL = 충전 포인트 환불(결제 취소, 쓰지 않은 만큼만) / ENTRY_FEE = 챌린지 참가비 / REFUND = 참가비 환급(취소·삭제·강퇴·성공)
 * REWARD = 챌린지 보상 / PURCHASE = 상점 구매 / SEASON_BONUS = 시즌 보너스 / ADJUST = 관리자 조정
 */
public enum PointTxType {
    CHARGE, CHARGE_CANCEL, ENTRY_FEE, REFUND, REWARD, PURCHASE, SEASON_BONUS, ADJUST
}
