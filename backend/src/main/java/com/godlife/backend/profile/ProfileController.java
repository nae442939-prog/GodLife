package com.godlife.backend.profile;

import com.godlife.backend.auth.AuthUser;
import com.godlife.backend.profile.dto.ProfileResponse;
import lombok.RequiredArgsConstructor;
import org.springframework.security.core.annotation.AuthenticationPrincipal;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.PutMapping;
import org.springframework.web.bind.annotation.RequestBody;
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

    /** 착용할 뱃지 (code 를 비우면 해제) */
    public record EquipBadgeRequest(String code) {
    }

    /** 뱃지 착용 · 해제 (설정 → 프로필). 딴 뱃지만 착용할 수 있고, 착용한 뱃지는 이름 옆에 보인다 */
    @PutMapping("/api/users/me/badge")
    public ProfileResponse equipBadge(@AuthenticationPrincipal AuthUser authUser,
                                      @RequestBody EquipBadgeRequest request) {
        return profileService.equipBadge(authUser.id(), request.code());
    }
}
