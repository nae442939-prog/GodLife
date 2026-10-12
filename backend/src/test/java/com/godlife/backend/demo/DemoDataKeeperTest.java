package com.godlife.backend.demo;

import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.test.context.ActiveProfiles;
import org.springframework.test.context.TestPropertySource;
import org.springframework.transaction.annotation.Transactional;

import java.time.Clock;
import java.time.LocalDate;
import java.util.List;
import java.util.UUID;

import static org.assertj.core.api.Assertions.assertThat;

/** 시연용 더미 데이터 날짜 맞추기: 더미 회원의 챌린지 · 인증만 밀고 실제 회원의 기록은 그대로 둔다 */
@SpringBootTest
@ActiveProfiles("test")
@TestPropertySource(properties = "app.demo.keep-dates=true")
@Transactional
class DemoDataKeeperTest {

    @Autowired DemoDataKeeper keeper;
    @Autowired JdbcTemplate jdbc;
    @Autowired Clock clock;

    private LocalDate today;
    private long dummy;
    private long real;

    @BeforeEach
    void setUp() {
        today = LocalDate.now(clock);
        dummy = user(UUID.randomUUID() + "@dummy.godlife");
        real = user(UUID.randomUUID() + "@example.com");
    }

    @Test
    @DisplayName("더미 회원의 마지막 인증일이 오늘이 되도록 더미 챌린지 · 인증 날짜를 밀고, 실제 회원의 인증과 챌린지는 그대로 둔다")
    void rollsDummyDatesOnly() {
        long ongoing = challenge(dummy, today.minusDays(5), "ONGOING");
        long mine = challenge(real, today.minusDays(5), "ONGOING");
        long dummyIn = participant(ongoing, dummy);
        long realIn = participant(ongoing, real);
        verify(dummyIn, today.minusDays(4));
        verify(dummyIn, today.minusDays(3));
        verify(realIn, today.minusDays(3));

        assertThat(keeper.roll(today)).isEqualTo(3);

        assertThat(startOf(ongoing)).isEqualTo(today.minusDays(2));
        assertThat(verifyDates(dummyIn)).containsExactly(today.minusDays(1), today);
        assertThat(startOf(mine)).isEqualTo(today.minusDays(5));
        assertThat(verifyDates(realIn)).containsExactly(today.minusDays(3));
        // 이미 맞춰져 있으면 더 밀지 않는다
        assertThat(keeper.roll(today)).isZero();
        assertThat(startOf(ongoing)).isEqualTo(today.minusDays(2));
    }

    @Test
    @DisplayName("꺼 둔 사이 시작일이 지나 진행 중이 된 더미 챌린지는 날짜를 밀면 다시 모집 중이 된다")
    void backToRecruiting() {
        long ongoing = challenge(dummy, today.minusDays(6), "ONGOING");
        long started = challenge(dummy, today.minusDays(1), "ONGOING");
        verify(participant(ongoing, dummy), today.minusDays(3));

        keeper.roll(today);

        assertThat(startOf(started)).isEqualTo(today.plusDays(2));
        assertThat(statusOf(started)).isEqualTo("RECRUITING");
        assertThat(statusOf(ongoing)).isEqualTo("ONGOING");
    }

    @Test
    @DisplayName("더미 회원의 연속 기록은 오늘이나 어제에서 끝나는 연속 인증 일수로 맞추고, 그보다 오래 쉬었으면 0이다")
    void syncsStreaks() {
        long ongoing = challenge(dummy, today.minusDays(6), "ONGOING");
        long other = user(UUID.randomUUID() + "@dummy.godlife");
        long keeping = participant(ongoing, dummy);
        long resting = participant(ongoing, other);
        verify(keeping, today.minusDays(4));
        verify(keeping, today.minusDays(1));
        verify(keeping, today);
        verify(resting, today.minusDays(3));
        jdbc.update("UPDATE challenge_participants SET current_streak = 5, max_streak = 5 WHERE id = ?", resting);

        keeper.syncStreaks(today);

        assertThat(streakOf(keeping)).isEqualTo(2);
        assertThat(streakOf(resting)).isZero();
    }

    private long user(String email) {
        jdbc.update("INSERT INTO users (email, nickname) VALUES (?, ?)", email,
                "t" + UUID.randomUUID().toString().replace("-", "").substring(0, 10));
        return jdbc.queryForObject("SELECT id FROM users WHERE email = ?", Long.class, email);
    }

    private long challenge(long host, LocalDate start, String status) {
        String title = "demo-" + UUID.randomUUID();
        jdbc.update("""
                INSERT INTO challenges (host_id, category_id, title, description, mode, visibility, invite_code, start_date, end_date,
                                        frequency_type, entry_fee, min_bet, max_bet, max_participants, status)
                VALUES (?, 1, ?, '날짜 맞추기 테스트', 'FREE', 'PUBLIC', ?, ?, ?, 'DAILY', 0, 0, 0, 10, ?)
                """, host, title, UUID.randomUUID().toString().replace("-", "").substring(0, 8).toUpperCase(),
                start, start.plusDays(13), status);
        return jdbc.queryForObject("SELECT id FROM challenges WHERE title = ?", Long.class, title);
    }

    private long participant(long challenge, long user) {
        jdbc.update("INSERT INTO challenge_participants (challenge_id, user_id, deposit_amount) VALUES (?, ?, 0)",
                challenge, user);
        return jdbc.queryForObject("SELECT id FROM challenge_participants WHERE challenge_id = ? AND user_id = ?",
                Long.class, challenge, user);
    }

    private void verify(long participant, LocalDate day) {
        jdbc.update("""
                INSERT INTO verifications (participant_id, verify_date, received_at, image_url, image_hash, status)
                VALUES (?, ?, ?, 'verification/test.jpg', SHA2(UUID(), 256), 'APPROVED')
                """, participant, day, day.atTime(9, 0));
    }

    private LocalDate startOf(long challenge) {
        return jdbc.queryForObject("SELECT start_date FROM challenges WHERE id = ?", LocalDate.class, challenge);
    }

    private String statusOf(long challenge) {
        return jdbc.queryForObject("SELECT status FROM challenges WHERE id = ?", String.class, challenge);
    }

    private List<LocalDate> verifyDates(long participant) {
        return jdbc.queryForList("SELECT verify_date FROM verifications WHERE participant_id = ? ORDER BY verify_date",
                LocalDate.class, participant);
    }

    private int streakOf(long participant) {
        return jdbc.queryForObject("SELECT current_streak FROM challenge_participants WHERE id = ?",
                Integer.class, participant);
    }
}
