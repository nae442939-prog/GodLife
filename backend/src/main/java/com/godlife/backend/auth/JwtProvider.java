package com.godlife.backend.auth;

import com.godlife.backend.config.JwtProperties;
import io.jsonwebtoken.Claims;
import io.jsonwebtoken.JwtException;
import io.jsonwebtoken.Jwts;
import io.jsonwebtoken.security.Keys;
import org.springframework.stereotype.Component;

import javax.crypto.SecretKey;
import java.nio.charset.StandardCharsets;
import java.time.Clock;
import java.time.Duration;
import java.util.Date;

@Component
public class JwtProvider {

    private static final int MIN_SECRET_BYTES = 32;

    private final SecretKey key;
    private final Duration accessTtl;
    private final Clock clock;

    public JwtProvider(JwtProperties props, Clock clock) {
        if (props.secret() == null || props.secret().getBytes(StandardCharsets.UTF_8).length < MIN_SECRET_BYTES) {
            // 키가 없거나 짧으면 서버가 뜨지 않게 한다. (약한 키로 조용히 동작하는 것보다 낫다)
            throw new IllegalStateException("jwt.secret(JWT_SECRET)은 32바이트 이상이어야 합니다.");
        }
        this.key = Keys.hmacShaKeyFor(props.secret().getBytes(StandardCharsets.UTF_8));
        this.accessTtl = Duration.ofMinutes(props.accessTokenMinutes());
        this.clock = clock;
    }

    public long accessTtlSeconds() {
        return accessTtl.toSeconds();
    }

    public String createAccessToken(Long userId, String role) {
        return Jwts.builder()
                .subject(String.valueOf(userId))
                .claim("role", role)
                .issuedAt(Date.from(clock.instant()))
                .expiration(Date.from(clock.instant().plus(accessTtl)))
                .signWith(key)
                .compact();
    }

    /** 서명/만료가 유효하면 AuthUser, 아니면 JwtException. */
    public AuthUser parse(String token) {
        Claims claims = Jwts.parser()
                .verifyWith(key)
                .clock(() -> Date.from(clock.instant()))
                .build()
                .parseSignedClaims(token)
                .getPayload();
        try {
            return new AuthUser(Long.valueOf(claims.getSubject()), claims.get("role", String.class));
        } catch (NumberFormatException e) {
            throw new JwtException("invalid subject", e);
        }
    }
}
