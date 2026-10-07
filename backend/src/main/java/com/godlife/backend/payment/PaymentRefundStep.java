package com.godlife.backend.payment;

import com.godlife.backend.common.error.BusinessException;
import com.godlife.backend.common.error.ErrorCode;
import com.godlife.backend.wallet.WalletService;
import lombok.RequiredArgsConstructor;
import org.springframework.jdbc.core.namedparam.MapSqlParameterSource;
import org.springframework.jdbc.core.namedparam.NamedParameterJdbcTemplate;
import org.springframework.stereotype.Component;
import org.springframework.transaction.annotation.Transactional;

import java.util.Map;

/**
 * 결제 한 건의 (부분) 취소 = 환불 한 걸음. 걸음마다 트랜잭션이 따로라, 여러 결제에 걸친 환불이 중간에 실패해도
 * 이미 토스에서 취소된 결제는 포인트 차감과 함께 그대로 남는다 (토스와 원장이 어긋나지 않는다).
 */
@Component
@RequiredArgsConstructor
public class PaymentRefundStep {

    private final NamedParameterJdbcTemplate jdbc;
    private final WalletService walletService;
    private final TossClient toss;

    /**
     * 결제 행 → 지갑 순서로 잠그고, 충전 포인트를 먼저 뺀 다음(아직 커밋 전) 토스 결제 취소를 부른다.
     * 토스가 거절하면 예외로 포인트 차감도 함께 되돌려진다. 잠근 채 부르므로 그 사이 같은 포인트가 다른 데 쓰일 수 없다.
     * @param ledgerKey 원장 멱등 키. 이미 있으면(같은 요청의 재전송) 아무것도 하지 않는다
     * @param pgKey     토스 멱등 키. 응답을 못 받고 다시 불러도 한 번만 취소된다
     */
    @Transactional
    public void cancelOne(Long userId, Long paymentId, long part, String ledgerKey, String pgKey) {
        Map<String, Object> row = jdbc.queryForList("""
                SELECT payment_key, amount, canceled_amount, status FROM payments
                WHERE id = :id AND user_id = :user
                FOR UPDATE
                """, new MapSqlParameterSource("id", paymentId).addValue("user", userId)).stream()
                .findFirst().orElseThrow(() -> new BusinessException(ErrorCode.PAYMENT_NOT_FOUND));
        if (!walletService.cancelCharge(userId, part, paymentId, ledgerKey)) {
            return;
        }
        long amount = ((Number) row.get("amount")).longValue();
        long canceled = ((Number) row.get("canceled_amount")).longValue();
        if (!"PAID".equals(row.get("status")) || amount - canceled < part) {
            throw new BusinessException(ErrorCode.REFUND_EXCEEDS_CHARGED,
                    "환불할 수 있는 금액이 바뀌었어요. 새로고침한 뒤 다시 시도해 주세요.");
        }
        toss.cancel((String) row.get("payment_key"), part, "충전 포인트 환불", pgKey);
        jdbc.update("UPDATE payments SET canceled_amount = :canceled, status = :status WHERE id = :id",
                new MapSqlParameterSource("id", paymentId).addValue("canceled", canceled + part)
                        .addValue("status", canceled + part >= amount ? "CANCELED" : "PAID"));
    }
}
