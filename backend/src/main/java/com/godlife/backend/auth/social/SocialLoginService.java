package com.godlife.backend.auth.social;

import com.godlife.backend.auth.AuthService;
import com.godlife.backend.auth.dto.IssuedTokens;
import com.godlife.backend.common.crypto.Hashing;
import com.godlife.backend.common.error.BusinessException;
import com.godlife.backend.common.error.ErrorCode;
import com.godlife.backend.user.User;
import com.godlife.backend.user.UserRepository;
import com.godlife.backend.user.UserStatus;
import lombok.RequiredArgsConstructor;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.security.SecureRandom;
import java.util.Locale;

/**
 * 소셜 로그인 → 우리 회원으로 연결/가입 → 토큰 발급.
 * <ol>
 *   <li>이미 연결된 소셜 계정이면 그 회원으로 로그인한다.</li>
 *   <li>제공자가 검증한 이메일이 기존 회원과 같으면 그 회원에 연결한다.
 *       (검증되지 않은 이메일로 연결하면 남의 계정을 가로챌 수 있다)</li>
 *   <li>그 외에는 새로 가입시킨다. 쓸 수 있는 검증된 이메일이 없으면 가상 이메일을 쓴다.</li>
 * </ol>
 */
@Service
@RequiredArgsConstructor
public class SocialLoginService {

    static final String VIRTUAL_EMAIL_DOMAIN = "social.godlife.local";
    static final String DEFAULT_NICKNAME = "갓생러";

    private static final int NICKNAME_BASE_MAX = 15;
    private static final int NICKNAME_ATTEMPTS = 10;
    private static final SecureRandom RANDOM = new SecureRandom();

    private final SocialAccountRepository socialAccountRepository;
    private final UserRepository userRepository;
    private final AuthService authService;

    /**
     * 같은 사용자가 두 탭에서 동시에 처음 로그인하면 유니크 제약(provider, provider_user_id)에 걸려 한쪽이 실패한다.
     * 그 경우 전체가 롤백되어 반쪽짜리 회원이 남지 않고, 다시 시도하면 1번 경로로 로그인된다.
     */
    @Transactional
    public IssuedTokens login(SocialProfile profile) {
        User user = socialAccountRepository
                .findByProviderAndProviderUserId(profile.provider(), profile.providerUserId())
                .map(account -> userRepository.findById(account.getUserId()).orElseThrow())
                .orElseGet(() -> linkOrSignup(profile));

        if (user.getStatus() == UserStatus.SUSPENDED) {
            throw new BusinessException(ErrorCode.ACCOUNT_SUSPENDED);
        }
        if (user.getStatus() == UserStatus.WITHDRAWN) {
            throw new BusinessException(ErrorCode.SOCIAL_LOGIN_FAILED);
        }
        return authService.issueTokens(user);
    }

    private User linkOrSignup(SocialProfile profile) {
        String verifiedEmail = profile.emailVerified() && profile.email() != null
                ? profile.email().trim().toLowerCase(Locale.ROOT)
                : null;

        User user = verifiedEmail == null ? null : userRepository.findByEmail(verifiedEmail).orElse(null);
        if (user == null) {
            String email = verifiedEmail != null ? verifiedEmail : virtualEmail(profile);
            user = userRepository.save(User.createSocial(email, availableNickname(profile.nickname())));
        }
        socialAccountRepository.save(SocialAccount.link(user.getId(), profile.provider(), profile.providerUserId()));
        return user;
    }

    /** 이메일이 없거나 검증되지 않은 경우의 로그인 ID. 실제로 메일을 받을 수 없는 도메인이다. */
    static String virtualEmail(SocialProfile profile) {
        return profile.provider().name().toLowerCase(Locale.ROOT) + "_" + profile.providerUserId()
                + "@" + VIRTUAL_EMAIL_DOMAIN;
    }

    /** 닉네임 규칙(한글/영문/숫자 2~20자)에 맞게 다듬고, 겹치면 숫자 4자리를 붙인다. */
    private String availableNickname(String raw) {
        String base = sanitizeNickname(raw);
        if (!userRepository.existsByNickname(base)) {
            return base;
        }
        for (int i = 0; i < NICKNAME_ATTEMPTS; i++) {
            String candidate = base + String.format("%04d", RANDOM.nextInt(10_000));
            if (!userRepository.existsByNickname(candidate)) {
                return candidate;
            }
        }
        return DEFAULT_NICKNAME + Hashing.randomDigits(8);
    }

    static String sanitizeNickname(String raw) {
        String cleaned = raw == null ? "" : raw.replaceAll("[^가-힣a-zA-Z0-9]", "");
        if (cleaned.length() > NICKNAME_BASE_MAX) {
            cleaned = cleaned.substring(0, NICKNAME_BASE_MAX);
        }
        return cleaned.length() < 2 ? DEFAULT_NICKNAME : cleaned;
    }
}
