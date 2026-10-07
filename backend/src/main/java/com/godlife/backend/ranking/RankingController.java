package com.godlife.backend.ranking;

import com.godlife.backend.auth.AuthUser;
import com.godlife.backend.common.error.BusinessException;
import com.godlife.backend.common.error.ErrorCode;
import com.godlife.backend.ranking.dto.ChallengeRankingResponse;
import com.godlife.backend.ranking.dto.UserRankingResponse;
import lombok.RequiredArgsConstructor;
import org.springframework.security.core.annotation.AuthenticationPrincipal;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.RequestParam;
import org.springframework.web.bind.annotation.RestController;

import java.util.List;
import java.util.Locale;

/** 랭킹 메뉴. 비로그인도 볼 수 있고, 로그인하면 내 순위가 같이 온다. */
@RestController
@RequiredArgsConstructor
public class RankingController {

    private final RankingService rankingService;

    /** 전체(개인) 랭킹. ?metric=month_verify(기본) | total_success | max_streak | month_reward | success_rate */
    @GetMapping("/api/rankings/users")
    public UserRankingResponse users(@RequestParam(defaultValue = "month_verify") String metric,
                                     @AuthenticationPrincipal AuthUser authUser) {
        return rankingService.users(parse(metric), authUser == null ? null : authUser.id());
    }

    /** 친구 랭킹: 나 + 내가 팔로우한 사람끼리 (로그인 필요) */
    @GetMapping("/api/rankings/friends")
    public UserRankingResponse friends(@RequestParam(defaultValue = "month_verify") String metric,
                                       @AuthenticationPrincipal AuthUser authUser) {
        if (authUser == null) {
            throw new BusinessException(ErrorCode.UNAUTHORIZED);
        }
        return rankingService.friends(parse(metric), authUser.id());
    }

    /** 챌린지(팀) 랭킹: 진행 중인 공개 챌린지의 참가자 평균 달성률 */
    @GetMapping("/api/rankings/challenges")
    public List<ChallengeRankingResponse> challenges() {
        return rankingService.challenges();
    }

    /** 잘못된 기준은 기본(이번 달 인증)으로 */
    private static RankingMetric parse(String metric) {
        try {
            return RankingMetric.valueOf(metric.toUpperCase(Locale.ROOT));
        } catch (IllegalArgumentException e) {
            return RankingMetric.MONTH_VERIFY;
        }
    }
}
