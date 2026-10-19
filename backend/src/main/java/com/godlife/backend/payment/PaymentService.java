package com.godlife.backend.payment;

import com.godlife.backend.common.error.BusinessException;
import com.godlife.backend.common.error.ErrorCode;
import com.godlife.backend.payment.dto.PaymentDtos.PaymentConfig;
import com.godlife.backend.payment.dto.PaymentDtos.ReadyResponse;
import com.godlife.backend.wallet.WalletService;
import com.godlife.backend.wallet.dto.WalletResponse;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.jdbc.core.namedparam.MapSqlParameterSource;
import org.springframework.jdbc.core.namedparam.NamedParameterJdbcTemplate;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.net.URLEncoder;
import java.nio.charset.StandardCharsets;
import java.sql.Timestamp;
import java.time.Clock;
import java.time.LocalDateTime;
import java.util.List;
import java.util.UUID;
import java.util.function.ToLongFunction;

/**
 * 포인트 충전 결제 (토스페이먼츠 · 카카오페이, 테스트 모드 전용 — 프로젝트 규칙 3).
 * 1. 준비: 서버가 주문(payments, READY)을 먼저 만들고 금액을 적어 둔다. 화면이 금액을 바꿔 보내도 승인 때 걸러진다.
 *    카카오페이는 이때 카카오페이에도 결제를 준비해 결제 고유번호(tid)를 주문에 적고, 결제창 주소를 돌려준다.
 * 2. 결제: 사용자가 토스 · 카카오페이 결제창에서 결제한다. 카드 정보는 PG 만 다루고 서버에는 결제키(tid)만 온다.
 * 3. 승인: 주문 행을 잠그고(SELECT ... FOR UPDATE) PG 에 승인을 요청한 뒤, 승인된 만큼 충전 포인트를 넣는다.
 *    같은 주문의 승인 요청이 두 번 와도(새로고침 · 재전송) 주문 잠금과 원장 멱등 키(pay:{결제 id})로 한 번만 충전된다 (규칙 4).
 * 잠그는 순서는 언제나 결제 → 지갑이다.
 */
@Slf4j
@Service
@RequiredArgsConstructor
public class PaymentService {

    /** 고를 수 있는 충전 금액 (1P = 1원) */
    static final List<Long> AMOUNTS = List.of(1_000L, 5_000L, 10_000L, 30_000L, 50_000L);
    /** payments.provider 값 */
    static final String TOSS = "TOSS";
    static final String KAKAOPAY = "KAKAOPAY";

    private final NamedParameterJdbcTemplate jdbc;
    private final WalletService walletService;
    private final TossClient toss;
    private final KakaoPayClient kakaoPay;
    private final Clock clock;

    /** 카카오페이 결제창이 끝나면 돌아올 화면 주소의 앞부분 */
    @Value("${app.frontend-url}")
    private String frontendUrl;

    /** paymentKey = 토스 결제키(승인 때 적힘) 또는 카카오페이 tid(준비 때 적힘) */
    private record Payment(Long id, long amount, String status, String provider, String paymentKey) {
    }

    public PaymentConfig config() {
        return new PaymentConfig(AMOUNTS, toss.configured(), toss.clientKey(), kakaoPay.configured());
    }

    /**
     * 결제 준비: 주문을 만든다. 정해진 금액만, 하루 충전 한도 안에서.
     * 토스는 화면이 이 주문 번호로 결제창을 띄우고, 카카오페이는 돌려준 결제창 주소로 이동한다.
     */
    @Transactional(noRollbackFor = PgException.class)
    public ReadyResponse ready(Long userId, long amount, String provider) {
        boolean kakao = KAKAOPAY.equals(provider);
        if (!AMOUNTS.contains(amount)) {
            throw new BusinessException(ErrorCode.INVALID_CHARGE_AMOUNT);
        }
        if (kakao ? !kakaoPay.configured() : !toss.configured()) {
            throw new BusinessException(ErrorCode.PAYMENT_NOT_CONFIGURED);
        }
        walletService.checkChargeLimit(userId, amount);

        String orderId = "GL" + UUID.randomUUID().toString().replace("-", "");
        String orderName = "갓생살기 포인트 %,dP".formatted(amount);
        jdbc.update("""
                INSERT INTO payments (user_id, provider, order_id, amount, status, is_test, created_at)
                VALUES (:user, :provider, :order, :amount, 'READY', TRUE, :now)
                """, new MapSqlParameterSource("user", userId).addValue("provider", kakao ? KAKAOPAY : TOSS)
                .addValue("order", orderId).addValue("amount", amount)
                .addValue("now", Timestamp.valueOf(LocalDateTime.now(clock))));
        if (!kakao) {
            return new ReadyResponse(orderId, amount, orderName, TOSS, null, null);
        }
        String back = frontendUrl + "/wallet/charge/";
        KakaoPayClient.Ready ready;
        try {
            // 카카오페이가 승인 주소 뒤에 ?pg_token=... 을 붙여 돌려보내서, 주문 번호 · 금액(화면 표시용)은 경로에 싣는다
            ready = kakaoPay.ready(orderId, userId, orderName, amount,
                    back + "kakao/" + orderId + "/" + amount,
                    back + "fail?orderId=" + orderId + "&message=" + encode("결제를 취소했어요."),
                    back + "fail?orderId=" + orderId + "&message=" + encode("카카오페이 결제에 실패했어요."));
        } catch (PgException e) {
            jdbc.update("UPDATE payments SET status = 'FAILED' WHERE order_id = :order",
                    new MapSqlParameterSource("order", orderId));
            throw e;
        }
        jdbc.update("UPDATE payments SET payment_key = :tid WHERE order_id = :order",
                new MapSqlParameterSource("order", orderId).addValue("tid", ready.tid()));
        return new ReadyResponse(orderId, amount, orderName, KAKAOPAY, ready.pcUrl(), ready.mobileUrl());
    }

    /** 토스 결제 승인 → 충전 */
    @Transactional(noRollbackFor = PgException.class)
    public WalletResponse confirm(Long userId, String paymentKey, String orderId, long amount) {
        return approve(userId, orderId, TOSS, amount, paymentKey,
                payment -> toss.confirm(paymentKey, orderId, payment.amount()));
    }

    /** 카카오페이 결제 승인 → 충전. 금액은 화면에서 받지 않고 준비 때 주문에 적어 둔 값으로만 승인한다 */
    @Transactional(noRollbackFor = PgException.class)
    public WalletResponse approveKakao(Long userId, String orderId, String pgToken) {
        return approve(userId, orderId, KAKAOPAY, null, null,
                payment -> kakaoPay.approve(payment.paymentKey(), orderId, userId, pgToken));
    }

    /**
     * 승인 공통. PG 가 거절하면 주문을 FAILED 로 남긴다 (그 기록은 되돌리지 않는다).
     * @param expectedAmount 화면이 보낸 금액 (토스). null 이면 비교하지 않는다
     * @param paymentKey     승인되면 적을 결제키. null 이면 이미 적힌 값(카카오페이 tid)을 그대로 둔다
     * @param pg             PG 승인 요청. 승인된 금액을 돌려준다
     */
    private WalletResponse approve(Long userId, String orderId, String provider, Long expectedAmount,
                                   String paymentKey, ToLongFunction<Payment> pg) {
        Payment payment = lock(userId, orderId);
        if (!provider.equals(payment.provider())) {
            throw new BusinessException(ErrorCode.PAYMENT_NOT_FOUND);
        }
        if ("PAID".equals(payment.status())) {
            return walletService.wallet(userId);
        }
        if (!"READY".equals(payment.status())) {
            throw new BusinessException(ErrorCode.PAYMENT_ALREADY_CLOSED);
        }
        if (expectedAmount != null && payment.amount() != expectedAmount) {
            throw new BusinessException(ErrorCode.PAYMENT_AMOUNT_MISMATCH);
        }
        walletService.checkChargeLimit(userId, payment.amount());
        long approved;
        try {
            approved = pg.applyAsLong(payment);
        } catch (PgException e) {
            markFailed(payment.id());
            throw e;
        }
        if (approved != payment.amount()) {
            // 주문 금액으로 승인을 요청하므로 달라질 일이 없지만, 다르면 주문 금액만큼만 넣는다
            log.warn("승인 금액이 주문과 다릅니다. 결제 {} 주문 {} 승인 {}", payment.id(), payment.amount(), approved);
        }
        Long txId = walletService.charge(userId, payment.amount(), payment.id());
        jdbc.update("""
                UPDATE payments SET status = 'PAID', payment_key = COALESCE(:key, payment_key),
                                    points_granted = :amount, transaction_id = :tx, paid_at = :now
                WHERE id = :id
                """, new MapSqlParameterSource("id", payment.id()).addValue("key", paymentKey)
                .addValue("amount", payment.amount()).addValue("tx", txId)
                .addValue("now", Timestamp.valueOf(LocalDateTime.now(clock))));
        return walletService.wallet(userId);
    }

    /** 사용자가 결제창을 닫았거나 PG 가 실패로 돌려보냈을 때: 아직 승인 전인 주문을 닫는다. */
    @Transactional
    public void fail(Long userId, String orderId) {
        jdbc.update("""
                UPDATE payments SET status = 'FAILED'
                WHERE order_id = :order AND user_id = :user AND status = 'READY'
                """, new MapSqlParameterSource("order", orderId).addValue("user", userId));
    }

    /** 내 주문 행을 잠근다. 남의 주문 · 없는 주문은 모두 '없음'이다. */
    private Payment lock(Long userId, String orderId) {
        return jdbc.query("""
                SELECT id, amount, status, provider, payment_key FROM payments
                WHERE order_id = :order AND user_id = :user
                FOR UPDATE
                """, new MapSqlParameterSource("order", orderId).addValue("user", userId),
                (rs, i) -> new Payment(rs.getLong("id"), rs.getLong("amount"), rs.getString("status"),
                        rs.getString("provider"), rs.getString("payment_key"))).stream()
                .findFirst().orElseThrow(() -> new BusinessException(ErrorCode.PAYMENT_NOT_FOUND));
    }

    private void markFailed(Long paymentId) {
        jdbc.update("UPDATE payments SET status = 'FAILED' WHERE id = :id",
                new MapSqlParameterSource("id", paymentId));
    }

    private static String encode(String text) {
        return URLEncoder.encode(text, StandardCharsets.UTF_8);
    }
}
