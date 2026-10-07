package com.godlife.backend.season;

import com.godlife.backend.auth.AuthUser;
import com.godlife.backend.common.error.BusinessException;
import com.godlife.backend.common.error.ErrorCode;
import lombok.RequiredArgsConstructor;
import org.springframework.security.core.annotation.AuthenticationPrincipal;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.RequestParam;
import org.springframework.web.bind.annotation.RestController;

import java.util.Locale;

/** 시즌 랭킹(주간 · 월간). 비로그인도 볼 수 있고, 로그인하면 내 순위가 같이 온다. */
@RestController
@RequiredArgsConstructor
public class SeasonController {

    private final SeasonService seasonService;

    /** 지금 진행 중인 시즌. ?type=weekly(기본) | monthly */
    @GetMapping("/api/seasons/current")
    public SeasonService.SeasonView current(@RequestParam(defaultValue = "weekly") String type,
                                            @AuthenticationPrincipal AuthUser authUser) {
        SeasonService.SeasonView view = seasonService.current(parse(type), authUser == null ? null : authUser.id());
        if (view == null) {
            throw new BusinessException(ErrorCode.SEASON_NOT_FOUND);
        }
        return view;
    }

    /** 지난 시즌 결과 */
    @GetMapping("/api/seasons/{id}")
    public SeasonService.SeasonView byId(@PathVariable long id, @AuthenticationPrincipal AuthUser authUser) {
        SeasonService.SeasonView view = seasonService.byId(id, authUser == null ? null : authUser.id());
        if (view == null) {
            throw new BusinessException(ErrorCode.SEASON_NOT_FOUND);
        }
        return view;
    }

    private static SeasonService.Type parse(String type) {
        try {
            return SeasonService.Type.valueOf(type.toUpperCase(Locale.ROOT));
        } catch (IllegalArgumentException e) {
            return SeasonService.Type.WEEKLY;
        }
    }
}
