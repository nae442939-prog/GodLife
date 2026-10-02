package com.godlife.backend.profile;

import com.godlife.backend.block.UserBlockRepository;
import com.godlife.backend.challenge.ChallengeParticipantRepository;
import com.godlife.backend.challenge.ChallengeRepository;
import com.godlife.backend.challenge.ParticipantStatus;
import com.godlife.backend.common.error.BusinessException;
import com.godlife.backend.common.error.ErrorCode;
import com.godlife.backend.follow.FollowRepository;
import com.godlife.backend.profile.dto.ProfileResponse;
import com.godlife.backend.profile.dto.ProfileResponse.Badge;
import com.godlife.backend.profile.dto.ProfileResponse.ProfileChallenge;
import com.godlife.backend.ranking.RankingMetric;
import com.godlife.backend.ranking.RankingService;
import com.godlife.backend.user.User;
import com.godlife.backend.user.UserRepository;
import com.godlife.backend.user.UserStatus;
import lombok.RequiredArgsConstructor;
import org.springframework.jdbc.core.namedparam.MapSqlParameterSource;
import org.springframework.jdbc.core.namedparam.NamedParameterJdbcTemplate;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.time.Clock;
import java.time.LocalDate;
import java.util.List;

/**
 * 회원 프로필: 기본 정보 + 칭호(티어) · 뱃지 + 기록 + 참여 중인 공개 챌린지. 정지·탈퇴 회원은 없는 것처럼(404).
 * 뱃지는 따로 저장하지 않고 지금 기록(인증 횟수 · 최장 연속 · 완주 · 활동)으로 계산한다. 포인트와는 상관없다.
 */
@Service
@RequiredArgsConstructor
public class ProfileService {

    /** tiers.id 순서 (db/02-seed.sql) */
    private static final List<String> TIERS = List.of("BRONZE", "SILVER", "GOLD", "PLATINUM", "DIAMOND");

    /** 시작한 챌린지 중 참여한 수 (시작 전에 나갔거나 내보내진 것은 빼고) */
    private static final String JOINED_SQL = """
            SELECT COUNT(*) FROM challenge_participants p JOIN challenges c ON c.id = p.challenge_id
            WHERE p.user_id = :u AND p.status IN ('ACTIVE', 'COMPLETED', 'FAILED', 'GAVE_UP')
              AND c.status <> 'RECRUITING'
            """;
    /** 내가 연 챌린지 중 시작한 수 */
    private static final String HOSTED_SQL =
            "SELECT COUNT(*) FROM challenges WHERE host_id = :u AND status <> 'RECRUITING'";

    private final UserRepository userRepository;
    private final ChallengeRepository challengeRepository;
    private final ChallengeParticipantRepository participantRepository;
    private final RankingService rankingService;
    private final FollowRepository followRepository;
    private final UserBlockRepository blockRepository;
    private final NamedParameterJdbcTemplate jdbc;
    private final Clock clock;

    @Transactional(readOnly = true)
    public ProfileResponse profile(Long userId, Long viewerId) {
        User user = userRepository.findById(userId)
                .filter(u -> u.getStatus() == UserStatus.ACTIVE)
                .orElseThrow(() -> new BusinessException(ErrorCode.USER_NOT_FOUND));
        LocalDate today = LocalDate.now(clock);
        long maxStreak = (long) rankingService.valueOf(RankingMetric.MAX_STREAK, userId);
        long completed = participantRepository.countByUserIdAndStatus(userId, ParticipantStatus.COMPLETED);
        long totalSuccess = (long) rankingService.valueOf(RankingMetric.TOTAL_SUCCESS, userId);
        long followers = followRepository.followerCount(userId);
        return new ProfileResponse(user.getId(), user.getNickname(), user.getProfileImageUrl(), user.getBio(),
                user.getCreatedAt() == null ? null : user.getCreatedAt().toLocalDate(),
                (long) rankingService.valueOf(RankingMetric.MONTH_VERIFY, userId),
                maxStreak,
                rankingService.valueOrNull(RankingMetric.SUCCESS_RATE, userId),
                completed,
                challengeRepository.findPublicJoined(userId).stream().map(c -> ProfileChallenge.of(c, today)).toList(),
                userId.equals(viewerId),
                followers, followRepository.followingCount(userId),
                viewerId != null && followRepository.exists(viewerId, userId),
                viewerId != null && followRepository.exists(userId, viewerId),
                viewerId != null && blockRepository.existsByBlockerIdAndBlockedId(viewerId, userId),
                tierOf(user),
                badges(totalSuccess, maxStreak, completed, count(JOINED_SQL, userId), count(HOSTED_SQL, userId),
                        followers));
    }

    private static String tierOf(User user) {
        int index = user.getTierId() == null ? 0 : user.getTierId() - 1;
        return index >= 0 && index < TIERS.size() ? TIERS.get(index) : TIERS.get(0);
    }

    private long count(String sql, Long userId) {
        Long n = jdbc.queryForObject(sql, new MapSqlParameterSource("u", userId), Long.class);
        return n == null ? 0 : n;
    }

    /** 뱃지 전체 목록 15개 (묶음마다 쉬운 것부터)와 땄는지 · 얼마나 왔는지 */
    private static List<Badge> badges(long verify, long streak, long finished, long joined, long hosted,
                                      long followers) {
        return List.of(
                Badge.of("FIRST_VERIFY", "VERIFY", "첫 인증", "인증에 처음 성공하기", verify, 1),
                Badge.of("VERIFY_10", "VERIFY", "인증 10회", "인증에 10번 성공하기", verify, 10),
                Badge.of("VERIFY_30", "VERIFY", "인증 30회", "인증에 30번 성공하기", verify, 30),
                Badge.of("VERIFY_100", "VERIFY", "인증 100회", "인증에 100번 성공하기", verify, 100),
                Badge.of("STREAK_3", "STREAK", "작심삼일 돌파", "3일 연속 인증하기", streak, 3),
                Badge.of("STREAK_7", "STREAK", "일주일 꾸준히", "7일 연속 인증하기", streak, 7),
                Badge.of("STREAK_14", "STREAK", "2주 연속", "14일 연속 인증하기", streak, 14),
                Badge.of("STREAK_30", "STREAK", "한 달 갓생", "30일 연속 인증하기", streak, 30),
                Badge.of("STREAK_100", "STREAK", "100일의 기적", "100일 연속 인증하기", streak, 100),
                Badge.of("FINISHER", "FINISH", "첫 완주", "챌린지 하나를 끝까지 성공하기", finished, 1),
                Badge.of("FINISHER_5", "FINISH", "완주 5회", "챌린지 5개를 끝까지 성공하기", finished, 5),
                Badge.of("FINISHER_10", "FINISH", "완주 10회", "챌린지 10개를 끝까지 성공하기", finished, 10),
                Badge.of("CHALLENGER", "ACTIVITY", "도전가", "챌린지 3개에 참여하기", joined, 3),
                Badge.of("HOST", "ACTIVITY", "방장 데뷔", "내가 연 챌린지 시작하기", hosted, 1),
                Badge.of("POPULAR", "ACTIVITY", "인기인", "팔로워 10명 모으기", followers, 10));
    }
}
