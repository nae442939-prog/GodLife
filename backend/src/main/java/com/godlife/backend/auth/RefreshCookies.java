package com.godlife.backend.auth;

import com.godlife.backend.auth.dto.IssuedTokens;
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

    /**
     * 자동 로그인을 켠 회원은 쿠키를 리프레시 토큰 수명(14일)만큼 남긴다.
     * 끈 회원은 Max-Age 없이 내려 준다 → 브라우저를 닫으면 쿠키가 사라져 다시 로그인해야 한다.
     */
    public ResponseCookie issue(IssuedTokens tokens) {
        return build(tokens.refreshToken(),
                tokens.persistent() ? Duration.ofDays(jwtProperties.refreshTokenDays()) : null);
    }

    public ResponseCookie expire() {
        return build("", Duration.ZERO);
    }

    /**
     * JS 에서 읽을 수 없고(HttpOnly), 다른 사이트 요청에는 실리지 않으며(Strict), 인증 경로에서만 전송된다.
     * maxAge 가 null 이면 세션 쿠키다.
     */
    private ResponseCookie build(String value, Duration maxAge) {
        ResponseCookie.ResponseCookieBuilder cookie = ResponseCookie.from(NAME, value)
                .httpOnly(true)
                .secure(jwtProperties.refreshCookieSecure())
                .sameSite("Strict")
                .path(PATH);
        if (maxAge != null) {
            cookie.maxAge(maxAge);
        }
        return cookie.build();
    }
}
