package com.godlife.backend.challenge.dto;

import com.godlife.backend.challenge.ChallengeMode;
import com.godlife.backend.challenge.FrequencyType;
import jakarta.validation.constraints.AssertTrue;
import jakarta.validation.constraints.Max;
import jakarta.validation.constraints.Min;
import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.NotNull;
import jakarta.validation.constraints.Size;

import java.time.LocalDate;
import java.time.LocalTime;
import java.time.temporal.ChronoUnit;

/**
 * 챌린지 개설. 여러 값을 함께 봐야 하는 규칙은 @AssertTrue 로 검사한다.
 * (오류 필드 이름은 "endDateValid" 처럼 끝에 Valid 가 붙는다. 시작일이 오늘 이후인지는 서버 시각으로 서비스에서 본다)
 *
 * @param entryFee 포인트 챌린지(BET)만. 무료 챌린지는 무시하고 0 으로 저장한다.
 */
public record ChallengeCreateRequest(
        @NotNull(message = "카테고리를 골라 주세요.")
        Integer categoryId,

        @NotBlank(message = "제목을 입력해 주세요.")
        @Size(max = MAX_TITLE, message = "제목은 50자 이하로 입력해 주세요.")
        String title,

        @NotBlank(message = "설명을 입력해 주세요.")
        @Size(max = MAX_DESCRIPTION, message = "설명은 1000자 이하로 입력해 주세요.")
        String description,

        @NotNull(message = "무료 챌린지와 포인트 챌린지 중 하나를 골라 주세요.")
        ChallengeMode mode,

        @NotNull(message = "시작일을 골라 주세요.")
        LocalDate startDate,

        @NotNull(message = "종료일을 골라 주세요.")
        LocalDate endDate,

        @NotNull(message = "인증 주기를 골라 주세요.")
        FrequencyType frequencyType,

        Integer weeklyCount,

        Long entryFee,

        @NotNull(message = "최대 인원을 입력해 주세요.")
        @Min(value = MIN_PARTICIPANTS, message = "최대 인원은 2명 이상이어야 합니다.")
        @Max(value = MAX_PARTICIPANTS, message = "최대 인원은 100명 이하여야 합니다.")
        Integer maxParticipants,

        LocalTime verifyFrom,

        LocalTime verifyUntil,

        boolean partialRefund) {

    public static final int MAX_TITLE = 50;
    public static final int MAX_DESCRIPTION = 1000;
    public static final int MIN_PARTICIPANTS = 2;
    public static final int MAX_PARTICIPANTS = 100;
    public static final int MAX_DAYS = 90;
    public static final long MIN_ENTRY_FEE = 100;
    public static final long MAX_ENTRY_FEE = 100_000;
    public static final long ENTRY_FEE_UNIT = 100;

    @AssertTrue(message = "기간은 1일 이상 90일 이하로 정해 주세요.")
    public boolean isEndDateValid() {
        if (startDate == null || endDate == null) {
            return true;
        }
        long days = ChronoUnit.DAYS.between(startDate, endDate) + 1;
        return days >= 1 && days <= MAX_DAYS;
    }

    @AssertTrue(message = "주 1회 ~ 6회 중에서 골라 주세요. (주 7회는 '매일'을 고르세요)")
    public boolean isWeeklyCountValid() {
        return frequencyType != FrequencyType.WEEKLY_N || (weeklyCount != null && weeklyCount >= 1 && weeklyCount <= 6);
    }

    @AssertTrue(message = "참가 포인트는 100P 단위로 100P ~ 100,000P 사이여야 합니다.")
    public boolean isEntryFeeValid() {
        if (mode != ChallengeMode.BET) {
            return true;
        }
        return entryFee != null && entryFee >= MIN_ENTRY_FEE && entryFee <= MAX_ENTRY_FEE
                && entryFee % ENTRY_FEE_UNIT == 0;
    }

    @AssertTrue(message = "인증 시간대는 시작과 끝을 모두 정하고, 시작이 끝보다 빨라야 합니다.")
    public boolean isVerifyWindowValid() {
        if (verifyFrom == null && verifyUntil == null) {
            return true;
        }
        return verifyFrom != null && verifyUntil != null && verifyFrom.isBefore(verifyUntil);
    }
}
