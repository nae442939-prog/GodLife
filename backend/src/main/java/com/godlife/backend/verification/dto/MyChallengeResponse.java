package com.godlife.backend.verification.dto;

import com.godlife.backend.challenge.Challenge;
import com.godlife.backend.challenge.ChallengeMode;
import com.godlife.backend.challenge.dto.CategoryResponse;
import com.godlife.backend.verification.VerifyState;

import java.time.LocalDate;

/**
 * 내 챌린지 목록 한 줄 (마이페이지 · 챌린지 목록 위 '참여 중인 챌린지').
 * @param joined      참여 중인지 (개설만 하고 참여하지 않았으면 false)
 * @param inProgress  오늘이 시작일~종료일 사이인지
 * @param verifyState 참여 중일 때 지금 인증할 수 있는지 (참여하지 않았으면 null)
 */
public record MyChallengeResponse(Long id, String title, CategoryResponse category, ChallengeMode mode,
                                  LocalDate startDate, LocalDate endDate, long totalDays,
                                  boolean inProgress, boolean joined, boolean host,
                                  int successDays, int targetCount, VerifyState verifyState) {

    public static MyChallengeResponse of(Challenge c, LocalDate today, boolean joined, Long userId,
                                         int successDays, VerifyState verifyState) {
        return new MyChallengeResponse(c.getId(), c.getTitle(), CategoryResponse.from(c.getCategory()), c.getMode(),
                c.getStartDate(), c.getEndDate(), c.totalDays(), c.isInProgress(today), joined, c.isHost(userId),
                successDays, c.targetCount(), verifyState);
    }
}
