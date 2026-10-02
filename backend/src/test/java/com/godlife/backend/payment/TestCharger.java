package com.godlife.backend.payment;

import com.godlife.backend.auth.JwtProvider;
import com.godlife.backend.wallet.WalletService;
import lombok.RequiredArgsConstructor;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.stereotype.Component;
import org.springframework.transaction.annotation.Transactional;

import java.util.UUID;

/**
 * 테스트용 충전: PG 를 거치지 않고, 승인이 끝난 결제(PAID) 한 건과 그 충전 원장을 바로 만든다.
 * 충전 포인트가 필요한 다른 테스트(챌린지 참가 · 정산 · 상점)가 쓴다. 결제 흐름 자체는 PaymentApiTest 가 검증한다.
 * 롤백하지 않는 테스트는 끝나고 payments 도 지워야 한다 (payments.user_id → users).
 */
@Component
@RequiredArgsConstructor
public class TestCharger {

    private final JdbcTemplate jdbc;
    private final WalletService walletService;
    private final JwtProvider jwtProvider;

    @Transactional
    public void charge(Long userId, long amount) {
        String orderId = "TEST" + UUID.randomUUID().toString().replace("-", "");
        jdbc.update("""
                INSERT INTO payments (user_id, provider, order_id, payment_key, amount, points_granted, status, is_test,
                                      paid_at)
                VALUES (?, 'TOSS', ?, ?, ?, ?, 'PAID', TRUE, NOW())
                """, userId, orderId, "key-" + orderId, amount, amount);
        Long paymentId = jdbc.queryForObject("SELECT id FROM payments WHERE order_id = ?", Long.class, orderId);
        walletService.charge(userId, amount, paymentId);
    }

    public void charge(String token, long amount) {
        charge(jwtProvider.parse(token).id(), amount);
    }
}
