package com.godlife.backend.auth.social;

import com.godlife.backend.auth.dto.IssuedTokens;
import com.godlife.backend.common.error.BusinessException;
import com.godlife.backend.common.error.ErrorCode;
import org.springframework.stereotype.Component;

import java.security.SecureRandom;
import java.time.Clock;
import java.time.Duration;
import java.time.Instant;
import java.util.Base64;
import java.util.Map;
import java.util.concurrent.ConcurrentHashMap;

/**
 * 안드로이드 앱의 소셜 로그인용 1회용 코드.
 * 앱은 제공자 화면을 브라우저(Custom Tab)로 열기 때문에, 서버가 심는 리프레시 쿠키가 앱이 아니라 브라우저에 남는다.
 * 그래서 로그인이 끝나면 토큰 대신 짧게 사는 코드만 앱 주소(godlife://login?code=...)로 넘기고,
 * 앱이 그 코드를 /api/auth/app-login 으로 바꿔 자기 쿠키 저장소에 리프레시 쿠키를 받는다.
 * 코드는 한 번 쓰면 사라지고 1분 뒤에도 사라진다 (서버 한 대라 메모리에 둔다).
 */
@Component
public class AppLoginCodes {

    static final Duration TTL = Duration.ofMinutes(1);

    private final SecureRandom random = new SecureRandom();
    private final Map<String, Entry> codes = new ConcurrentHashMap<>();
    private final Clock clock;

    public AppLoginCodes() {
        this(Clock.systemUTC());
    }

    AppLoginCodes(Clock clock) {
        this.clock = clock;
    }

    public String issue(IssuedTokens tokens) {
        Instant now = clock.instant();
        codes.values().removeIf(e -> e.expiresAt().isBefore(now));
        byte[] bytes = new byte[32];
        random.nextBytes(bytes);
        String code = Base64.getUrlEncoder().withoutPadding().encodeToString(bytes);
        codes.put(code, new Entry(tokens, now.plus(TTL)));
        return code;
    }

    /** 코드를 토큰으로 바꾼다. 없거나 지났거나 이미 쓴 코드면 소셜 로그인 실패. */
    public IssuedTokens consume(String code) {
        Entry entry = code == null ? null : codes.remove(code);
        if (entry == null || entry.expiresAt().isBefore(clock.instant())) {
            throw new BusinessException(ErrorCode.SOCIAL_LOGIN_FAILED);
        }
        return entry.tokens();
    }

    private record Entry(IssuedTokens tokens, Instant expiresAt) {
    }
}
