package com.godlife.backend.profile.dto;

import com.godlife.backend.challenge.Challenge;
import com.godlife.backend.challenge.ChallengeMode;
import com.godlife.backend.challenge.dto.CategoryResponse;

import java.time.LocalDate;
import java.util.List;

/**
 * 회원 프로필 (누구나 볼 수 있음). 이메일·휴대폰 같은 개인정보는 싣지 않는다.
 * 챌린지는 공개 챌린지 중 참여 중인 것만 (비공개 챌린지는 보이지 않는다).
 * @param successRate 누적 성공률 (끝난 챌린지가 없으면 null)
 * @param mine        내 프로필인지
 */
public record ProfileResponse(Long id, String nickname, String profileImageUrl, String bio, LocalDate joinedAt,
                              long monthVerify, long maxStreak, Double successRate, long completedCount,
                              List<ProfileChallenge> challenges, boolean mine) {

    public record ProfileChallenge(Long id, String title, CategoryResponse category, ChallengeMode mode,
                                   LocalDate startDate, LocalDate endDate, long totalDays, boolean inProgress) {

        public static ProfileChallenge of(Challenge c, LocalDate today) {
            return new ProfileChallenge(c.getId(), c.getTitle(), CategoryResponse.from(c.getCategory()), c.getMode(),
                    c.getStartDate(), c.getEndDate(), c.totalDays(), c.isInProgress(today));
        }
    }
}
