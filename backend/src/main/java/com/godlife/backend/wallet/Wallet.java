package com.godlife.backend.wallet;

import com.godlife.backend.common.error.BusinessException;
import com.godlife.backend.common.error.ErrorCode;
import jakarta.persistence.Column;
import jakarta.persistence.Entity;
import jakarta.persistence.GeneratedValue;
import jakarta.persistence.GenerationType;
import jakarta.persistence.Id;
import jakarta.persistence.Table;
import lombok.AccessLevel;
import lombok.Getter;
import lombok.NoArgsConstructor;

/**
 * 포인트 지갑. 출처별 잔액(충전·보상)은 원장(point_transactions)의 캐시이고,
 * 모든 증감은 이 행을 SELECT ... FOR UPDATE 로 잠근 채 원장 한 줄과 함께 바꾼다.
 */
@Getter
@NoArgsConstructor(access = AccessLevel.PROTECTED)
@Entity
@Table(name = "wallets")
public class Wallet {

    @Id
    @GeneratedValue(strategy = GenerationType.IDENTITY)
    private Long id;

    @Column(name = "user_id", nullable = false, updatable = false)
    private Long userId;

    /** 전체 잔액 (= 충전 + 보상) */
    @Column(nullable = false)
    private long balance;

    @Column(name = "charged_balance", nullable = false)
    private long chargedBalance;

    @Column(name = "reward_balance", nullable = false)
    private long rewardBalance;

    public long balanceOf(PointSource source) {
        return switch (source) {
            case CHARGED -> chargedBalance;
            case REWARD -> rewardBalance;
            case SHOP -> 0;
        };
    }

    /** 출처별 잔액을 바꾸고 바뀐 뒤 그 출처의 잔액을 돌려준다. 모자라면 INSUFFICIENT_POINTS. */
    long apply(PointSource source, long amount) {
        long after = balanceOf(source) + amount;
        if (after < 0) {
            throw new BusinessException(ErrorCode.INSUFFICIENT_POINTS);
        }
        switch (source) {
            case CHARGED -> chargedBalance = after;
            case REWARD -> rewardBalance = after;
            case SHOP -> throw new IllegalArgumentException("상점 포인트는 아직 쓰지 않는다");
        }
        balance = chargedBalance + rewardBalance;
        return after;
    }
}
