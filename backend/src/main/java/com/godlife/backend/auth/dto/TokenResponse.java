package com.godlife.backend.auth.dto;

/** 응답 본문에는 액세스 토큰만 담는다. 리프레시 토큰은 HttpOnly 쿠키로만 내려간다. */
public record TokenResponse(String accessToken, String tokenType, long expiresIn) {

    public static TokenResponse bearer(String accessToken, long expiresInSeconds) {
        return new TokenResponse(accessToken, "Bearer", expiresInSeconds);
    }
}
