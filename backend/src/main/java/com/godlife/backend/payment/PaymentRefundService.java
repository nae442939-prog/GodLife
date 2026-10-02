package com.godlife.backend.payment;

import com.godlife.backend.common.error.BusinessException;
import com.godlife.backend.common.error.ErrorCode;
import com.godlife.backend.wallet.WalletService;
import com.godlife.backend.wallet.dto.WalletResponse;
import lombok.RequiredArgsConstructor;
import org.springframework.jdbc.core.namedparam.MapSqlParameterSource;
import org.springframework.jdbc.core.namedparam.NamedParameterJdbcTemplate;
import org.springframework.stereotype.Service;

import java.util.List;

/**
 * 충전 포인트 환불 (프로젝트 규칙 2): 쓰지 않은 충전 포인트를 PG 결제 취소(테스트 모드)로 돌려준다. 출금 · 송금이 아니다.
 * 보상 포인트는 대상이 아니다 — 충전 출처(CHARGED) 잔액과 승인된 결제만 본다.
 * 결제 한 건의 남은 금액보다 많이 환불하면 최근 결제부터 차례로 나눠 (부분) 취소한다.
 */
@Service
@RequiredArgsConstructor
public class PaymentRefundService {

    private final NamedParameterJdbcTemplate jdbc;
    private final WalletService walletService;
    private final PaymentRefundStep step;

    private record Cancelable(Long id, long remaining) {
    }

    /**
     * requestKey 는 화면이 누를 때마다 만드는 값이라, 같은 요청이 두 번 와도(더블클릭 · 재전송) 한 번만 환불된다.
     * 걸음(결제 한 건)마다 따로 커밋하므로 이 메서드에는 트랜잭션을 걸지 않는다.
     */
    public WalletResponse refund(Long userId, long amount, String requestKey) {
        if (amount < 100 || amount % 100 != 0) {
            throw new BusinessException(ErrorCode.INVALID_REFUND_AMOUNT);
        }
        String keyPrefix = "cc:" + userId + ":" + requestKey + ":";
        if (walletService.hasTransactionsWithKeyPrefix(keyPrefix)) {
            return walletService.wallet(userId);
        }
        long charged = walletService.chargedBalance(userId);
        if (amount > charged) {
            throw new BusinessException(ErrorCode.REFUND_EXCEEDS_CHARGED,
                    "쓰지 않은 충전 포인트 %,dP까지만 환불할 수 있어요.".formatted(charged));
        }
        List<Cancelable> payments = jdbc.query("""
                SELECT id, amount - canceled_amount AS remaining FROM payments
                WHERE user_id = :user AND status = 'PAID' AND amount > canceled_amount
                ORDER BY id DESC
                """, new MapSqlParameterSource("user", userId),
                (rs, i) -> new Cancelable(rs.getLong("id"), rs.getLong("remaining")));
        long cancelable = payments.stream().mapToLong(Cancelable::remaining).sum();
        if (amount > cancelable) {
            throw new BusinessException(ErrorCode.REFUND_EXCEEDS_CHARGED,
                    "결제 취소로 환불할 수 있는 금액은 %,dP까지예요.".formatted(cancelable));
        }

        long done = 0;
        for (Cancelable payment : payments) {
            long part = Math.min(amount - done, payment.remaining());
            try {
                step.cancelOne(userId, payment.id(), part, keyPrefix + payment.id(),
                        "cc-" + requestKey + "-" + payment.id());
            } catch (BusinessException e) {
                if (done == 0) {
                    throw e;
                }
                throw new BusinessException(ErrorCode.REFUND_FAILED,
                        "%,dP 중 %,dP만 환불됐어요. 나머지는 잠시 후 다시 시도해 주세요.".formatted(amount, done));
            }
            done += part;
            if (done >= amount) {
                break;
            }
        }
        return walletService.wallet(userId);
    }
}
