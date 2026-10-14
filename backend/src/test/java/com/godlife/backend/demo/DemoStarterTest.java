package com.godlife.backend.demo;

import com.godlife.backend.auth.JwtProvider;
import com.godlife.backend.user.User;
import com.godlife.backend.user.UserRepository;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.boot.webmvc.test.autoconfigure.AutoConfigureMockMvc;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.test.context.ActiveProfiles;
import org.springframework.test.web.servlet.MockMvc;
import org.springframework.transaction.annotation.Transactional;

import java.time.Clock;
import java.time.LocalDate;
import java.util.UUID;

import static org.assertj.core.api.Assertions.assertThat;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.jsonPath;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

/** 테스트 서버용 체험 데이터: 오늘 시작 무료 챌린지 참여 + 지난 기록이 있는 체험 챌린지, 한 번만 */
@SpringBootTest
@AutoConfigureMockMvc
@ActiveProfiles("test")
@Transactional
class DemoStarterTest {

    @Autowired DemoStarter demoStarter;
    @Autowired UserRepository userRepository;
    @Autowired JdbcTemplate jdbc;
    @Autowired JwtProvider jwtProvider;
    @Autowired MockMvc mvc;
    @Autowired Clock clock;

    @Test
    @DisplayName("오늘 시작 무료 챌린지에 참여하고, 지난 6일 기록(성공 5 · 실패 1)이 있는 체험 챌린지가 생긴다. 두 번은 안 들어간다")
    void seedsOnce() throws Exception {
        LocalDate today = LocalDate.now(clock);
        User host = newUser();
        long todayFree = challenge(host.getId(), today, "FREE", "PUBLIC");
        long todayBet = challenge(host.getId(), today, "BET", "PUBLIC");
        User me = newUser();

        assertThat(demoStarter.seed(me.getId())).isTrue();

        // ① 오늘 시작 무료 챌린지에는 참여, 포인트 챌린지에는 참여하지 않는다
        assertThat(count("SELECT COUNT(*) FROM challenge_participants WHERE user_id = ? AND challenge_id = ?", me.getId(), todayFree))
                .isEqualTo(1);
        assertThat(count("SELECT COUNT(*) FROM challenge_participants WHERE user_id = ? AND challenge_id = ?", me.getId(), todayBet))
                .isZero();

        // ② 체험 챌린지: 본인이 연 비공개 무료 챌린지, 6일 전 시작, 지난 기록 5일
        Long starter = jdbc.queryForObject("SELECT id FROM challenges WHERE host_id = ? AND title = ?", Long.class,
                me.getId(), DemoStarter.TITLE);
        assertThat(jdbc.queryForObject("SELECT CONCAT(mode, '/', visibility, '/', status, '/', start_date) FROM challenges WHERE id = ?",
                String.class, starter)).isEqualTo("FREE/PRIVATE/ONGOING/" + today.minusDays(6));
        assertThat(count("""
                SELECT COUNT(*) FROM verifications v JOIN challenge_participants p ON p.id = v.participant_id
                WHERE p.challenge_id = ? AND p.user_id = ? AND v.status = 'APPROVED'""", starter, me.getId())).isEqualTo(5);

        // 캘린더: 4일 전은 실패, 어제는 성공, 오늘은 '오늘 아직'
        String token = "Bearer " + jwtProvider.createAccessToken(me.getId(), "USER");
        mvc.perform(get("/api/records/days/" + today.minusDays(4)).header("Authorization", token))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.items[?(@.challengeId == " + starter + ")].result").value("FAIL"));
        mvc.perform(get("/api/records/days/" + today.minusDays(1)).header("Authorization", token))
                .andExpect(jsonPath("$.items[?(@.challengeId == " + starter + ")].result").value("DONE"));
        mvc.perform(get("/api/records/days/" + today).header("Authorization", token))
                .andExpect(jsonPath("$.items[?(@.challengeId == " + starter + ")].result").value("PENDING"));

        // 다시 불러도 그대로
        assertThat(demoStarter.seed(me.getId())).isFalse();
        assertThat(count("SELECT COUNT(*) FROM challenges WHERE host_id = ? AND title = ?", me.getId(), DemoStarter.TITLE))
                .isEqualTo(1);
        assertThat(demoStarter.membersWithoutStarter()).doesNotContain(me.getId()).contains(host.getId());
    }

    @Test
    @DisplayName("이미 참여한 챌린지가 있으면 자동 참여는 하지 않고 체험 챌린지만 넣는다")
    void noAutoJoinForActiveMembers() {
        LocalDate today = LocalDate.now(clock);
        User host = newUser();
        long other = challenge(host.getId(), today, "FREE", "PUBLIC");
        long todayFree = challenge(host.getId(), today, "FREE", "PUBLIC");
        User me = newUser();
        jdbc.update("INSERT INTO challenge_participants (challenge_id, user_id, deposit_amount) VALUES (?, ?, 0)", other, me.getId());

        demoStarter.seed(me.getId());

        assertThat(count("SELECT COUNT(*) FROM challenge_participants WHERE user_id = ? AND challenge_id = ?", me.getId(), todayFree))
                .isZero();
        assertThat(count("SELECT COUNT(*) FROM challenges WHERE host_id = ? AND title = ?", me.getId(), DemoStarter.TITLE))
                .isEqualTo(1);
    }

    private long challenge(Long hostId, LocalDate start, String mode, String visibility) {
        String code = UUID.randomUUID().toString().replace("-", "").substring(0, 8).toUpperCase();
        int bet = "BET".equals(mode) ? 1000 : 0;
        jdbc.update("""
                INSERT INTO challenges (host_id, category_id, title, description, mode, visibility, invite_code, start_date, end_date,
                  frequency_type, entry_fee, min_bet, max_bet, max_participants, status)
                VALUES (?, 1, '오늘 시작', '설명', ?, ?, ?, ?, ?, 'DAILY', ?, ?, ?, 1000, 'ONGOING')""",
                hostId, mode, visibility, code, start, start.plusDays(6), bet, bet, bet);
        return jdbc.queryForObject("SELECT id FROM challenges WHERE invite_code = ?", Long.class, code);
    }

    private long count(String sql, Object... args) {
        Long n = jdbc.queryForObject(sql, Long.class, args);
        return n == null ? 0 : n;
    }

    private User newUser() {
        String suffix = UUID.randomUUID().toString().replace("-", "").substring(0, 12);
        return userRepository.saveAndFlush(User.createWithPassword(suffix + "@example.com", "unused", "d" + suffix,
                "d".repeat(52) + suffix));
    }
}
