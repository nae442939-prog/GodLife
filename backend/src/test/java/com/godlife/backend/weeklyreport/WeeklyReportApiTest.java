package com.godlife.backend.weeklyreport;

import com.godlife.backend.auth.JwtProvider;
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

/** 주간 회고 리포트: 지난주 리포트 생성(성공률 · 놓친 요일 · 코칭 문구 · 알림), 재실행 안전성, 진행 중인 이번 주, 본인만 */
@SpringBootTest
@AutoConfigureMockMvc
@ActiveProfiles("test")
@Transactional
class WeeklyReportApiTest {

    private static final Pattern ID = Pattern.compile("\"id\":(\\d+)");

    @Autowired MockMvc mvc;
    @Autowired UserRepository userRepository;
    @Autowired JwtProvider jwtProvider;
    @Autowired WeeklyReportService weeklyReportService;
    @Autowired JdbcTemplate jdbc;
    @Autowired EntityManager entityManager;
    @Autowired Clock clock;

    private String host;
    private LocalDate today;
    private LocalDate thisWeek;
    private LocalDate lastWeek;

    @BeforeEach
    void setUp() {
        host = tokenOf(newUser());
        today = LocalDate.now(clock);
        thisWeek = today.minusDays(today.getDayOfWeek().getValue() - 1);
        lastWeek = thisWeek.minusWeeks(1);
    }

    @Test
    @DisplayName("지난주가 끝나면 성공률 · 가장 많이 놓친 요일 · 코칭 문구가 담긴 리포트를 만들고 알림을 보낸다. 다시 돌려도 하나뿐이다")
    void generatesLastWeekReport() throws Exception {
        User user = newUser();
        // 지난주 월~일 매일 챌린지: 월 · 화 · 수 · 목 · 금 인증, 토 · 일은 놓침 → 5/7 = 71%
        long participant = joinedSince(challenge("회고 테스트"), user, lastWeek);
        approvedOn(participant, lastWeek, 5);

        assertThat(weeklyReportService.run(today)).isGreaterThanOrEqualTo(1);

        report(user, lastWeek).andExpect(jsonPath("$.weekStart").value(lastWeek.toString()))
                .andExpect(jsonPath("$.weekEnd").value(lastWeek.plusDays(6).toString()))
                .andExpect(jsonPath("$.inProgress").value(false))
                .andExpect(jsonPath("$.successRate").value(71))
                .andExpect(jsonPath("$.worstWeekday").value(6)) // 토 · 일 한 번씩 → 같으면 앞 요일
                .andExpect(jsonPath("$.stats.done").value(5)).andExpect(jsonPath("$.stats.total").value(7))
                .andExpect(jsonPath("$.stats.weekdays.length()").value(7))
                .andExpect(jsonPath("$.stats.weekdays[0].done").value(1))
                .andExpect(jsonPath("$.stats.weekdays[5].fail").value(1))
                .andExpect(jsonPath("$.stats.challenges[0].title").value("회고 테스트"))
                .andExpect(jsonPath("$.stats.challenges[0].done").value(5))
                .andExpect(jsonPath("$.stats.previousRate").doesNotExist())
                .andExpect(jsonPath("$.coaching").value(org.hamcrest.Matchers.containsString("7번 중 5번")))
                .andExpect(jsonPath("$.coaching").value(org.hamcrest.Matchers.containsString("토 · 일요일")))
                .andExpect(jsonPath("$.weeks[0]").value(thisWeek.toString()))
                .andExpect(jsonPath("$.weeks[1]").value(lastWeek.toString()));
        assertThat(count("weekly_reports", user)).isEqualTo(1);
        assertThat(notifications(user)).isEqualTo(1);

        // 여러 번 돌아도 리포트 · 알림이 늘지 않는다
        weeklyReportService.run(today);
        assertThat(count("weekly_reports", user)).isEqualTo(1);
        assertThat(notifications(user)).isEqualTo(1);
    }

    @Test
    @DisplayName("빠진 지난 주들도 채우되 알림은 바로 지난주 것만 보내고, 그 전 주와 성공률을 비교한다")
    void backfillsAndComparesWithPreviousWeek() throws Exception {
        User user = newUser();
        LocalDate twoWeeksAgo = lastWeek.minusWeeks(1);
        long participant = joinedSince(challenge("두 주 테스트"), user, twoWeeksAgo);
        approvedOn(participant, twoWeeksAgo, 2); // 그 전 주 2/7 = 29%
        approvedOn(participant, lastWeek, 7); // 지난주 7/7 = 100%

        weeklyReportService.run(today);

        assertThat(count("weekly_reports", user)).isEqualTo(2);
        assertThat(notifications(user)).isEqualTo(1);
        report(user, lastWeek).andExpect(jsonPath("$.successRate").value(100))
                .andExpect(jsonPath("$.worstWeekday").doesNotExist())
                .andExpect(jsonPath("$.stats.previousRate").value(29))
                .andExpect(jsonPath("$.coaching").value(org.hamcrest.Matchers.containsString("71%p 올랐어요")));
        // 주 안의 아무 날짜로 물어도 그 주 리포트가 온다
        report(user, twoWeeksAgo.plusDays(3)).andExpect(jsonPath("$.weekStart").value(twoWeeksAgo.toString()))
                .andExpect(jsonPath("$.successRate").value(29));
    }

    @Test
    @DisplayName("이번 주는 저장하지 않고 지금까지의 기록으로 보여 준다. 오늘 아직 안 한 인증은 실패로 세지 않는다")
    void currentWeekIsLive() throws Exception {
        User user = newUser();
        long participant = joinedSince(challenge("이번 주 테스트"), user, thisWeek);
        int past = today.getDayOfWeek().getValue() - 1; // 이번 주에 이미 지나간 날 수

        mvc.perform(get("/api/weekly-reports").header("Authorization", "Bearer " + tokenOf(user)))
                .andExpect(status().isOk()).andExpect(jsonPath("$.weekStart").value(thisWeek.toString()))
                .andExpect(jsonPath("$.inProgress").value(true))
                .andExpect(jsonPath("$.stats.done").value(0)).andExpect(jsonPath("$.stats.total").value(past));

        approvedOn(participant, today, 1);
        mvc.perform(get("/api/weekly-reports").header("Authorization", "Bearer " + tokenOf(user)))
                .andExpect(jsonPath("$.stats.done").value(1)).andExpect(jsonPath("$.stats.total").value(past + 1));
        assertThat(count("weekly_reports", user)).isZero();
    }

    @Test
    @DisplayName("인증할 날이 없던 회원은 리포트를 만들지 않고, 리포트는 본인 것만 · 로그인해야 본다")
    void emptyAndPrivate() throws Exception {
        User user = newUser();
        long participant = joinedSince(challenge("남의 리포트"), user, lastWeek);
        approvedOn(participant, lastWeek, 7);
        User stranger = newUser();

        weeklyReportService.run(today);

        assertThat(count("weekly_reports", stranger)).isZero();
        report(stranger, lastWeek).andExpect(jsonPath("$.successRate").doesNotExist())
                .andExpect(jsonPath("$.stats.total").value(0))
                .andExpect(jsonPath("$.stats.challenges.length()").value(0));
        mvc.perform(get("/api/weekly-reports")).andExpect(status().isUnauthorized());
    }

    // ---------- helpers ----------

    private long challenge(String title) throws Exception {
        String body = """
                {"categoryId":1,"title":"%s","description":"매일 인증","mode":"FREE",
                 "startDate":"%s","endDate":"%s","frequencyType":"DAILY","maxParticipants":20}
                """.formatted(title, today.plusDays(1), today.plusDays(30));
        String res = mvc.perform(post("/api/challenges").header("Authorization", "Bearer " + host)
                        .contentType(MediaType.APPLICATION_JSON).content(body.replace("\n", "")))
                .andExpect(status().isCreated()).andReturn().getResponse().getContentAsString();
        Matcher m = ID.matcher(res);
        assertThat(m.find()).isTrue();
        return Long.parseLong(m.group(1));
    }

    /** 챌린지에 들어간 뒤, 챌린지 시작일과 들어온 날을 start 로 당겨 지난 기록이 세어지게 한다 */
    private long joinedSince(long challengeId, User user, LocalDate start) throws Exception {
        mvc.perform(post("/api/challenges/" + challengeId + "/participants")
                .header("Authorization", "Bearer " + tokenOf(user))).andExpect(status().isOk());
        entityManager.flush();
        jdbc.update("UPDATE challenges SET start_date = ? WHERE id = ?", start, challengeId);
        jdbc.update("UPDATE challenge_participants SET joined_at = ? WHERE challenge_id = ?", start.atStartOfDay(),
                challengeId);
        Long participant = jdbc.queryForObject(
                "SELECT id FROM challenge_participants WHERE challenge_id = ? AND user_id = ?", Long.class,
                challengeId, user.getId());
        assertThat(participant).isNotNull();
        return participant;
    }

    /** start 부터 하루씩 count 개의 승인된 인증을 넣는다 */
    private void approvedOn(long participantId, LocalDate start, int count) {
        for (int i = 0; i < count; i++) {
            jdbc.update("""
                    INSERT INTO verifications (participant_id, verify_date, received_at, image_url, image_hash, status)
                    VALUES (?, ?, NOW(), 'test.jpg', ?, 'APPROVED')
                    """, participantId, start.plusDays(i), UUID.randomUUID().toString().replace("-", "")
                    + UUID.randomUUID().toString().replace("-", ""));
        }
    }

    private ResultActions report(User user, LocalDate week) throws Exception {
        return mvc.perform(get("/api/weekly-reports?week=" + week).header("Authorization", "Bearer " + tokenOf(user)))
                .andExpect(status().isOk());
    }

    private int count(String table, User user) {
        Integer n = jdbc.queryForObject("SELECT COUNT(*) FROM " + table + " WHERE user_id = ?", Integer.class,
                user.getId());
        return n == null ? 0 : n;
    }

    private int notifications(User user) {
        Integer n = jdbc.queryForObject(
                "SELECT COUNT(*) FROM notifications WHERE user_id = ? AND type = 'WEEKLY_REPORT'", Integer.class,
                user.getId());
        return n == null ? 0 : n;
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
