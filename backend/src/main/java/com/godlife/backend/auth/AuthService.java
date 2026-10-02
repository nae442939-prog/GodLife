package com.godlife.backend.auth;

import com.godlife.backend.auth.dto.IssuedTokens;
import com.godlife.backend.auth.dto.LoginRequest;
import com.godlife.backend.auth.dto.SignupRequest;
import com.godlife.backend.common.crypto.Hashing;
import com.godlife.backend.common.error.BusinessException;
import com.godlife.backend.common.error.ErrorCode;
import com.godlife.backend.config.JwtProperties;
import com.godlife.backend.phone.PhoneVerification;
import com.godlife.backend.phone.PhoneVerificationService;
import com.godlife.backend.user.User;
import com.godlife.backend.user.UserRepository;
import com.godlife.backend.user.UserStatus;
import jakarta.annotation.PostConstruct;
import lombok.RequiredArgsConstructor;
import org.springframework.dao.DataIntegrityViolationException;
import org.springframework.security.crypto.password.PasswordEncoder;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;
import org.springframework.transaction.support.TransactionTemplate;

import java.time.Clock;
import java.time.LocalDateTime;
import java.util.Locale;

@Service
@RequiredArgsConstructor
public class AuthService {

    /** 회전 직후 다른 탭이 옛 토큰으로 동시에 요청하는 경우는 탈취로 보지 않는 유예 시간. */
    private static final long REUSE_GRACE_SECONDS = 10;

    private final UserRepository userRepository;
    private final RefreshTokenRepository refreshTokenRepository;
    private final PasswordEncoder passwordEncoder;
    private final JwtProvider jwtProvider;
    private final JwtProperties jwtProperties;
    private final Clock clock;
    private final PhoneVerificationService phoneVerificationService;
    private final TransactionTemplate transactionTemplate;

    /** 존재하지 않는 이메일로 로그인할 때도 BCrypt 비교를 한 번 수행해 응답 시간 차이로 가입 여부를 알 수 없게 한다. */
    private String dummyHash;

    @PostConstruct
    void initDummyHash() {
        this.dummyHash = passwordEncoder.encode("timing-equalizer-not-a-real-password");
    }

    /**
     * 휴대폰 인증 증표 사용과 회원 저장을 한 트랜잭션으로 묶는다. (가입이 실패하면 증표도 다시 쓸 수 있게)
     * 메서드 자체에는 트랜잭션을 걸지 않는다: 유니크 제약 위반이 나면 롤백된 뒤 깨끗한 새 트랜잭션에서 원인을 다시 조회한다.
     */
    public User signup(SignupRequest req) {
        String email = normalizeEmail(req.email());
        if (userRepository.existsByEmail(email)) {
            throw new BusinessException(ErrorCode.DUPLICATE_EMAIL);
        }
        if (userRepository.existsByNickname(req.nickname())) {
            throw new BusinessException(ErrorCode.DUPLICATE_NICKNAME);
        }
        String passwordHash = passwordEncoder.encode(req.password());
        try {
            return transactionTemplate.execute(tx -> {
                PhoneVerification phone = phoneVerificationService.consumeProof(req.phoneProof());
                if (userRepository.existsByPhoneHash(phone.getPhoneHash())) {
                    throw new BusinessException(ErrorCode.PHONE_ALREADY_REGISTERED);
                }
                User created = User.createWithPassword(email, passwordHash, req.nickname(), phone.getPhoneHash());
                created.rememberPhone(phone.getPhoneEnc());
                User user = userRepository.saveAndFlush(created);
                phone.linkUser(user.getId());
                return user;
            });
        } catch (DataIntegrityViolationException e) {
            // 동시 가입으로 사전 검사를 통과한 뒤 DB 유니크 제약에 걸린 경우
            if (userRepository.existsByEmail(email)) {
                throw new BusinessException(ErrorCode.DUPLICATE_EMAIL);
            }
            if (userRepository.existsByNickname(req.nickname())) {
                throw new BusinessException(ErrorCode.DUPLICATE_NICKNAME);
            }
            throw new BusinessException(ErrorCode.PHONE_ALREADY_REGISTERED);
        }
    }

    @Transactional
    public IssuedTokens login(LoginRequest req) {
        User user = userRepository.findByEmail(normalizeEmail(req.email())).orElse(null);
        boolean hasPassword = user != null && user.getPasswordHash() != null;
        boolean matches = passwordEncoder.matches(req.password(), hasPassword ? user.getPasswordHash() : dummyHash);
        if (!hasPassword || !matches) {
            throw new BusinessException(ErrorCode.INVALID_CREDENTIALS);
        }
        // 비밀번호가 맞은 뒤에만 계정 상태를 알려 준다. (상태로 가입 여부를 추측하지 못하게)
        if (user.getStatus() == UserStatus.WITHDRAWN) {
            throw new BusinessException(ErrorCode.INVALID_CREDENTIALS);
        }
        if (user.getStatus() == UserStatus.SUSPENDED) {
            throw new BusinessException(ErrorCode.ACCOUNT_SUSPENDED);
        }
        return issueTokens(user);
    }

    /**
     * 리프레시 토큰 회전: 쓴 토큰은 폐기하고 새 토큰을 발급한다.
     * 실패 시에도 폐기(재사용 감지 시 전체 세션 폐기)가 남도록 BusinessException 에서는 롤백하지 않는다.
     */
    @Transactional(noRollbackFor = BusinessException.class)
    public IssuedTokens refresh(String rawRefreshToken) {
        if (rawRefreshToken == null || rawRefreshToken.isBlank()) {
            throw new BusinessException(ErrorCode.INVALID_REFRESH_TOKEN);
        }
        RefreshToken stored = refreshTokenRepository.findByTokenHashForUpdate(Hashing.sha256Hex(rawRefreshToken))
                .orElseThrow(() -> new BusinessException(ErrorCode.INVALID_REFRESH_TOKEN));
        LocalDateTime now = LocalDateTime.now(clock);

        if (stored.isRevoked()) {
            // 이미 쓰인/로그아웃된 토큰의 재사용 = 탈취 가능성. 잠시 전에 회전된 경우가 아니면 이 사용자의 모든 세션을 끊는다.
            if (stored.getRevokedAt().isBefore(now.minusSeconds(REUSE_GRACE_SECONDS))) {
                refreshTokenRepository.revokeAllByUserId(stored.getUserId(), now);
            }
            throw new BusinessException(ErrorCode.INVALID_REFRESH_TOKEN);
        }
        if (stored.isExpired(now)) {
            throw new BusinessException(ErrorCode.INVALID_REFRESH_TOKEN);
        }
        User user = userRepository.findById(stored.getUserId())
                .orElseThrow(() -> new BusinessException(ErrorCode.INVALID_REFRESH_TOKEN));
        if (user.getStatus() != UserStatus.ACTIVE) {
            stored.revoke(now);
            throw new BusinessException(ErrorCode.INVALID_REFRESH_TOKEN);
        }
        stored.revoke(now);
        return issueTokens(user);
    }

    /** 현재 기기(이 쿠키의 토큰)만 폐기한다. 이미 만료/폐기/없는 토큰이어도 성공으로 취급한다(멱등). */
    @Transactional
    public void logout(String rawRefreshToken) {
        if (rawRefreshToken == null || rawRefreshToken.isBlank()) {
            return;
        }
        refreshTokenRepository.findByTokenHashForUpdate(Hashing.sha256Hex(rawRefreshToken))
                .ifPresent(t -> t.revoke(LocalDateTime.now(clock)));
    }

    /** 인증을 마친 사용자에게 액세스 토큰과 리프레시 토큰을 발급한다. (소셜 로그인도 여기로 모인다) */
    public IssuedTokens issueTokens(User user) {
        String access = jwtProvider.createAccessToken(user.getId(), user.getRole().name());
        String rawRefresh = Hashing.randomToken();
        LocalDateTime expiresAt = LocalDateTime.now(clock).plusDays(jwtProperties.refreshTokenDays());
        refreshTokenRepository.save(RefreshToken.issue(user.getId(), Hashing.sha256Hex(rawRefresh), expiresAt));
        return new IssuedTokens(access, jwtProvider.accessTtlSeconds(), rawRefresh, user.isAutoLogin());
    }

    private static String normalizeEmail(String email) {
        return email.trim().toLowerCase(Locale.ROOT);
    }
}
