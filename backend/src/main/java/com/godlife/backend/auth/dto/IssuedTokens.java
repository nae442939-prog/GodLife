package com.godlife.backend.auth.dto;

/** 서비스 → 컨트롤러 전달용. refreshToken 원문은 쿠키로만 내보내고 DB 에는 해시만 남는다. */
public record IssuedTokens(String accessToken, long accessExpiresInSeconds, String refreshToken) {

    @Override
    public String toString() {
        return "IssuedTokens[***]";
    }
}
