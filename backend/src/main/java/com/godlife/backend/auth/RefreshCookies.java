package com.godlife.backend.auth;

import com.godlife.backend.config.JwtProperties;
import lombok.RequiredArgsConstructor;
import org.springframework.http.ResponseCookie;
import org.springframework.stereotype.Component;

import java.time.Duration;

/** 리프레시 토큰 쿠키. 이메일 로그인과 소셜 로그인이 같은 쿠키를 쓴다. */
@Component
@RequiredArgsConstructor
public class RefreshCookies {

    public static final String NAME = "refresh_token";
    private static final String PATH = "/api/auth";

    private final JwtProperties jwtProperties;

    public ResponseCookie issue(String rawRefreshToken) {
        return build(rawRefreshToken, Duration.ofDays(jwtProperties.refreshTokenDays()));
    }

    public ResponseCookie expire() {
        return build("", Duration.ZERO);
    }

    /** JS 에서 읽을 수 없고(HttpOnly), 다른 사이트 요청에는 실리지 않으며(Strict), 인증 경로에서만 전송된다. */
    private ResponseCookie build(String value, Duration maxAge) {
        return ResponseCookie.from(NAME, value)
                .httpOnly(true)
                .secure(jwtProperties.refreshCookieSecure())
                .sameSite("Strict")
                .path(PATH)
                .maxAge(maxAge)
                .build();
    }
}
