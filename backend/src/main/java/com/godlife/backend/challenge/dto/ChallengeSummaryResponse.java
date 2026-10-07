package com.godlife.backend.challenge.dto;

import com.godlife.backend.challenge.Challenge;
import com.godlife.backend.challenge.ChallengeMode;
import com.godlife.backend.challenge.FrequencyType;

import java.time.LocalDate;

/**
 * 챌린지 목록 카드. 설명은 카드에서 두 줄까지만 보여 준다(자르기는 프론트).
 *
 * @param subType 카테고리 세부 종류 ('기타'에서 고른 경우만, 없으면 null)
 */
public record ChallengeSummaryResponse(Long id, String title, String description, CategoryResponse category,
                                       SubTypeResponse subType, ChallengeMode mode, long entryFee, LocalDate startDate, LocalDate endDate,
                                       long totalDays, FrequencyType frequencyType, Integer weeklyCount,
                                       int participantCount, int maxParticipants) {

    public static ChallengeSummaryResponse from(Challenge c) {
        return new ChallengeSummaryResponse(c.getId(), c.getTitle(), c.getDescription(),
                CategoryResponse.from(c.getCategory()), SubTypeResponse.from(c.getSubType()), c.getMode(),
                c.getEntryFee(), c.getStartDate(),
                c.getEndDate(), c.totalDays(), c.getFrequencyType(), c.getWeeklyCount(), c.getParticipantCount(),
                c.getMaxParticipants());
    }
}
