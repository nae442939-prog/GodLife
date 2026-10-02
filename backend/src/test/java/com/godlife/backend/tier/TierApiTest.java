package com.godlife.backend.tier;

import com.godlife.backend.auth.JwtProvider;
import com.godlife.backend.payment.TestCharger;
import com.godlife.backend.user.User;
import com.godlife.backend.user.UserRepository;
import jakarta.persistence.EntityManager;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.boot.webmvc.test.autoconfigure.AutoConfigureMockMvc;
import org.springframework.http.MediaType;
import org.springframework.jdbc.core.JdbcTemplate;
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
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.post;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.jsonPath;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

/** 칭호(티어): 점수 계산 · 승급 · 강등 · 칭호별 베팅 한도 · 고액 챌린지 */
@SpringBootTest
@AutoConfigureMockMvc
@ActiveProfiles("test")
@Transactional
class TierApiTest {

    private static final Pattern ID = Pattern.compile("\"id\":(\\d+)");

    @Autowired MockMvc mvc;
    @Autowired UserRepository userRepository;
    @Autowired JwtProvider jwtProvider;
    @Autowired TierService tierService;
    @Autowired TestCharger testCharger;
    @Autowired JdbcTemplate jdbc;
    @Autowired EntityManager entityManager;
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
    @DisplayName("인증 성공 10점 · 완주 50점으로 점수가 쌓이고, 기준 점수를 넘으면 승급하고 알림이 온다")
    void promoteByScore() throws Exception {
        tier(me).andExpect(jsonPath("$.tier.name").value("BRONZE"))
                .andExpect(jsonPath("$.score").value(0))
                .andExpect(jsonPath("$.next.name").value("SILVER"))
                .andExpect(jsonPath("$.tiers.length()").value(5));

        long participant = joinFree();
        approved(participant, 9);
        tierService.refresh(meUser.getId());
        tier(me).andExpect(jsonPath("$.score").value(90)).andExpect(jsonPath("$.tier.name").value("BRONZE"));
        assertThat(tierNotifications()).isZero();

        // 한 번 더 인증해 100점 → 실버
        approved(participant, 1);
        tierService.refresh(meUser.getId());
        tier(me).andExpect(jsonPath("$.score").value(100))
                .andExpect(jsonPath("$.tier.name").value("SILVER"))
                .andExpect(jsonPath("$.record.verified").value(10))
                .andExpect(jsonPath("$.next.name").value("GOLD"))
                .andExpect(jsonPath("$.next.minScore").value(300));
        assertThat(storedTier()).isEqualTo(2);
        assertThat(tierNotifications()).isEqualTo(1);

        // 완주하면 +50
        jdbc.update("UPDATE challenge_participants SET status = 'COMPLETED' WHERE id = ?", participant);
        tierService.refresh(meUser.getId());
        tier(me).andExpect(jsonPath("$.score").value(150)).andExpect(jsonPath("$.record.completed").value(1));
    }

    @Test
    @DisplayName("챌린지를 실패하면 30점, 포기하면 50점이 깎이고, 기준 점수 아래로 내려가면 강등된다")
    void demoteByFailure() throws Exception {
        long first = joinFree();
        approved(first, 11); // 110점 → 실버
        tierService.refresh(meUser.getId());
        assertThat(storedTier()).isEqualTo(2);

        // 실패 -30 → 80점 → 브론즈로 강등
        jdbc.update("UPDATE challenge_participants SET status = 'FAILED' WHERE id = ?", first);
        tierService.refresh(meUser.getId());
        tier(me).andExpect(jsonPath("$.score").value(80))
                .andExpect(jsonPath("$.tier.name").value("BRONZE"))
                .andExpect(jsonPath("$.record.failed").value(1));
        assertThat(storedTier()).isEqualTo(1);
        assertThat(tierNotifications()).isEqualTo(2);

        // 포기 -50 → 30점. 점수는 0 아래로 내려가지 않는다
        long second = joinFree();
        jdbc.update("UPDATE challenge_participants SET status = 'GAVE_UP' WHERE id = ?", second);
        tierService.refresh(meUser.getId());
        tier(me).andExpect(jsonPath("$.score").value(30)).andExpect(jsonPath("$.record.gaveUp").value(1));
        jdbc.update("UPDATE verifications SET status = 'REJECTED' WHERE participant_id = ?", first);
        tierService.refresh(meUser.getId());
        tier(me).andExpect(jsonPath("$.score").value(0));
    }

    @Test
    @DisplayName("자정 진행 관리가 모든 회원의 칭호를 점수에 맞춘다 (기능이 생기기 전 기록도 반영)")
    void refreshAllAppliesExistingRecords() throws Exception {
        approved(joinFree(), 30); // 300점 → 골드
        assertThat(storedTier()).isEqualTo(1);

        assertThat(tierService.refreshAll()).isGreaterThanOrEqualTo(1);

        assertThat(storedTier()).isEqualTo(3);
        tier(me).andExpect(jsonPath("$.tier.name").value("GOLD"));
    }

    @Test
    @DisplayName("베팅 한도는 칭호에 따라 달라지고, 참가 포인트 30,000P 이상 챌린지는 플래티넘부터 참여할 수 있다")
    void limitsAndHighStakeByTier() throws Exception {
        // 가입 30일이 지난 회원으로 만든다 (그 전에는 칭호와 상관없이 낮은 한도)
        jdbc.update("UPDATE users SET created_at = DATE_SUB(NOW(), INTERVAL 60 DAY) WHERE id = ?", meUser.getId());
        entityManager.clear();
        wallet(me).andExpect(jsonPath("$.newbie").value(false))
                .andExpect(jsonPath("$.tier").value("BRONZE"))
                .andExpect(jsonPath("$.betDailyLimit").value(30000))
                .andExpect(jsonPath("$.betMonthlyLimit").value(200000));

        testCharger.charge(meUser.getId(), 30_000);
        long highStake = betChallenge(30000);
        join(me, highStake).andExpect(status().isForbidden())
                .andExpect(jsonPath("$.code").value("HIGH_STAKE_TIER_REQUIRED"));
        wallet(me).andExpect(jsonPath("$.chargedBalance").value(30000));

        jdbc.update("UPDATE users SET tier_id = 4 WHERE id = ?", meUser.getId());
        entityManager.clear();
        wallet(me).andExpect(jsonPath("$.tier").value("PLATINUM"))
                .andExpect(jsonPath("$.betDailyLimit").value(70000))
                .andExpect(jsonPath("$.betMonthlyLimit").value(600000));
        join(me, highStake).andExpect(status().isOk());
        wallet(me).andExpect(jsonPath("$.chargedBalance").value(0)).andExpect(jsonPath("$.betToday").value(30000));
    }

    @Test
    @DisplayName("관리자는 칭호 대상이 아니다: '관리자'로 표시되고, 승급 · 강등 없이 가장 높은 칭호의 혜택을 받는다")
    void adminIsOutsideTiers() throws Exception {
        approved(joinFree(), 12); // 보통 회원이면 120점 → 실버
        jdbc.update("UPDATE users SET role = 'ADMIN' WHERE id = ?", meUser.getId());
        entityManager.clear();
        String admin = jwtProvider.createAccessToken(meUser.getId(), "ADMIN");

        tierService.refresh(meUser.getId());
        tierService.refreshAll();

        assertThat(storedTier()).isEqualTo(1);
        assertThat(tierNotifications()).isZero();
        tier(admin).andExpect(jsonPath("$.admin").value(true))
                .andExpect(jsonPath("$.tier.name").value("ADMIN"))
                .andExpect(jsonPath("$.score").value(0))
                .andExpect(jsonPath("$.next").doesNotExist());
        mvc.perform(get("/api/users/" + meUser.getId() + "/profile"))
                .andExpect(jsonPath("$.tier").value("ADMIN"));
        // 가입 30일 이내여도 신규 회원 한도가 아니라 가장 높은 칭호의 한도, 고액 챌린지도 참여할 수 있다
        wallet(admin).andExpect(jsonPath("$.newbie").value(false))
                .andExpect(jsonPath("$.tier").value("ADMIN"))
                .andExpect(jsonPath("$.betDailyLimit").value(100000))
                .andExpect(jsonPath("$.betMonthlyLimit").value(1000000));
        testCharger.charge(meUser.getId(), 30_000);
        join(admin, betChallenge(30000)).andExpect(status().isOk());
    }

    // ---------- helpers ----------

    /** 방장이 연 무료 챌린지에 참여하고 내 참가 기록 id 를 돌려준다 */
    private long joinFree() throws Exception {
        long id = challenge("""
                {"categoryId":1,"title":"칭호 테스트","description":"매일 인증","mode":"FREE",
                 "startDate":"%s","endDate":"%s","frequencyType":"DAILY","maxParticipants":10}
                """.formatted(today.plusDays(1), today.plusDays(7)));
        join(me, id).andExpect(status().isOk());
        entityManager.flush();
        Long participant = jdbc.queryForObject(
                "SELECT id FROM challenge_participants WHERE challenge_id = ? AND user_id = ?", Long.class, id,
                meUser.getId());
        assertThat(participant).isNotNull();
        return participant;
    }

    private long betChallenge(long fee) throws Exception {
        return challenge("""
                {"categoryId":1,"title":"포인트 걸기","description":"매일 인증","mode":"BET",
                 "startDate":"%s","endDate":"%s","frequencyType":"DAILY","entryFee":%d,"maxParticipants":10}
                """.formatted(today.plusDays(1), today.plusDays(7), fee));
    }

    private long challenge(String body) throws Exception {
        String res = mvc.perform(post("/api/challenges").header("Authorization", "Bearer " + host)
                        .contentType(MediaType.APPLICATION_JSON).content(body.replace("\n", "")))
                .andExpect(status().isCreated()).andReturn().getResponse().getContentAsString();
        Matcher m = ID.matcher(res);
        assertThat(m.find()).isTrue();
        return Long.parseLong(m.group(1));
    }

    /** 승인된 인증을 count 개 넣는다 (날짜는 겹치지 않게 과거로) */
    private void approved(long participantId, int count) {
        Integer existing = jdbc.queryForObject("SELECT COUNT(*) FROM verifications WHERE participant_id = ?",
                Integer.class, participantId);
        for (int i = 0; i < count; i++) {
            int offset = (existing == null ? 0 : existing) + i + 1;
            jdbc.update("""
                    INSERT INTO verifications (participant_id, verify_date, received_at, image_url, image_hash, status)
                    VALUES (?, ?, NOW(), 'test.jpg', ?, 'APPROVED')
                    """, participantId, today.minusDays(offset), UUID.randomUUID().toString().replace("-", "")
                    + UUID.randomUUID().toString().replace("-", ""));
        }
    }

    private int storedTier() {
        entityManager.flush();
        Integer tier = jdbc.queryForObject("SELECT tier_id FROM users WHERE id = ?", Integer.class, meUser.getId());
        return tier == null ? 0 : tier;
    }

    private int tierNotifications() {
        Integer n = jdbc.queryForObject("SELECT COUNT(*) FROM notifications WHERE user_id = ? AND type = 'TIER'",
                Integer.class, meUser.getId());
        return n == null ? 0 : n;
    }

    private ResultActions tier(String token) throws Exception {
        return mvc.perform(get("/api/users/me/tier").header("Authorization", "Bearer " + token))
                .andExpect(status().isOk());
    }

    private ResultActions wallet(String token) throws Exception {
        return mvc.perform(get("/api/wallet").header("Authorization", "Bearer " + token)).andExpect(status().isOk());
    }

    private ResultActions join(String token, long id) throws Exception {
        return mvc.perform(post("/api/challenges/" + id + "/participants").header("Authorization", "Bearer " + token));
    }

    private User newUser() {
        String suffix = UUID.randomUUID().toString().replace("-", "").substring(0, 12);
        return userRepository.saveAndFlush(User.createWithPassword(suffix + "@example.com", "unused", "t" + suffix,
                "t".repeat(52) + suffix));
    }

    private String tokenOf(User user) {
        return jwtProvider.createAccessToken(user.getId(), "USER");
    }
}
