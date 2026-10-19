package com.godlife.backend.payment;

import com.godlife.backend.auth.JwtProvider;
import com.godlife.backend.common.error.ErrorCode;
import com.godlife.backend.user.User;
import com.godlife.backend.user.UserRepository;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.boot.webmvc.test.autoconfigure.AutoConfigureMockMvc;
import org.springframework.http.MediaType;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.test.context.ActiveProfiles;
import org.springframework.test.context.bean.override.mockito.MockitoBean;
import org.springframework.test.web.servlet.MockMvc;
import org.springframework.test.web.servlet.ResultActions;
import org.springframework.transaction.annotation.Transactional;

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
 * 카카오페이 충전 · 환불. 카카오페이는 실제로 부르지 않고 가짜로 바꿔 끼운다.
 * 준비(tid 를 주문에 적음) → 승인(pg_token) → 충전, 그리고 결제 취소로 환불되는지 본다.
 */
@SpringBootTest
@AutoConfigureMockMvc
@ActiveProfiles("test")
@Transactional
class KakaoPayApiTest {

    private static final Pattern ORDER_ID = Pattern.compile("\"orderId\":\"([^\"]+)\"");

    @Autowired MockMvc mvc;
    @Autowired UserRepository userRepository;
    @Autowired JwtProvider jwtProvider;
    @Autowired JdbcTemplate jdbc;

    @MockitoBean KakaoPayClient kakaoPay;
    @MockitoBean TossClient toss;

    private User meUser;
    private String me;
    private String other;
    private String tid;

    @BeforeEach
    void setUp() {
        meUser = newUser();
        me = tokenOf(meUser);
        other = tokenOf(newUser());
        tid = "T" + UUID.randomUUID().toString().replace("-", "").substring(0, 19);
        when(kakaoPay.configured()).thenReturn(true);
        when(toss.configured()).thenReturn(true);
        when(kakaoPay.ready(anyString(), anyLong(), anyString(), anyLong(), anyString(), anyString(), anyString()))
                .thenReturn(new KakaoPayClient.Ready(tid, "https://pc.kakaopay/" + tid, "https://m.kakaopay/" + tid));
        when(kakaoPay.approve(eq(tid), anyString(), anyLong(), eq("pg-ok"))).thenReturn(5000L);
    }

    @Test
    @DisplayName("설정에 카카오페이가 켜졌는지 오고, 키가 없으면 카카오페이로는 준비할 수 없다")
    void config() throws Exception {
        mvc.perform(get("/api/payments/config").header("Authorization", "Bearer " + me))
                .andExpect(jsonPath("$.kakaoPayEnabled").value(true))
                .andExpect(jsonPath("$.tossEnabled").value(true))
                .andExpect(jsonPath("$.enabled").value(true));

        when(kakaoPay.configured()).thenReturn(false);
        ready(me, 5000).andExpect(status().isServiceUnavailable())
                .andExpect(jsonPath("$.code").value("PAYMENT_NOT_CONFIGURED"));
    }

    @Test
    @DisplayName("준비하면 결제창 주소가 오고, 승인하면 충전 포인트로 들어간다. 같은 승인을 다시 보내도 한 번만 충전된다")
    void chargeOnce() throws Exception {
        String orderId = orderIdOf(ready(me, 5000).andExpect(status().isOk())
                .andExpect(jsonPath("$.provider").value("KAKAOPAY"))
                .andExpect(jsonPath("$.redirectUrl").value("https://pc.kakaopay/" + tid))
                .andExpect(jsonPath("$.mobileRedirectUrl").value("https://m.kakaopay/" + tid)));
        // 승인 주소는 경로에 주문 번호 · 금액을 싣는다 (카카오페이가 뒤에 ?pg_token= 을 붙인다)
        verify(kakaoPay).ready(eq(orderId), anyLong(), anyString(), eq(5000L),
                eq("http://localhost:5173/wallet/charge/kakao/" + orderId + "/5000"), anyString(), anyString());

        approve(me, orderId, "pg-ok").andExpect(status().isOk())
                .andExpect(jsonPath("$.chargedBalance").value(5000))
                .andExpect(jsonPath("$.refundable").value(5000));
        approve(me, orderId, "pg-ok").andExpect(status().isOk())
                .andExpect(jsonPath("$.chargedBalance").value(5000));
        verify(kakaoPay, times(1)).approve(eq(tid), eq(orderId), anyLong(), eq("pg-ok"));
    }

    @Test
    @DisplayName("남의 주문 · 토스 주문은 카카오페이로 승인할 수 없고, 카카오페이 주문을 토스 승인으로 넘길 수도 없다")
    void wrongOrderRejected() throws Exception {
        String orderId = orderIdOf(ready(me, 5000));
        approve(other, orderId, "pg-ok").andExpect(status().isNotFound());
        mvc.perform(post("/api/payments/toss/confirm").header("Authorization", "Bearer " + me)
                        .contentType(MediaType.APPLICATION_JSON)
                        .content("{\"paymentKey\":\"%s\",\"orderId\":\"%s\",\"amount\":5000}".formatted(tid, orderId)))
                .andExpect(status().isNotFound());

        String tossOrder = orderIdOf(mvc.perform(post("/api/payments/ready").header("Authorization", "Bearer " + me)
                .contentType(MediaType.APPLICATION_JSON).content("{\"amount\":1000}")));
        approve(me, tossOrder, "pg-ok").andExpect(status().isNotFound());

        verify(kakaoPay, never()).approve(anyString(), anyString(), anyLong(), anyString());
        verify(toss, never()).confirm(anyString(), anyString(), anyLong());
    }

    @Test
    @DisplayName("카카오페이가 준비 · 승인을 거절하면 충전되지 않고 주문이 닫힌다")
    void pgRejects() throws Exception {
        when(kakaoPay.ready(anyString(), anyLong(), anyString(), eq(1000L), anyString(), anyString(), anyString()))
                .thenThrow(new PgException(ErrorCode.PAYMENT_FAILED, "가맹점 정보가 올바르지 않아요."));
        ready(me, 1000).andExpect(status().isBadGateway())
                .andExpect(jsonPath("$.message").value("가맹점 정보가 올바르지 않아요."));
        assertThat(jdbc.queryForList("SELECT status FROM payments WHERE user_id = ? AND amount = 1000",
                String.class, meUser.getId())).containsExactly("FAILED");

        String orderId = orderIdOf(ready(me, 5000));
        when(kakaoPay.approve(eq(tid), eq(orderId), anyLong(), eq("pg-bad")))
                .thenThrow(new PgException(ErrorCode.PAYMENT_FAILED, null));
        approve(me, orderId, "pg-bad").andExpect(status().isBadGateway());
        approve(me, orderId, "pg-ok").andExpect(status().isConflict())
                .andExpect(jsonPath("$.code").value("PAYMENT_ALREADY_CLOSED"));
    }

    @Test
    @DisplayName("환불은 카카오페이 결제 취소로. 카카오페이에서 이미 취소된 몫(응답을 못 받은 재시도)은 다시 취소하지 않는다")
    void refundByKakaoCancel() throws Exception {
        String orderId = orderIdOf(ready(me, 5000));
        approve(me, orderId, "pg-ok").andExpect(status().isOk());

        when(kakaoPay.canceledTotal(tid)).thenReturn(0L);
        refund(me, 2000).andExpect(status().isOk()).andExpect(jsonPath("$.chargedBalance").value(3000));
        verify(kakaoPay, times(1)).cancel(tid, 2000L);

        // 카카오페이에는 이미 3,000 이 취소돼 있다 (우리 기록은 2,000) → 이번 1,000 은 카카오페이를 다시 부르지 않고 맞춘다
        when(kakaoPay.canceledTotal(tid)).thenReturn(3000L);
        refund(me, 1000).andExpect(status().isOk()).andExpect(jsonPath("$.chargedBalance").value(2000));
        verify(kakaoPay, never()).cancel(tid, 1000L);
        verify(toss, never()).cancel(anyString(), anyLong(), anyString(), anyString());
    }

    // ---------- helpers ----------

    private ResultActions ready(String token, long amount) throws Exception {
        return mvc.perform(post("/api/payments/ready").header("Authorization", "Bearer " + token)
                .contentType(MediaType.APPLICATION_JSON)
                .content("{\"amount\":%d,\"provider\":\"KAKAOPAY\"}".formatted(amount)));
    }

    private ResultActions approve(String token, String orderId, String pgToken) throws Exception {
        return mvc.perform(post("/api/payments/kakao/approve").header("Authorization", "Bearer " + token)
                .contentType(MediaType.APPLICATION_JSON)
                .content("{\"orderId\":\"%s\",\"pgToken\":\"%s\"}".formatted(orderId, pgToken)));
    }

    private ResultActions refund(String token, long amount) throws Exception {
        return mvc.perform(post("/api/wallet/refund").header("Authorization", "Bearer " + token)
                .contentType(MediaType.APPLICATION_JSON)
                .content("{\"amount\":%d,\"requestKey\":\"%s\"}".formatted(amount, UUID.randomUUID())));
    }

    private static String orderIdOf(ResultActions result) throws Exception {
        Matcher m = ORDER_ID.matcher(result.andReturn().getResponse().getContentAsString());
        assertThat(m.find()).isTrue();
        return m.group(1);
    }

    private User newUser() {
        String suffix = UUID.randomUUID().toString().replace("-", "").substring(0, 12);
        return userRepository.saveAndFlush(User.createWithPassword(suffix + "@example.com", "unused", "k" + suffix,
                "k".repeat(52) + suffix));
    }

    private String tokenOf(User user) {
        return jwtProvider.createAccessToken(user.getId(), "USER");
    }
}
