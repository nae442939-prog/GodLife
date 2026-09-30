package com.godlife.backend.settlement;

import jakarta.persistence.Column;
import jakarta.persistence.Entity;
import jakarta.persistence.GeneratedValue;
import jakarta.persistence.GenerationType;
import jakarta.persistence.Id;
import jakarta.persistence.Table;
import lombok.AccessLevel;
import lombok.Getter;
import lombok.NoArgsConstructor;

import java.math.BigDecimal;
import java.math.RoundingMode;

/** 최종 정산의 참가자 한 명 몫: 매일 결과를 더한 환급·보상·잃은 포인트와 지급한 원장 줄 */
@Getter
@NoArgsConstructor(access = AccessLevel.PROTECTED)
@Entity
@Table(name = "settlement_items")
public class SettlementItem {

    @Id
    @GeneratedValue(strategy = GenerationType.IDENTITY)
    private Long id;

    @Column(name = "settlement_id", nullable = false, updatable = false)
    private Long settlementId;

    @Column(name = "participant_id", nullable = false, updatable = false)
    private Long participantId;

    /** 필요한 인증 중 채운 비율 (0~100) */
    @Column(name = "success_rate", nullable = false)
    private BigDecimal successRate;

    @Column(name = "refund_amount", nullable = false)
    private long refundAmount;

    @Column(name = "reward_amount", nullable = false)
    private long rewardAmount;

    @Column(name = "forfeit_amount", nullable = false)
    private long forfeitAmount;

    /** 어느 날이든 보상 상한에 걸렸는지 */
    @Column(nullable = false)
    private boolean capped;

    @Column(name = "refund_tx_id")
    private Long refundTxId;

    @Column(name = "reward_tx_id")
    private Long rewardTxId;

    static SettlementItem of(Long settlementId, Long participantId, int done, int target, long refund, long reward,
                             long forfeit, boolean capped) {
        SettlementItem item = new SettlementItem();
        item.settlementId = settlementId;
        item.participantId = participantId;
        item.successRate = target == 0 ? BigDecimal.ZERO
                : BigDecimal.valueOf(Math.min(done, target) * 100L)
                        .divide(BigDecimal.valueOf(target), 2, RoundingMode.DOWN);
        item.refundAmount = refund;
        item.rewardAmount = reward;
        item.forfeitAmount = forfeit;
        item.capped = capped;
        return item;
    }

    void paid(Long refundTxId, Long rewardTxId) {
        this.refundTxId = refundTxId;
        this.rewardTxId = rewardTxId;
    }
}
