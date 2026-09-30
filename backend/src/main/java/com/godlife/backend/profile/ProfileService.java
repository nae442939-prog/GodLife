package com.godlife.backend.profile;

import com.godlife.backend.challenge.ChallengeParticipantRepository;
import com.godlife.backend.challenge.ChallengeRepository;
import com.godlife.backend.challenge.ParticipantStatus;
import com.godlife.backend.common.error.BusinessException;
import com.godlife.backend.common.error.ErrorCode;
import com.godlife.backend.follow.FollowRepository;
import com.godlife.backend.profile.dto.ProfileResponse;
import com.godlife.backend.profile.dto.ProfileResponse.ProfileChallenge;
import com.godlife.backend.ranking.RankingMetric;
import com.godlife.backend.ranking.RankingService;
import com.godlife.backend.user.User;
import com.godlife.backend.user.UserRepository;
import com.godlife.backend.user.UserStatus;
import lombok.RequiredArgsConstructor;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.time.Clock;
import java.time.LocalDate;

/** 회원 프로필: 기본 정보 + 기록 + 참여 중인 공개 챌린지. 정지·탈퇴 회원은 없는 것처럼(404). */
@Service
@RequiredArgsConstructor
public class ProfileService {

    private final UserRepository userRepository;
    private final ChallengeRepository challengeRepository;
    private final ChallengeParticipantRepository participantRepository;
    private final RankingService rankingService;
    private final FollowRepository followRepository;
    private final Clock clock;

    @Transactional(readOnly = true)
    public ProfileResponse profile(Long userId, Long viewerId) {
        User user = userRepository.findById(userId)
                .filter(u -> u.getStatus() == UserStatus.ACTIVE)
                .orElseThrow(() -> new BusinessException(ErrorCode.USER_NOT_FOUND));
        LocalDate today = LocalDate.now(clock);
        return new ProfileResponse(user.getId(), user.getNickname(), user.getProfileImageUrl(), user.getBio(),
                user.getCreatedAt() == null ? null : user.getCreatedAt().toLocalDate(),
                (long) rankingService.valueOf(RankingMetric.MONTH_VERIFY, userId),
                (long) rankingService.valueOf(RankingMetric.MAX_STREAK, userId),
                rankingService.valueOrNull(RankingMetric.SUCCESS_RATE, userId),
                participantRepository.countByUserIdAndStatus(userId, ParticipantStatus.COMPLETED),
                challengeRepository.findPublicJoined(userId).stream().map(c -> ProfileChallenge.of(c, today)).toList(),
                userId.equals(viewerId),
                followRepository.followerCount(userId), followRepository.followingCount(userId),
                viewerId != null && followRepository.exists(viewerId, userId),
                viewerId != null && followRepository.exists(userId, viewerId));
    }
}
