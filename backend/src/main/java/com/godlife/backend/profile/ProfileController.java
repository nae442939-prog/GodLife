package com.godlife.backend.profile;

import com.godlife.backend.auth.AuthUser;
import com.godlife.backend.profile.dto.ProfileResponse;
import lombok.RequiredArgsConstructor;
import org.springframework.security.core.annotation.AuthenticationPrincipal;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.RestController;

/** 회원 프로필. 비로그인도 볼 수 있다 (랭킹·참가자 목록에서 눌러 들어온다). */
@RestController
@RequiredArgsConstructor
public class ProfileController {

    private final ProfileService profileService;

    @GetMapping("/api/users/{id}/profile")
    public ProfileResponse profile(@PathVariable Long id, @AuthenticationPrincipal AuthUser authUser) {
        return profileService.profile(id, authUser == null ? null : authUser.id());
    }
}
