package com.godlife.backend.user;

import com.godlife.backend.auth.AuthUser;
import com.godlife.backend.user.WithdrawalService.WithdrawalCheck;
import com.godlife.backend.user.dto.ProfileEditDtos.AccountResponse;
import com.godlife.backend.user.dto.ProfileEditDtos.PasswordChangeRequest;
import com.godlife.backend.user.dto.ProfileEditDtos.ProfileUpdateRequest;
import com.godlife.backend.user.dto.UserResponse;
import jakarta.validation.Valid;
import lombok.RequiredArgsConstructor;
import org.springframework.core.io.FileSystemResource;
import org.springframework.core.io.Resource;
import org.springframework.http.CacheControl;
import org.springframework.http.MediaType;
import org.springframework.http.ResponseEntity;
import org.springframework.security.core.annotation.AuthenticationPrincipal;
import org.springframework.web.bind.annotation.DeleteMapping;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.PutMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestPart;
import org.springframework.web.bind.annotation.RestController;
import org.springframework.web.multipart.MultipartFile;

import java.time.Duration;

/** 마이페이지 · 설정: 내 프로필(닉네임 · 한 줄 소개 · 사진), 계정(비밀번호), 회원 탈퇴 */
@RestController
@RequiredArgsConstructor
public class ProfileEditController {

    private final ProfileEditService profileEditService;
    private final WithdrawalService withdrawalService;
    private final UserService userService;

    /** 탈퇴 확인 (비밀번호가 있는 계정은 password, 소셜 계정은 confirm = '탈퇴') */
    public record WithdrawRequest(String password, String confirm) {

        @Override
        public String toString() {
            return "WithdrawRequest[password=***, confirm=" + confirm + "]";
        }
    }

    /** 탈퇴 전 확인: 탈퇴할 수 있는지, 막는 이유, 사라지는 보상 포인트 */
    @GetMapping("/api/users/me/withdrawal")
    public WithdrawalCheck withdrawalCheck(@AuthenticationPrincipal AuthUser authUser) {
        return withdrawalService.check(authUser.id());
    }

    /** 탈퇴 본인 확인: 비밀번호(또는 확인 문구)가 맞는지만 본다. 맞으면 화면이 마지막 경고를 띄운다 */
    @PostMapping("/api/users/me/withdrawal/verify")
    public ResponseEntity<Void> verifyWithdrawal(@AuthenticationPrincipal AuthUser authUser,
                                                 @RequestBody WithdrawRequest request) {
        withdrawalService.verify(authUser.id(), request.password(), request.confirm());
        return ResponseEntity.noContent().build();
    }

    /** 회원 탈퇴 (되돌릴 수 없다) */
    @PostMapping("/api/users/me/withdrawal")
    public ResponseEntity<Void> withdraw(@AuthenticationPrincipal AuthUser authUser,
                                         @RequestBody WithdrawRequest request) {
        withdrawalService.withdraw(authUser.id(), request.password(), request.confirm());
        return ResponseEntity.noContent().build();
    }

    /** 설정의 '계정' 칸: 이메일 · 비밀번호가 있는지 · 연결된 소셜 계정 */
    @GetMapping("/api/users/me/account")
    public AccountResponse account(@AuthenticationPrincipal AuthUser authUser) {
        return profileEditService.account(authUser.id());
    }

    @PutMapping("/api/users/me/profile")
    public UserResponse updateProfile(@AuthenticationPrincipal AuthUser authUser,
                                      @Valid @RequestBody ProfileUpdateRequest request) {
        return userService.toResponse(
                profileEditService.updateProfile(authUser.id(), request.nickname(), request.bio()));
    }

    /** 프로필 사진 바꾸기 (multipart: file = JPG/PNG 5MB 이하) */
    @PostMapping(value = "/api/users/me/profile-image", consumes = MediaType.MULTIPART_FORM_DATA_VALUE)
    public UserResponse changeImage(@AuthenticationPrincipal AuthUser authUser,
                                    @RequestPart("file") MultipartFile file) {
        return userService.toResponse(profileEditService.changeImage(authUser.id(), file));
    }

    @DeleteMapping("/api/users/me/profile-image")
    public UserResponse removeImage(@AuthenticationPrincipal AuthUser authUser) {
        return userService.toResponse(profileEditService.removeImage(authUser.id()));
    }

    /** 자동 로그인 켜기/끄기 (설정 → 보안). 쿠키 수명은 다음 토큰 재발급(/api/auth/refresh) 때부터 바뀐다. */
    public record AutoLoginRequest(boolean enabled) {
    }

    @PutMapping("/api/users/me/auto-login")
    public UserResponse changeAutoLogin(@AuthenticationPrincipal AuthUser authUser,
                                        @RequestBody AutoLoginRequest request) {
        return userService.toResponse(profileEditService.changeAutoLogin(authUser.id(), request.enabled()));
    }

    /** 비밀번호 바꾸기. 성공하면 모든 기기에서 로그아웃되므로 다시 로그인해야 한다. */
    @PutMapping("/api/users/me/password")
    public ResponseEntity<Void> changePassword(@AuthenticationPrincipal AuthUser authUser,
                                               @Valid @RequestBody PasswordChangeRequest request) {
        profileEditService.changePassword(authUser.id(), request.currentPassword(), request.newPassword());
        return ResponseEntity.noContent().build();
    }

    /** 올린 프로필 사진 (비로그인도 볼 수 있다). 사진을 바꾸면 주소도 바뀌므로 오래 캐시해도 된다. */
    @GetMapping("/api/profile-images/{userId}/{name}")
    public ResponseEntity<Resource> image(@PathVariable Long userId, @PathVariable String name) {
        return ResponseEntity.ok()
                .contentType(MediaType.IMAGE_JPEG)
                .cacheControl(CacheControl.maxAge(Duration.ofDays(30)).cachePublic())
                .body(new FileSystemResource(profileEditService.imageFile(userId, name)));
    }
}
