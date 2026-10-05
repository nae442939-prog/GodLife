package com.godlife.backend.auth;

import com.godlife.backend.auth.dto.IssuedTokens;
import com.godlife.backend.auth.dto.LoginRequest;
import com.godlife.backend.auth.dto.SignupRequest;
import com.godlife.backend.auth.dto.TokenResponse;
import com.godlife.backend.device.DeviceService;
import com.godlife.backend.user.User;
import com.godlife.backend.user.dto.UserResponse;
import jakarta.servlet.http.HttpServletRequest;
import jakarta.validation.Valid;
import lombok.RequiredArgsConstructor;
import org.springframework.http.HttpHeaders;
import org.springframework.http.HttpStatus;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.CookieValue;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;

@RestController
@RequestMapping("/api/auth")
@RequiredArgsConstructor
public class AuthController {

    static final String REFRESH_COOKIE = RefreshCookies.NAME;

    private final AuthService authService;
    private final RefreshCookies refreshCookies;
    private final DeviceService deviceService;

    @PostMapping("/signup")
    public ResponseEntity<UserResponse> signup(@Valid @RequestBody SignupRequest request, HttpServletRequest http) {
        User user = authService.signup(request);
        recordDevice(user.getId(), http);
        return ResponseEntity.status(HttpStatus.CREATED).body(UserResponse.from(user));
    }

    @PostMapping("/login")
    public ResponseEntity<TokenResponse> login(@Valid @RequestBody LoginRequest request, HttpServletRequest http) {
        IssuedTokens tokens = authService.login(request);
        recordDevice(tokens.userId(), http);
        return withRefreshCookie(tokens);
    }

    /** 소셜 로그인은 화면을 옮겨 다니느라 기기 지문을 못 보내서, 로그인 직후 오는 이 재발급 요청에서 기기를 남긴다 */
    @PostMapping("/refresh")
    public ResponseEntity<TokenResponse> refresh(
            @CookieValue(name = REFRESH_COOKIE, required = false) String refreshToken, HttpServletRequest http) {
        IssuedTokens tokens = authService.refresh(refreshToken);
        recordDevice(tokens.userId(), http);
        return withRefreshCookie(tokens);
    }

    @PostMapping("/logout")
    public ResponseEntity<Void> logout(
            @CookieValue(name = REFRESH_COOKIE, required = false) String refreshToken) {
        authService.logout(refreshToken);
        return ResponseEntity.noContent()
                .header(HttpHeaders.SET_COOKIE, refreshCookies.expire().toString())
                .build();
    }

    /** 브라우저가 보낸 기기 지문(없을 수 있다)으로 이 회원의 기기를 남긴다. 가입 · 로그인 결과에는 영향이 없다. */
    private void recordDevice(Long userId, HttpServletRequest http) {
        deviceService.record(userId, http.getHeader(DeviceService.HEADER), http.getRemoteAddr(),
                http.getHeader(HttpHeaders.USER_AGENT));
    }

    private ResponseEntity<TokenResponse> withRefreshCookie(IssuedTokens tokens) {
        return ResponseEntity.ok()
                .header(HttpHeaders.SET_COOKIE, refreshCookies.issue(tokens).toString())
                .header(HttpHeaders.CACHE_CONTROL, "no-store")
                .body(TokenResponse.bearer(tokens.accessToken(), tokens.accessExpiresInSeconds()));
    }
}
