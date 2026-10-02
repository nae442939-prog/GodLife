package com.godlife.backend.tier;

import com.godlife.backend.auth.AuthUser;
import com.godlife.backend.tier.TierService.MyTier;
import lombok.RequiredArgsConstructor;
import org.springframework.security.core.annotation.AuthenticationPrincipal;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.RestController;

/** 내 칭호: 지금 점수 · 다음 칭호까지 남은 점수 · 칭호별 혜택. 로그인 회원만. */
@RestController
@RequiredArgsConstructor
public class TierController {

    private final TierService tierService;

    @GetMapping("/api/users/me/tier")
    public MyTier mine(@AuthenticationPrincipal AuthUser authUser) {
        return tierService.mine(authUser.id());
    }
}
