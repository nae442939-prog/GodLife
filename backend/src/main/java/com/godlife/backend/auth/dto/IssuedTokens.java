package com.godlife.backend.auth.dto;

/**
 * 서비스 → 컨트롤러 전달용. refreshToken 원문은 쿠키로만 내보내고 DB 에는 해시만 남는다.
 * @param persistent 자동 로그인을 켠 회원인지. true 면 쿠키를 14일 남기고, false 면 브라우저를 닫을 때 사라지는 쿠키로 내려 준다
 */
public record IssuedTokens(String accessToken, long accessExpiresInSeconds, String refreshToken, boolean persistent) {

    @Override
    public String toString() {
        return "IssuedTokens[***]";
    }
}
