package com.godlife.backend.season;

import com.godlife.backend.auth.JwtProvider;
import com.godlife.backend.user.User;
import com.godlife.backend.user.UserRepository;
import com.godlife.backend.wallet.WalletService;
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

/** 시즌제 랭킹: 주간 · 월간 시즌 시작, 점수(그 기간 승인된 인증 수) · 공동 순위, 상위 3명 보너스, 재실행 안전성 */
@SpringBootTest
@AutoConfigureMockMvc
@ActiveProfiles("test")
@Transactional
class SeasonApiTest {

    private static final Pattern ID = Pattern.compile("\"id\":(\\d+)");

    @Autowired MockMvc mvc;
    @Autowired UserRepository userRepository;
    @Autowired JwtProvider jwtProvider;
    @Autowired SeasonService seasonService;
    @Autowired WalletService walletService;
    @Autowired JdbcTemplate jdbc;
    @Autowired EntityManager entityManager;
    @Autowired Clock clock;

    private String host;
    private LocalDate today;

    @BeforeEach
    void setUp() {
        host = tokenOf(newUser());
        today = LocalDate.now(clock);
    }

    @Test
    @DisplayName("오늘 기준으로 이번 주(월~일) · 이번 달(1일~말일) 시즌이 없으면 하나씩 만들고, 다시 돌려도 새로 안 만든다")
    void startsCurrentSeasons() throws Exception {
        seasonService.run(today);
        LocalDate monday = today.minusDays(today.getDayOfWeek().getValue() - 1);

        current("weekly").andExpect(jsonPath("$.start").value(monday.toString()))
                .andExpect(jsonPath("$.end").value(monday.plusDays(6).toString()))
                .andExpect(jsonPath("$.status").value("ACTIVE"))
                .andExpect(jsonPath("$.top.length()").value(0));
        current("monthly").andExpect(jsonPath("$.start").value(today.withDayOfMonth(1).toString()))
                .andExpect(jsonPath("$.end").value(today.withDayOfMonth(today.lengthOfMonth()).toString()));

        seasonService.run(today);
        assertThat(countSeasons("WEEKLY", "ACTIVE")).isEqualTo(1);
        assertThat(countSeasons("MONTHLY", "ACTIVE")).isEqualTo(1);
    }

    @Test
    @DisplayName("시즌 랭킹은 비로그인도 볼 수 있다")
    void anonymousCanView() throws Exception {
        seasonService.run(today);
        mvc.perform(get("/api/seasons/current?type=weekly")).andExpect(status().isOk())
                .andExpect(jsonPath("$.me").doesNotExist());
    }

    @Test
    @DisplayName("진행 중인 시즌도 지금까지 쌓인 인증으로 실시간 순위를 보여주지만, 보너스는 예상일 뿐 시즌이 끝나야 지급된다")
    void activeSeasonShowsLiveRankingButDoesNotPayYet() throws Exception {
        seasonService.run(today);
        long challenge = freeChallenge();
        User user = newUser();
        approvedOn(joinAs(challenge, tokenOf(user), user.getId()), today, 3);

        current("weekly", tokenOf(user)).andExpect(jsonPath("$.status").value("ACTIVE"))
                .andExpect(jsonPath("$.bonusConfirmed").value(false))
                .andExpect(jsonPath("$.top[0].rank").value(1)).andExpect(jsonPath("$.top[0].score").value(3))
                .andExpect(jsonPath("$.top[0].bonusPoints").value(5000)) // 지금 순위대로면 받을 예상 보너스
                .andExpect(jsonPath("$.me.rank").value(1));

        // 아직 시즌이 안 끝났으니 실제로는 안 들어온다
        assertThat(walletService.wallet(user.getId()).rewardBalance()).isZero();
        assertThat(seasonNotifications(user.getId())).isZero();
    }

    @Test
    @DisplayName("시즌이 끝나면 그 기간 승인 인증 수로 순위를 매기고(공동 순위 포함) 상위 3명에게 차등 보너스를 주고, 다음 시즌을 새로 시작한다")
    void closesSeasonAndPaysTopThree() throws Exception {
        LocalDate monday = today.minusDays(today.getDayOfWeek().getValue() - 1).minusWeeks(1);
        long seasonId = insertSeason("WEEKLY", monday, monday.plusDays(6));
        long challenge = freeChallenge();

        User firstA = newUser(); // 5회, 공동 1위
        User firstB = newUser(); // 5회, 공동 1위
        User third = newUser(); // 3회 → 공동 1위가 둘이라 3위
        User fourth = newUser(); // 1회 → 4위, 보너스 없음
        approvedOn(joinAs(challenge, tokenOf(firstA), firstA.getId()), monday, 5);
        approvedOn(joinAs(challenge, tokenOf(firstB), firstB.getId()), monday, 5);
        approvedOn(joinAs(challenge, tokenOf(third), third.getId()), monday, 3);
        approvedOn(joinAs(challenge, tokenOf(fourth), fourth.getId()), monday, 1);

        seasonService.run(today);

        assertThat(countSeasons("WEEKLY", "CLOSED")).isEqualTo(1);
        assertThat(rankOf(seasonId, firstA.getId())).isEqualTo(1);
        assertThat(rankOf(seasonId, firstB.getId())).isEqualTo(1);
        assertThat(rankOf(seasonId, third.getId())).isEqualTo(3);
        assertThat(rankOf(seasonId, fourth.getId())).isEqualTo(4);

        assertThat(walletService.wallet(firstA.getId()).rewardBalance()).isEqualTo(5_000);
        assertThat(walletService.wallet(firstB.getId()).rewardBalance()).isEqualTo(5_000);
        assertThat(walletService.wallet(third.getId()).rewardBalance()).isEqualTo(1_000); // '3위' 보너스
        assertThat(walletService.wallet(fourth.getId()).rewardBalance()).isZero();

        assertThat(seasonNotifications(firstA.getId())).isEqualTo(1);
        assertThat(seasonNotifications(fourth.getId())).isZero();

        byId(seasonId, tokenOf(firstA)).andExpect(jsonPath("$.status").value("CLOSED"))
                .andExpect(jsonPath("$.bonusConfirmed").value(true))
                .andExpect(jsonPath("$.top[0].rank").value(1))
                .andExpect(jsonPath("$.top[0].bonusPoints").value(5000))
                .andExpect(jsonPath("$.me.rank").value(1)).andExpect(jsonPath("$.me.mine").value(true));

        // 지난주는 끝났고, 이번 주 시즌이 새로 열려 있다
        current("weekly").andExpect(jsonPath("$.previousSeasonId").value(seasonId))
                .andExpect(jsonPath("$.status").value("ACTIVE"));

        // 여러 번 돌아도 보너스가 두 번 들어가지 않는다
        seasonService.run(today);
        assertThat(walletService.wallet(firstA.getId()).rewardBalance()).isEqualTo(5_000);
        assertThat(seasonNotifications(firstA.getId())).isEqualTo(1);
    }

    // ---------- helpers ----------

    private long freeChallenge() throws Exception {
        return challenge("""
                {"categoryId":1,"title":"시즌 테스트","description":"매일 인증","mode":"FREE",
                 "startDate":"%s","endDate":"%s","frequencyType":"DAILY","maxParticipants":20}
                """.formatted(today.plusDays(1), today.plusDays(30)));
    }

    private long challenge(String body) throws Exception {
        String res = mvc.perform(post("/api/challenges").header("Authorization", "Bearer " + host)
                        .contentType(MediaType.APPLICATION_JSON).content(body.replace("\n", "")))
                .andExpect(status().isCreated()).andReturn().getResponse().getContentAsString();
        Matcher m = ID.matcher(res);
        assertThat(m.find()).isTrue();
        return Long.parseLong(m.group(1));
    }

    private long joinAs(long challengeId, String token, long userId) throws Exception {
        mvc.perform(post("/api/challenges/" + challengeId + "/participants").header("Authorization", "Bearer " + token))
                .andExpect(status().isOk());
        entityManager.flush();
        Long participant = jdbc.queryForObject(
                "SELECT id FROM challenge_participants WHERE challenge_id = ? AND user_id = ?", Long.class,
                challengeId, userId);
        assertThat(participant).isNotNull();
        return participant;
    }

    /** start 부터 하루씩 늘려 가며(겹치면 unique 제약에 걸려서) count 개의 승인된 인증을 넣는다 */
    private void approvedOn(long participantId, LocalDate start, int count) {
        for (int i = 0; i < count; i++) {
            jdbc.update("""
                    INSERT INTO verifications (participant_id, verify_date, received_at, image_url, image_hash, status)
                    VALUES (?, ?, NOW(), 'test.jpg', ?, 'APPROVED')
                    """, participantId, start.plusDays(i), UUID.randomUUID().toString().replace("-", "")
                    + UUID.randomUUID().toString().replace("-", ""));
        }
    }

    private long insertSeason(String type, LocalDate start, LocalDate end) {
        jdbc.update("INSERT INTO seasons (type, start_date, end_date, status, bonus_paid) "
                + "VALUES (?, ?, ?, 'ACTIVE', 0)", type, start, end);
        Long id = jdbc.queryForObject("SELECT id FROM seasons WHERE type = ? AND start_date = ?", Long.class, type,
                start);
        assertThat(id).isNotNull();
        return id;
    }

    private int countSeasons(String type, String status) {
        Integer n = jdbc.queryForObject("SELECT COUNT(*) FROM seasons WHERE type = ? AND status = ?", Integer.class,
                type, status);
        return n == null ? 0 : n;
    }

    private int rankOf(long seasonId, long userId) {
        Integer n = jdbc.queryForObject(
                "SELECT rank_no FROM season_rankings WHERE season_id = ? AND user_id = ?", Integer.class, seasonId,
                userId);
        return n == null ? 0 : n;
    }

    private int seasonNotifications(long userId) {
        Integer n = jdbc.queryForObject("SELECT COUNT(*) FROM notifications WHERE user_id = ? AND type = 'SEASON'",
                Integer.class, userId);
        return n == null ? 0 : n;
    }

    private ResultActions current(String type) throws Exception {
        return current(type, host);
    }

    private ResultActions current(String type, String token) throws Exception {
        return mvc.perform(get("/api/seasons/current?type=" + type).header("Authorization", "Bearer " + token))
                .andExpect(status().isOk());
    }

    private ResultActions byId(long id, String token) throws Exception {
        return mvc.perform(get("/api/seasons/" + id).header("Authorization", "Bearer " + token))
                .andExpect(status().isOk());
    }

    private User newUser() {
        String suffix = UUID.randomUUID().toString().replace("-", "").substring(0, 12);
        return userRepository.saveAndFlush(User.createWithPassword(suffix + "@example.com", "unused", "s" + suffix,
                "s".repeat(52) + suffix));
    }

    private String tokenOf(User user) {
        return jwtProvider.createAccessToken(user.getId(), "USER");
    }
}
