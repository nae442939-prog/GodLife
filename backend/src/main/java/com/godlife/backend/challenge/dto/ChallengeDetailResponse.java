package com.godlife.backend.challenge.dto;

import com.godlife.backend.challenge.Challenge;
import com.godlife.backend.challenge.ChallengeMode;
import com.godlife.backend.challenge.ChallengeStatus;
import com.godlife.backend.challenge.FrequencyType;

import java.time.LocalDate;
import java.time.LocalTime;
import java.util.List;

/**
 * @param recruiting 지금 참여할 수 있는 기간인지 (시작일 당일까지)
 * @param canLeave   지금 참여를 취소할 수 있는 기간인지 (시작일 전날까지)
 * @param joined     로그인한 사람이 참여 중인지. 비로그인이면 false
 * @param host       로그인한 사람이 개설자인지
 */
public record ChallengeDetailResponse(Long id, String title, String description, CategoryResponse category,
                                      ChallengeMode mode, long entryFee, boolean partialRefund,
                                      LocalDate startDate, LocalDate endDate, long totalDays,
                                      FrequencyType frequencyType, Integer weeklyCount,
                                      LocalTime verifyFrom, LocalTime verifyUntil,
                                      int participantCount, int maxParticipants, ChallengeStatus status,
                                      String hostNickname, boolean recruiting, boolean canLeave,
                                      boolean joined, boolean host, List<ParticipantResponse> participants) {

    public static ChallengeDetailResponse of(Challenge c, LocalDate today, String hostNickname, boolean joined,
                                             boolean host, List<ParticipantResponse> participants) {
        return new ChallengeDetailResponse(c.getId(), c.getTitle(), c.getDescription(),
                CategoryResponse.from(c.getCategory()), c.getMode(), c.getEntryFee(), c.isPartialRefund(),
                c.getStartDate(), c.getEndDate(), c.totalDays(), c.getFrequencyType(), c.getWeeklyCount(),
                c.getVerifyFrom(), c.getVerifyUntil(), c.getParticipantCount(), c.getMaxParticipants(),
                c.getStatus(), hostNickname, c.isRecruiting(today), c.canLeave(today), joined, host,
                participants);
    }
}
