package com.godlife.backend.config;

import org.springframework.boot.context.properties.ConfigurationProperties;
import org.springframework.boot.context.properties.bind.DefaultValue;

/**
 * @param secret              HMAC 서명 키. 32바이트 이상. 환경변수(JWT_SECRET)로만 주입하고 저장소에 커밋하지 않는다.
 * @param accessTokenMinutes  액세스 토큰 유효 시간(분)
 * @param refreshTokenDays    리프레시 토큰 유효 기간(일)
 * @param refreshCookieSecure HTTPS 로 배포할 때만 true. 로컬 개발(http)에서는 false 여야 쿠키가 저장된다.
 */
@ConfigurationProperties(prefix = "jwt")
public record JwtProperties(
        String secret,
        @DefaultValue("15") long accessTokenMinutes,
        @DefaultValue("14") long refreshTokenDays,
        @DefaultValue("false") boolean refreshCookieSecure) {
}
