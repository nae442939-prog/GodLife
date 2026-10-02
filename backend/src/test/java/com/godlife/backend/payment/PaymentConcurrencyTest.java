package com.godlife.backend.payment;

import com.godlife.backend.common.error.ErrorCode;
import com.godlife.backend.payment.dto.PaymentDtos.ReadyResponse;
import com.godlife.backend.user.User;
import com.godlife.backend.user.UserRepository;
import com.godlife.backend.wallet.WalletService;
import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.test.context.ActiveProfiles;
import org.springframework.test.context.bean.override.mockito.MockitoBean;

import java.util.ArrayList;
import java.util.List;
import java.util.UUID;
import java.util.concurrent.Callable;
import java.util.concurrent.CountDownLatch;
import java.util.concurrent.ExecutorService;
import java.util.concurrent.Executors;
import java.util.concurrent.Future;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.mockito.ArgumentMatchers.anyLong;
import static org.mockito.ArgumentMatchers.anyString;
import static org.mockito.Mockito.doThrow;
import static org.mockito.Mockito.times;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

/**
 * 결제 승인 · 환불이 동시에 몰리거나 PG 가 중간에 거절해도 이중 충전 · 이중 환불 · PG 와 어긋난 원장이 없는지 (프로젝트 규칙 4).
 * 스레드(걸음)마다 트랜잭션이 따로 돌아야 해서 롤백하지 않고, 만든 데이터를 끝나고 직접 지운다.
 */
@SpringBootTest
@ActiveProfiles("test")
class PaymentConcurrencyTest {

    private static final int THREADS = 8;

    @Autowired PaymentService paymentService;
    @Autowired PaymentRefundService refundService;
    @Autowired WalletService walletService;
    @Autowired UserRepository userRepository;
    @Autowired JdbcTemplate jdbc;

    @MockitoBean TossClient toss;

    private final List<Long> userIds = new ArrayList<>();

    @BeforeEach
    void setUp() {
        when(toss.configured()).thenReturn(true);
        when(toss.confirm(anyString(), anyString(), anyLong())).thenAnswer(call -> call.getArgument(2));
    }

    @AfterEach
    void cleanUp() {
        userIds.forEach(id -> {
            jdbc.update("DELETE FROM payments WHERE user_id = ?", id);
            jdbc.update("DELETE t FROM point_transactions t JOIN wallets w ON w.id = t.wallet_id WHERE w.user_id = ?", id);
            jdbc.update("DELETE FROM wallets WHERE user_id = ?", id);
            jdbc.update("DELETE FROM users WHERE id = ?", id);
        });
    }

    @Test
    @DisplayName("같은 결제의 승인 요청이 동시에 8번 와도 PG 승인도 충전도 한 번만 된다")
    void confirmOnceUnderConcurrency() throws Exception {
        Long me = newUser();
        ReadyResponse order = paymentService.ready(me, 10_000);

        int ok = runAll(() -> paymentService.confirm(me, "pay-key", order.orderId(), 10_000));

        assertThat(ok).isEqualTo(THREADS);
        assertThat(walletService.wallet(me).chargedBalance()).isEqualTo(10_000);
        assertThat(count("SELECT COUNT(*) FROM point_transactions t JOIN wallets w ON w.id = t.wallet_id "
                + "WHERE w.user_id = ?", me)).isEqualTo(1);
        verify(toss, times(1)).confirm("pay-key", order.orderId(), 10_000);
    }

    @Test
    @DisplayName("같은 환불 요청이 동시에 8번 와도 PG 취소도 포인트 차감도 한 번만 된다")
    void refundOnceUnderConcurrency() throws Exception {
        Long me = newUser();
        charge(me, "pay-key", 10_000);

        runAll(() -> refundService.refund(me, 4_000, "double-click"));

        assertThat(walletService.wallet(me).chargedBalance()).isEqualTo(6_000);
        assertThat(walletService.wallet(me).refundable()).isEqualTo(6_000);
        verify(toss, times(1)).cancel("pay-key", 4_000, "충전 포인트 환불", "cc-double-click-" + paymentId(me));
    }

    @Test
    @DisplayName("PG 가 결제 취소를 거절하면 포인트 차감도 함께 되돌려진다")
    void pgRejectsCancel() {
        Long me = newUser();
        charge(me, "pay-key", 10_000);
        doThrow(new PgException(ErrorCode.REFUND_FAILED, "이미 취소된 결제예요."))
                .when(toss).cancel(anyString(), anyLong(), anyString(), anyString());

        assertThatThrownBy(() -> refundService.refund(me, 4_000, "rejected"))
                .isInstanceOf(PgException.class).hasMessage("이미 취소된 결제예요.");

        assertThat(walletService.wallet(me).chargedBalance()).isEqualTo(10_000);
        assertThat(walletService.wallet(me).refundable()).isEqualTo(10_000);
        assertThat(count("SELECT canceled_amount FROM payments WHERE user_id = ?", me)).isZero();
    }

    // ---------- helpers ----------

    private void charge(Long userId, String paymentKey, long amount) {
        ReadyResponse order = paymentService.ready(userId, amount);
        paymentService.confirm(userId, paymentKey, order.orderId(), amount);
    }

    private long paymentId(Long userId) {
        return count("SELECT id FROM payments WHERE user_id = ?", userId);
    }

    /** 모두 한꺼번에 출발시키고 성공한 수를 센다 */
    private int runAll(Callable<Object> task) throws Exception {
        ExecutorService pool = Executors.newFixedThreadPool(THREADS);
        CountDownLatch start = new CountDownLatch(1);
        try {
            List<Future<Object>> futures = new ArrayList<>();
            for (int i = 0; i < THREADS; i++) {
                futures.add(pool.submit(() -> {
                    start.await();
                    return task.call();
                }));
            }
            start.countDown();
            int ok = 0;
            for (Future<Object> f : futures) {
                try {
                    f.get();
                    ok++;
                } catch (Exception ignored) {
                    // 거절된 요청
                }
            }
            return ok;
        } finally {
            pool.shutdownNow();
        }
    }

    private Long newUser() {
        String suffix = UUID.randomUUID().toString().replace("-", "").substring(0, 12);
        Long id = userRepository.saveAndFlush(User.createWithPassword(suffix + "@example.com", "unused", "y" + suffix,
                "y".repeat(52) + suffix)).getId();
        userIds.add(id);
        return id;
    }

    private long count(String sql, Object arg) {
        Long n = jdbc.queryForObject(sql, Long.class, arg);
        return n == null ? 0 : n;
    }
}
