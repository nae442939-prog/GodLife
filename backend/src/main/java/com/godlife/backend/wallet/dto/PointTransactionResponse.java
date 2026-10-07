package com.godlife.backend.wallet.dto;

import com.godlife.backend.wallet.PointSource;
import com.godlife.backend.wallet.PointTransaction;
import com.godlife.backend.wallet.PointTxType;

import java.time.LocalDateTime;

/**
 * 거래 내역 한 줄. amount 는 부호 있는 증감액, balanceAfter 는 그 출처의 거래 후 잔액.
 * @param challengeTitle 챌린지 참가비·환급·정산이면 그 챌린지 제목 (챌린지가 지워졌으면 null)
 * @param settlement     챌린지가 끝나고 한 번에 받은 최종 정산 지급인지
 * @param orderId        상점 구매 · 주문 취소면 그 주문 (주문 상세로 갈 때 쓴다)
 * @param orderTitle     그 주문의 상품 ('요가 매트 외 1가지')
 */
public record PointTransactionResponse(Long id, PointTxType type, PointSource source, long amount, long balanceAfter,
                                       String challengeTitle, boolean settlement, Long orderId, String orderTitle,
                                       LocalDateTime createdAt) {

    public static PointTransactionResponse from(PointTransaction t, String challengeTitle, String orderTitle) {
        boolean order = PointTransaction.REF_ORDER.equals(t.getRefType());
        return new PointTransactionResponse(t.getId(), t.getType(), t.getSource(), t.getAmount(), t.getBalanceAfter(),
                challengeTitle, PointTransaction.REF_SETTLEMENT.equals(t.getRefType()),
                order ? t.getRefId() : null, order ? orderTitle : null, t.getCreatedAt());
    }
}
