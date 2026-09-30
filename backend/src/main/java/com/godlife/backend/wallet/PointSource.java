package com.godlife.backend.wallet;

/**
 * 포인트 출처 (CLAUDE.md 규칙 2). 한 잔액으로 합치지 않는다.
 * CHARGED = 직접 충전. 챌린지 참가비로 쓰고, 쓰지 않은 만큼만 결제 취소로 환불할 수 있다.
 * REWARD = 챌린지 보상·이벤트. 포인트 상점에서만 쓰고, 환불·현금화할 수 없다.
 * SHOP = 상점에서 생기는 포인트 (상점을 만들 때 쓴다).
 */
public enum PointSource {
    CHARGED, REWARD, SHOP
}
