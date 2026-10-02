package com.godlife.backend.payment;

import com.godlife.backend.auth.JwtProvider;
import com.godlife.backend.common.error.ErrorCode;
import com.godlife.backend.user.User;
import com.godlife.backend.user.UserRepository;
import com.godlife.backend.wallet.PointSource;
import com.godlife.backend.wallet.PointTxType;
import com.godlife.backend.wallet.WalletService;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.boot.webmvc.test.autoconfigure.AutoConfigureMockMvc;
import org.springframework.http.MediaType;
import org.springframework.test.context.ActiveProfiles;
import org.springframework.test.context.bean.override.mockito.MockitoBean;
import org.springframework.test.web.servlet.MockMvc;
import org.springframework.test.web.servlet.ResultActions;
import org.springframework.transaction.annotation.Transactional;

import java.time.Clock;
import java.time.LocalDate;
import java.util.UUID;
import java.util.regex.Matcher;
import java.util.regex.Pattern;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.ArgumentMatchers.anyLong;
import static org.mockito.ArgumentMatchers.anyString;
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.times;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.post;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.jsonPath;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

/**
 * 포인트 충전 결제 · 충전 포인트 환불. 토스페이먼츠는 실제로 부르지 않고 가짜로 바꿔 끼운다.
 * 승인 · 취소가 PG 에 몇 번, 어떤 값으로 나갔는지까지 확인한다.
 */
@SpringBootTest
@AutoConfigureMockMvc
@ActiveProfiles("test")
@Transactional
class PaymentApiTest {

    private static final Pattern ORDER_ID = Pattern.compile("\"orderId\":\"([^\"]+)\"");
    private static final Pattern ID = Pattern.compile("\"id\":(\\d+)");

    @Autowired MockMvc mvc;
    @Autowired UserRepository userRepository;
    @Autowired JwtProvider jwtProvider;
    @Autowired WalletService walletService;
    @Autowired Clock clock;

    @MockitoBean TossClient toss;

    private User meUser;
    private String me;
    private String other;

    @BeforeEach
    void setUp() {
        meUser = newUser();
        me = tokenOf(meUser);
        other = tokenOf(newUser());
        when(toss.configured()).thenReturn(true);
        when(toss.clientKey()).thenReturn("test_ck_fake");
        when(toss.confirm(anyString(), anyString(), anyLong())).thenAnswer(call -> call.getArgument(2));
    }

    @Test
    @DisplayName("충전 화면 설정: 금액 목록과 토스 공개 키. 테스트 키가 없으면 결제를 준비할 수 없다")
    void config() throws Exception {
        mvc.perform(get("/api/payments/config").header("Authorization", "Bearer " + me))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.amounts[0]").value(1000))
                .andExpect(jsonPath("$.enabled").value(true))
                .andExpect(jsonPath("$.tossClientKey").value("test_ck_fake"));

        when(toss.configured()).thenReturn(false);
        ready(me, 5000).andExpect(status().isServiceUnavailable())
                .andExpect(jsonPath("$.code").value("PAYMENT_NOT_CONFIGURED"));
    }

    @Test
    @DisplayName("토스: 준비 → 승인하면 충전 포인트로 들어가고, 같은 승인을 다시 보내도 한 번만 충전된다")
    void tossChargeOnce() throws Exception {
        String orderId = orderIdOf(ready(me, 5000).andExpect(status().isOk())
                .andExpect(jsonPath("$.amount").value(5000)));

        confirm(me, "pay-key-1", orderId, 5000).andExpect(status().isOk())
                .andExpect(jsonPath("$.chargedBalance").value(5000))
                .andExpect(jsonPath("$.refundable").value(5000))
                .andExpect(jsonPath("$.chargedToday").value(5000))
                .andExpect(jsonPath("$.transactions[0].type").value("CHARGE"))
                .andExpect(jsonPath("$.transactions[0].source").value("CHARGED"));
        confirm(me, "pay-key-1", orderId, 5000).andExpect(status().isOk())
                .andExpect(jsonPath("$.chargedBalance").value(5000))
                .andExpect(jsonPath("$.transactions.length()").value(1));

        verify(toss, times(1)).confirm("pay-key-1", orderId, 5000);
    }

    @Test
    @DisplayName("금액을 바꿔 보내거나 남의 주문을 승인하려 하면 PG 를 부르지 않고 거절한다")
    void tamperedConfirmRejected() throws Exception {
        String orderId = orderIdOf(ready(me, 1000));

        confirm(me, "pay-key", orderId, 50000).andExpect(status().isBadRequest())
                .andExpect(jsonPath("$.code").value("PAYMENT_AMOUNT_MISMATCH"));
        confirm(other, "pay-key", orderId, 1000).andExpect(status().isNotFound())
                .andExpect(jsonPath("$.code").value("PAYMENT_NOT_FOUND"));

        verify(toss, never()).confirm(anyString(), anyString(), anyLong());
        wallet(me).andExpect(jsonPath("$.chargedBalance").value(0));
    }

    @Test
    @DisplayName("정해진 금액만, 하루 50,000P 까지 충전할 수 있다")
    void chargeLimits() throws Exception {
        ready(me, 3000).andExpect(status().isBadRequest())
                .andExpect(jsonPath("$.code").value("INVALID_CHARGE_AMOUNT"));

        // 한도 안에서 주문 두 개를 미리 만들어 두고, 하나를 승인해 한도를 채우면 나머지는 승인 때 거절된다
        String first = orderIdOf(ready(me, 50000));
        String second = orderIdOf(ready(me, 1000));
        confirm(me, "key-a", first, 50000).andExpect(status().isOk())
                .andExpect(jsonPath("$.chargedToday").value(50000));

        ready(me, 1000).andExpect(status().isConflict())
                .andExpect(jsonPath("$.code").value("CHARGE_LIMIT_EXCEEDED"));
        confirm(me, "key-b", second, 1000).andExpect(status().isConflict())
                .andExpect(jsonPath("$.code").value("CHARGE_LIMIT_EXCEEDED"));
        verify(toss, never()).confirm("key-b", second, 1000);
        wallet(me).andExpect(jsonPath("$.chargedBalance").value(50000));
    }

    @Test
    @DisplayName("PG 가 승인을 거절하면 충전되지 않고, 그 주문은 닫혀서 다시 승인할 수 없다")
    void pgRejectsConfirm() throws Exception {
        String orderId = orderIdOf(ready(me, 5000));
        when(toss.confirm("bad-key", orderId, 5000))
                .thenThrow(new PgException(ErrorCode.PAYMENT_FAILED, "카드 한도를 넘었어요."));

        confirm(me, "bad-key", orderId, 5000).andExpect(status().isBadGateway())
                .andExpect(jsonPath("$.code").value("PAYMENT_FAILED"))
                .andExpect(jsonPath("$.message").value("카드 한도를 넘었어요."));
        confirm(me, "good-key", orderId, 5000).andExpect(status().isConflict())
                .andExpect(jsonPath("$.code").value("PAYMENT_ALREADY_CLOSED"));
        wallet(me).andExpect(jsonPath("$.chargedBalance").value(0));
    }

    @Test
    @DisplayName("결제창을 닫으면 주문이 닫히고, 닫힌 주문은 승인되지 않는다")
    void canceledOrderIsClosed() throws Exception {
        String orderId = orderIdOf(ready(me, 5000));
        mvc.perform(post("/api/payments/" + orderId + "/fail").header("Authorization", "Bearer " + me))
                .andExpect(status().isNoContent());

        confirm(me, "key", orderId, 5000).andExpect(status().isConflict())
                .andExpect(jsonPath("$.code").value("PAYMENT_ALREADY_CLOSED"));
    }

    @Test
    @DisplayName("환불은 쓰지 않은 충전 포인트까지만, 최근 결제부터 PG 결제 취소로 나눠 돌려준다. 같은 요청은 한 번만 처리된다")
    void refundCancelsPayments() throws Exception {
        confirm(me, "first-key", orderIdOf(ready(me, 10000)), 10000);
        confirm(me, "second-key", orderIdOf(ready(me, 5000)), 5000);
        join(me, betChallenge(3000)); // 15,000P 중 3,000P 사용 → 12,000P 남음

        refund(me, 13000, key()).andExpect(status().isConflict())
                .andExpect(jsonPath("$.code").value("REFUND_EXCEEDS_CHARGED"));
        refund(me, 150, key()).andExpect(status().isBadRequest())
                .andExpect(jsonPath("$.code").value("INVALID_REFUND_AMOUNT"));

        // 7,000P: 나중에 결제한 5,000 전액 + 먼저 결제한 것에서 2,000 부분 취소
        String once = key();
        refund(me, 7000, once).andExpect(status().isOk())
                .andExpect(jsonPath("$.chargedBalance").value(5000))
                .andExpect(jsonPath("$.refundable").value(5000))
                .andExpect(jsonPath("$.transactions[0].type").value("CHARGE_CANCEL"))
                .andExpect(jsonPath("$.transactions[0].amount").value(-2000))
                .andExpect(jsonPath("$.transactions[1].type").value("CHARGE_CANCEL"))
                .andExpect(jsonPath("$.transactions[1].amount").value(-5000));
        refund(me, 7000, once).andExpect(status().isOk())
                .andExpect(jsonPath("$.chargedBalance").value(5000));

        verify(toss, times(1)).cancel(eq("second-key"), eq(5000L), anyString(), anyString());
        verify(toss, times(1)).cancel(eq("first-key"), eq(2000L), anyString(), anyString());
    }

    @Test
    @DisplayName("보상 포인트는 환불되지 않는다 (충전 포인트와 결제만 본다)")
    void rewardIsNotRefundable() throws Exception {
        walletService.settle(meUser.getId(), PointTxType.REWARD, PointSource.REWARD, 5000, null, null,
                "test:" + UUID.randomUUID());

        wallet(me).andExpect(jsonPath("$.rewardBalance").value(5000))
                .andExpect(jsonPath("$.refundable").value(0));
        refund(me, 1000, key()).andExpect(status().isConflict())
                .andExpect(jsonPath("$.code").value("REFUND_EXCEEDS_CHARGED"));
        verify(toss, never()).cancel(anyString(), anyLong(), anyString(), anyString());
    }

    // ---------- helpers ----------

    private ResultActions ready(String token, long amount) throws Exception {
        return mvc.perform(post("/api/payments/ready").header("Authorization", "Bearer " + token)
                .contentType(MediaType.APPLICATION_JSON)
                .content("{\"amount\":%d}".formatted(amount)));
    }

    private ResultActions confirm(String token, String paymentKey, String orderId, long amount) throws Exception {
        return mvc.perform(post("/api/payments/toss/confirm").header("Authorization", "Bearer " + token)
                .contentType(MediaType.APPLICATION_JSON)
                .content("{\"paymentKey\":\"%s\",\"orderId\":\"%s\",\"amount\":%d}"
                        .formatted(paymentKey, orderId, amount)));
    }

    private ResultActions refund(String token, long amount, String requestKey) throws Exception {
        return mvc.perform(post("/api/wallet/refund").header("Authorization", "Bearer " + token)
                .contentType(MediaType.APPLICATION_JSON)
                .content("{\"amount\":%d,\"requestKey\":\"%s\"}".formatted(amount, requestKey)));
    }

    private ResultActions wallet(String token) throws Exception {
        return mvc.perform(get("/api/wallet").header("Authorization", "Bearer " + token)).andExpect(status().isOk());
    }

    private static String orderIdOf(ResultActions result) throws Exception {
        Matcher m = ORDER_ID.matcher(result.andReturn().getResponse().getContentAsString());
        assertThat(m.find()).isTrue();
        return m.group(1);
    }

    private long betChallenge(long fee) throws Exception {
        LocalDate today = LocalDate.now(clock);
        String body = """
                {"categoryId":1,"title":"포인트 걸기","description":"매일 인증","mode":"BET",
                 "startDate":"%s","endDate":"%s","frequencyType":"DAILY","entryFee":%d,"maxParticipants":10}
                """.formatted(today.plusDays(1), today.plusDays(7), fee).replace("\n", "");
        String res = mvc.perform(post("/api/challenges").header("Authorization", "Bearer " + other)
                        .contentType(MediaType.APPLICATION_JSON).content(body))
                .andExpect(status().isCreated()).andReturn().getResponse().getContentAsString();
        Matcher m = ID.matcher(res);
        assertThat(m.find()).isTrue();
        return Long.parseLong(m.group(1));
    }

    private void join(String token, long id) throws Exception {
        mvc.perform(post("/api/challenges/" + id + "/participants").header("Authorization", "Bearer " + token))
                .andExpect(status().isOk());
    }

    private static String key() {
        return UUID.randomUUID().toString();
    }

    private User newUser() {
        String suffix = UUID.randomUUID().toString().replace("-", "").substring(0, 12);
        return userRepository.saveAndFlush(User.createWithPassword(suffix + "@example.com", "unused", "p" + suffix,
                "p".repeat(52) + suffix));
    }

    private String tokenOf(User user) {
        return jwtProvider.createAccessToken(user.getId(), "USER");
    }
}
