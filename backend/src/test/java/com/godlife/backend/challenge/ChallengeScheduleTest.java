package com.godlife.backend.challenge;

import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;

import java.time.LocalDate;
import java.time.LocalTime;

import static org.assertj.core.api.Assertions.assertThat;

/** 챌린지 날짜 계산 (DB 없이) */
class ChallengeScheduleTest {

    private static final LocalDate WED = LocalDate.of(2026, 10, 7);

    @Test
    @DisplayName("주 N회의 '한 주'는 시작일부터 7일씩 끊는다")
    void weekStartsFromStartDate() {
        Challenge c = weekly(WED, WED.plusDays(20), 3);
        assertThat(c.weekStartOf(WED)).isEqualTo(WED);
        assertThat(c.weekStartOf(WED.plusDays(6))).isEqualTo(WED);
        assertThat(c.weekStartOf(WED.plusDays(7))).isEqualTo(WED.plusDays(7));
    }

    @Test
    @DisplayName("필요 인증 횟수: 매일은 전체 일수, 주 N회는 마지막 짧은 주를 남은 일수만큼만 센다")
    void targetCount() {
        assertThat(daily(WED, WED.plusDays(20)).targetCount()).isEqualTo(21);
        assertThat(weekly(WED, WED.plusDays(20), 3).targetCount()).isEqualTo(9);   // 3주
        assertThat(weekly(WED, WED.plusDays(8), 3).targetCount()).isEqualTo(5);    // 7일 + 2일
        assertThat(weekly(WED, WED.plusDays(9), 3).targetCount()).isEqualTo(6);    // 7일 + 3일
    }

    @Test
    @DisplayName("진행 중 = 시작일부터 종료일까지(둘 다 포함)")
    void inProgress() {
        Challenge c = daily(WED, WED.plusDays(6));
        assertThat(c.isInProgress(WED.minusDays(1))).isFalse();
        assertThat(c.isInProgress(WED)).isTrue();
        assertThat(c.isInProgress(WED.plusDays(6))).isTrue();
        assertThat(c.isInProgress(WED.plusDays(7))).isFalse();
    }

    @Test
    @DisplayName("인증 시간대는 시작·끝 시각을 포함하고, 없으면 하루 종일")
    void verifyWindow() {
        assertThat(daily(WED, WED).isVerifyTimeOpen(LocalTime.of(23, 59, 59))).isTrue();

        Challenge early = Challenge.create(1L, null, "기상", "6시까지", ChallengeMode.FREE, ChallengeVisibility.PUBLIC,
                "ABCDEFGH", WED, WED, FrequencyType.DAILY, null, 0, 10,
                LocalTime.of(5, 0), LocalTime.of(6, 0), false);
        assertThat(early.isVerifyTimeOpen(LocalTime.of(4, 59))).isFalse();
        assertThat(early.isVerifyTimeOpen(LocalTime.of(5, 0))).isTrue();
        assertThat(early.isVerifyTimeOpen(LocalTime.of(6, 0))).isTrue();
        assertThat(early.isVerifyTimeOpen(LocalTime.of(6, 1))).isFalse();
    }

    private static Challenge daily(LocalDate start, LocalDate end) {
        return Challenge.create(1L, null, "t", "d", ChallengeMode.FREE, ChallengeVisibility.PUBLIC, "ABCDEFGH",
                start, end, FrequencyType.DAILY, null, 0, 10, null, null, false);
    }

    private static Challenge weekly(LocalDate start, LocalDate end, int n) {
        return Challenge.create(1L, null, "t", "d", ChallengeMode.FREE, ChallengeVisibility.PUBLIC, "ABCDEFGH",
                start, end, FrequencyType.WEEKLY_N, n, 0, 10, null, null, false);
    }
}
