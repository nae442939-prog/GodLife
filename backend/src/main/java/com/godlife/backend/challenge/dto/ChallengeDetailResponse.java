package com.godlife.backend.challenge.dto;

import com.godlife.backend.challenge.Challenge;
import com.godlife.backend.challenge.ChallengeMode;
import com.godlife.backend.challenge.ChallengeStatus;
import com.godlife.backend.challenge.ChallengeVisibility;
import com.godlife.backend.challenge.FrequencyType;
import com.godlife.backend.challenge.ParticipantStatus;

import java.time.LocalDate;
import java.time.LocalDateTime;
import java.time.LocalTime;
import java.util.List;

/**
 * @param recruiting 지금 참여할 수 있는 기간인지 (시작일 당일까지)
 * @param canLeave   지금 참여를 취소할 수 있는 기간인지 (시작일 전날까지)
 * @param joined     로그인한 사람이 참여 중인지(ACTIVE). 비로그인이면 false
 * @param myStatus   로그인한 사람의 참가 상태 (참여한 적 없으면 null). 성공·실패·포기 표시에 쓴다
 * @param host       로그인한 사람이 개설자인지
 * @param inviteCode 개설자·참가자에게만 준다(초대 링크 복사용). 그 외에는 null
 * @param member     개설자이거나 참여 중인 사람 (오픈채팅을 볼 수 있음)
 */
public record ChallengeDetailResponse(Long id, String title, String description, String notice,
                                      LocalDateTime noticeUpdatedAt, CategoryResponse category,
                                      ChallengeMode mode, ChallengeVisibility visibility, String inviteCode,
                                      long entryFee, boolean partialRefund,
                                      LocalDate startDate, LocalDate endDate, long totalDays,
                                      FrequencyType frequencyType, Integer weeklyCount,
                                      LocalTime verifyFrom, LocalTime verifyUntil,
                                      int participantCount, int maxParticipants, ChallengeStatus status,
                                      String hostNickname, boolean recruiting, boolean canLeave,
                                      boolean joined, ParticipantStatus myStatus, boolean host, boolean member,
                                      List<ParticipantResponse> participants) {

    public static ChallengeDetailResponse of(Challenge c, LocalDate today, String hostNickname, boolean joined,
                                             ParticipantStatus myStatus, boolean host, boolean member,
                                             List<ParticipantResponse> participants) {
        return new ChallengeDetailResponse(c.getId(), c.getTitle(), c.getDescription(), c.getNotice(),
                c.getNoticeUpdatedAt(),
                CategoryResponse.from(c.getCategory()), c.getMode(), c.getVisibility(),
                member ? c.getInviteCode() : null, c.getEntryFee(), c.isPartialRefund(),
                c.getStartDate(), c.getEndDate(), c.totalDays(), c.getFrequencyType(), c.getWeeklyCount(),
                c.getVerifyFrom(), c.getVerifyUntil(), c.getParticipantCount(), c.getMaxParticipants(),
                c.getStatus(), hostNickname, c.isRecruiting(today), c.canLeave(today), joined, myStatus, host, member,
                participants);
    }
}
