package com.godlife.backend.wallet;

import com.godlife.backend.auth.JwtProvider;
import com.godlife.backend.user.User;
import com.godlife.backend.user.UserRepository;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.boot.webmvc.test.autoconfigure.AutoConfigureMockMvc;
import org.springframework.http.MediaType;
import org.springframework.test.context.ActiveProfiles;
import org.springframework.test.web.servlet.MockMvc;
import org.springframework.test.web.servlet.ResultActions;
import org.springframework.transaction.annotation.Transactional;

import java.time.Clock;
import java.time.LocalDate;
import java.util.UUID;
import java.util.regex.Matcher;
import java.util.regex.Pattern;

import static org.assertj.core.api.Assertions.assertThat;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.delete;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.post;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.jsonPath;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

/** 포인트 지갑: 테스트 충전 · 포인트 챌린지 참가비 · 환급 · 베팅 한도 */
@SpringBootTest
@AutoConfigureMockMvc
@ActiveProfiles("test")
@Transactional
class WalletApiTest {

    private static final Pattern ID = Pattern.compile("\"id\":(\\d+)");

    @Autowired MockMvc mvc;
    @Autowired UserRepository userRepository;
    @Autowired JwtProvider jwtProvider;
    @Autowired Clock clock;

    private String host;
    private User meUser;
    private String me;
    private LocalDate today;

    @BeforeEach
    void setUp() {
        host = tokenOf(newUser());
        meUser = newUser();
        me = tokenOf(meUser);
        today = LocalDate.now(clock);
    }

    @Test
    @DisplayName("테스트 충전은 충전 포인트로 들어가고, 같은 요청을 두 번 보내도 한 번만 충전된다")
    void testChargeIsIdempotent() throws Exception {
        wallet(me).andExpect(jsonPath("$.chargedBalance").value(0))
                .andExpect(jsonPath("$.rewardBalance").value(0));

        charge(me, 5000, "same-key").andExpect(status().isOk())
                .andExpect(jsonPath("$.chargedBalance").value(5000))
                .andExpect(jsonPath("$.balance").value(5000));
        charge(me, 5000, "same-key").andExpect(status().isOk())
                .andExpect(jsonPath("$.chargedBalance").value(5000));

        wallet(me).andExpect(jsonPath("$.transactions.length()").value(1))
                .andExpect(jsonPath("$.transactions[0].type").value("CHARGE"))
                .andExpect(jsonPath("$.transactions[0].source").value("CHARGED"))
                .andExpect(jsonPath("$.transactions[0].amount").value(5000));
    }

    @Test
    @DisplayName("테스트 충전은 정해진 금액만, 하루 50,000P 까지")
    void testChargeLimits() throws Exception {
        charge(me, 3000, key()).andExpect(status().isBadRequest())
                .andExpect(jsonPath("$.code").value("INVALID_CHARGE_AMOUNT"));
        for (int i = 0; i < 5; i++) {
            charge(me, 10000, key()).andExpect(status().isOk());
        }
        charge(me, 1000, key()).andExpect(status().isConflict())
                .andExpect(jsonPath("$.code").value("CHARGE_LIMIT_EXCEEDED"));
        wallet(me).andExpect(jsonPath("$.chargedBalance").value(50000))
                .andExpect(jsonPath("$.chargedToday").value(50000));
    }

    @Test
    @DisplayName("포인트 챌린지에 참여하면 충전 포인트에서 빠지고, 시작 전에 취소하면 돌려받는다")
    void entryFeeAndRefundOnLeave() throws Exception {
        charge(me, 5000, key());
        long id = betChallenge(3000);

        join(me, id).andExpect(status().isOk()).andExpect(jsonPath("$.joined").value(true));
        wallet(me).andExpect(jsonPath("$.chargedBalance").value(2000))
                .andExpect(jsonPath("$.betToday").value(3000))
                .andExpect(jsonPath("$.transactions[0].type").value("ENTRY_FEE"))
                .andExpect(jsonPath("$.transactions[0].amount").value(-3000));

        mvc.perform(delete("/api/challenges/" + id + "/participants/me").header("Authorization", "Bearer " + me))
                .andExpect(status().isOk());
        wallet(me).andExpect(jsonPath("$.chargedBalance").value(5000))
                .andExpect(jsonPath("$.betToday").value(0))
                .andExpect(jsonPath("$.transactions[0].type").value("REFUND"));

        // 다시 참여해도 멱등 키가 겹치지 않고 또 빠진다
        join(me, id).andExpect(status().isOk());
        wallet(me).andExpect(jsonPath("$.chargedBalance").value(2000));
    }

    @Test
    @DisplayName("충전 포인트가 모자라면 참여 자체가 되지 않는다 (참가자도 늘지 않음)")
    void insufficientPoints() throws Exception {
        charge(me, 1000, key());
        long id = betChallenge(3000);

        join(me, id).andExpect(status().isConflict())
                .andExpect(jsonPath("$.code").value("INSUFFICIENT_POINTS"));
        mvc.perform(get("/api/challenges/" + id)).andExpect(jsonPath("$.participantCount").value(0));
        wallet(me).andExpect(jsonPath("$.chargedBalance").value(1000));
    }

    @Test
    @DisplayName("신규 회원(가입 30일 이내)은 하루 10,000P 까지만 걸 수 있다")
    void newbieDailyBetLimit() throws Exception {
        charge(me, 10000, key());
        charge(me, 10000, key());
        join(me, betChallenge(4000)).andExpect(status().isOk());
        join(me, betChallenge(4000)).andExpect(status().isOk());

        join(me, betChallenge(4000)).andExpect(status().isConflict())
                .andExpect(jsonPath("$.code").value("BET_LIMIT_EXCEEDED"));
        wallet(me).andExpect(jsonPath("$.newbie").value(true))
                .andExpect(jsonPath("$.betDailyLimit").value(10000))
                .andExpect(jsonPath("$.chargedBalance").value(12000));
    }

    @Test
    @DisplayName("방장이 내보내거나 챌린지를 지우면 건 포인트를 돌려받는다")
    void refundOnKickAndDelete() throws Exception {
        charge(me, 10000, key());
        long kicked = betChallenge(2000);
        long deleted = betChallenge(3000);
        join(me, kicked);
        join(me, deleted);
        wallet(me).andExpect(jsonPath("$.chargedBalance").value(5000));

        mvc.perform(post("/api/challenges/" + kicked + "/participants/" + meUser.getId() + "/kick")
                .header("Authorization", "Bearer " + host)).andExpect(status().isOk());
        wallet(me).andExpect(jsonPath("$.chargedBalance").value(7000));

        mvc.perform(delete("/api/challenges/" + deleted).header("Authorization", "Bearer " + host))
                .andExpect(status().is2xxSuccessful());
        wallet(me).andExpect(jsonPath("$.chargedBalance").value(10000));
    }

    @Test
    @DisplayName("충전 포인트는 쓰지 않은 만큼만 환불되고, 같은 환불 요청은 한 번만 처리된다")
    void refundUnusedCharged() throws Exception {
        charge(me, 10000, key());
        join(me, betChallenge(3000)); // 3,000P 사용 → 7,000P 남음

        refund(me, 8000, key()).andExpect(status().isConflict())
                .andExpect(jsonPath("$.code").value("REFUND_EXCEEDS_CHARGED"));
        refund(me, 150, key()).andExpect(status().isBadRequest())
                .andExpect(jsonPath("$.code").value("INVALID_REFUND_AMOUNT"));

        refund(me, 7000, "refund-once").andExpect(status().isOk())
                .andExpect(jsonPath("$.chargedBalance").value(0))
                .andExpect(jsonPath("$.transactions[0].type").value("CHARGE_CANCEL"))
                .andExpect(jsonPath("$.transactions[0].amount").value(-7000));
        refund(me, 7000, "refund-once").andExpect(status().isOk())
                .andExpect(jsonPath("$.chargedBalance").value(0));
    }

    // ---------- helpers ----------

    private long betChallenge(long fee) throws Exception {
        String body = """
                {"categoryId":1,"title":"포인트 걸기","description":"매일 인증","mode":"BET",
                 "startDate":"%s","endDate":"%s","frequencyType":"DAILY","entryFee":%d,"maxParticipants":10}
                """.formatted(today.plusDays(1), today.plusDays(7), fee).replace("\n", "");
        String res = mvc.perform(post("/api/challenges").header("Authorization", "Bearer " + host)
                        .contentType(MediaType.APPLICATION_JSON).content(body))
                .andExpect(status().isCreated()).andReturn().getResponse().getContentAsString();
        Matcher m = ID.matcher(res);
        assertThat(m.find()).isTrue();
        return Long.parseLong(m.group(1));
    }

    private ResultActions join(String token, long id) throws Exception {
        return mvc.perform(post("/api/challenges/" + id + "/participants").header("Authorization", "Bearer " + token));
    }

    private ResultActions wallet(String token) throws Exception {
        return mvc.perform(get("/api/wallet").header("Authorization", "Bearer " + token)).andExpect(status().isOk());
    }

    private ResultActions charge(String token, long amount, String requestKey) throws Exception {
        return mvc.perform(post("/api/wallet/test-charge").header("Authorization", "Bearer " + token)
                .contentType(MediaType.APPLICATION_JSON)
                .content("{\"amount\":%d,\"requestKey\":\"%s\"}".formatted(amount, requestKey)));
    }

    private ResultActions refund(String token, long amount, String requestKey) throws Exception {
        return mvc.perform(post("/api/wallet/refund").header("Authorization", "Bearer " + token)
                .contentType(MediaType.APPLICATION_JSON)
                .content("{\"amount\":%d,\"requestKey\":\"%s\"}".formatted(amount, requestKey)));
    }

    private static String key() {
        return UUID.randomUUID().toString();
    }

    private User newUser() {
        String suffix = UUID.randomUUID().toString().replace("-", "").substring(0, 12);
        return userRepository.saveAndFlush(User.createWithPassword(suffix + "@example.com", "unused", "w" + suffix,
                "w".repeat(52) + suffix));
    }

    private String tokenOf(User user) {
        return jwtProvider.createAccessToken(user.getId(), "USER");
    }
}
