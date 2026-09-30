package com.godlife.backend.wallet.dto;

import com.godlife.backend.wallet.PointSource;
import com.godlife.backend.wallet.PointTransaction;
import com.godlife.backend.wallet.PointTxType;

import java.time.LocalDateTime;

/**
 * 거래 내역 한 줄. amount 는 부호 있는 증감액, balanceAfter 는 그 출처의 거래 후 잔액.
 * @param challengeTitle 챌린지 참가비·환급이면 그 챌린지 제목 (챌린지가 지워졌으면 null)
 */
public record PointTransactionResponse(Long id, PointTxType type, PointSource source, long amount, long balanceAfter,
                                       String challengeTitle, LocalDateTime createdAt) {

    public static PointTransactionResponse from(PointTransaction t, String challengeTitle) {
        return new PointTransactionResponse(t.getId(), t.getType(), t.getSource(), t.getAmount(), t.getBalanceAfter(),
                challengeTitle, t.getCreatedAt());
    }
}
