package com.godlife.backend.ranking;

import com.godlife.backend.auth.AuthUser;
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

    /** 전체(개인) 랭킹. ?metric=month_verify(기본) | max_streak | month_reward | success_rate */
    @GetMapping("/api/rankings/users")
    public UserRankingResponse users(@RequestParam(defaultValue = "month_verify") String metric,
                                     @AuthenticationPrincipal AuthUser authUser) {
        RankingMetric m;
        try {
            m = RankingMetric.valueOf(metric.toUpperCase(Locale.ROOT));
        } catch (IllegalArgumentException e) {
            m = RankingMetric.MONTH_VERIFY;
        }
        return rankingService.users(m, authUser == null ? null : authUser.id());
    }

    /** 챌린지(팀) 랭킹: 진행 중인 공개 챌린지의 참가자 평균 달성률 */
    @GetMapping("/api/rankings/challenges")
    public List<ChallengeRankingResponse> challenges() {
        return rankingService.challenges();
    }
}
