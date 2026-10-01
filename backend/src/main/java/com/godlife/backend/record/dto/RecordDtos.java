package com.godlife.backend.record.dto;

import jakarta.validation.constraints.NotNull;
import jakarta.validation.constraints.Pattern;
import jakarta.validation.constraints.Size;

import java.time.LocalDate;
import java.time.LocalDateTime;
import java.util.List;

public final class RecordDtos {

    private RecordDtos() {
    }

    /** 캘린더 위에서 고르는 내 챌린지 (시작한 것만, 끝난 것도 포함) */
    public record ChallengeOption(Long id, String title, int categoryId, LocalDate startDate, LocalDate endDate) {
    }

    /**
     * 캘린더 한 칸.
     * @param total   그날 인증해야 했던 챌린지 수 (오늘 아직 안 한 것도 포함)
     * @param done    인증한 수
     * @param pending 오늘 아직 인증하지 않은 수 (오늘만 0보다 클 수 있다. 실패가 아니라 '진행 중')
     * @param diary   일기를 썼는지
     */
    public record Day(LocalDate date, int total, int done, int pending, boolean diary) {
    }

    /**
     * 요약.
     * @param streak     연속 달성 일수 (해야 할 인증을 모두 한 날이 이어진 수, 챌린지가 없던 날은 건너뛴다)
     * @param monthDone  이 달에 인증한 횟수
     * @param monthTotal 이 달에 인증해야 했던 횟수 (오늘 아직 안 한 것은 빼고)
     * @param monthRate  이 달 성공률(%) — 해야 할 인증이 없었으면 null
     */
    public record Summary(int streak, int monthDone, int monthTotal, Integer monthRate) {
    }

    /** 한 달 캘린더. challengeId 가 있으면 그 챌린지만 본 결과 */
    public record MonthResponse(String month, LocalDate today, Long challengeId, List<ChallengeOption> challenges,
                                List<Day> days, Summary summary) {
    }

    /**
     * 그날 챌린지 하나의 결과.
     * @param result         DONE(인증함) / FAIL(못 함) / PENDING(오늘 아직) / REST(주 N회 챌린지에서 인증하지 않아도 되는 날)
     * @param verificationId 인증 사진 id (DONE 일 때)
     */
    public record DayItem(Long challengeId, String title, int categoryId, String result, Long verificationId) {
    }

    /** 최근 7일 줄의 하루: 그날 일기를 썼는지와 기분 (여러 개 썼으면 가장 나중에 고른 기분) */
    public record WeekDay(LocalDate date, String mood, boolean written) {
    }

    /**
     * 일기 하나.
     * @param mood  기분 — GREAT / GOOD / OKAY / SAD / HARD (고르지 않았으면 null)
     * @param tags  태그한 챌린지 id
     * @param photo 붙인 사진이 있는지
     */
    public record DiaryEntry(Long id, String content, String mood, List<Long> tags, boolean photo,
                             LocalDateTime createdAt) {
    }

    /**
     * 하루 기록: 그날 챌린지 결과 + 그날 쓴 일기들(쓴 순서) + 이 날까지 최근 7일.
     */
    public record DayResponse(LocalDate date, List<DayItem> items, List<DiaryEntry> diaries, List<WeekDay> week) {
    }

    /** 새로 쓴 일기의 id */
    public record DiaryCreated(Long id) {
    }

    public record DiaryRequest(@NotNull(message = "일기 내용을 입력해 주세요.")
                               @Size(max = 500, message = "일기는 500자까지 쓸 수 있어요.") String content,
                               @Pattern(regexp = "GREAT|GOOD|OKAY|SAD|HARD", message = "기분을 다시 골라 주세요.")
                               String mood,
                               @Size(max = 20, message = "챌린지는 20개까지 태그할 수 있어요.") List<Long> challengeIds) {
    }
}
