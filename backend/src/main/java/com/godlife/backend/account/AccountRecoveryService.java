package com.godlife.backend.account;

import com.godlife.backend.account.dto.FindIdResponse;
import com.godlife.backend.auth.RefreshTokenRepository;
import com.godlife.backend.auth.social.SocialAccount;
import com.godlife.backend.auth.social.SocialAccountRepository;
import com.godlife.backend.common.crypto.Hashing;
import com.godlife.backend.common.error.BusinessException;
import com.godlife.backend.common.error.ErrorCode;
import com.godlife.backend.common.notify.EmailSender;
import com.godlife.backend.common.ratelimit.RequestThrottle;
import com.godlife.backend.phone.PhoneVerification;
import com.godlife.backend.phone.PhoneVerificationService;
import com.godlife.backend.user.User;
import com.godlife.backend.user.UserRepository;
import com.godlife.backend.user.UserStatus;
import lombok.extern.slf4j.Slf4j;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.security.crypto.password.PasswordEncoder;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.time.Clock;
import java.time.Duration;
import java.time.LocalDateTime;
import java.util.List;
import java.util.Locale;

/**
 * 아이디 찾기(휴대폰 인증) / 비밀번호 찾기(이메일 인증번호 → 새 비밀번호).
 * 비밀번호 찾기는 가입 여부와 관계없이 같은 응답을 줘서, 이메일로 가입 여부를 알아낼 수 없게 한다.
 */
@Slf4j
@Service
public class AccountRecoveryService {

    static final Duration RESET_TTL = Duration.ofMinutes(10);
    static final Duration RESEND_COOLDOWN = Duration.ofSeconds(60);
    static final int MAX_ATTEMPTS = 5;
    static final String VIRTUAL_EMAIL_SUFFIX = "@social.godlife.local";

    private final UserRepository userRepository;
    private final SocialAccountRepository socialAccountRepository;
    private final PasswordResetTokenRepository resetTokenRepository;
    private final RefreshTokenRepository refreshTokenRepository;
    private final PhoneVerificationService phoneVerificationService;
    private final PasswordEncoder passwordEncoder;
    private final EmailSender emailSender;
    private final RequestThrottle throttle;
    private final Clock clock;
    private final int mailIpLimit;

    public AccountRecoveryService(UserRepository userRepository, SocialAccountRepository socialAccountRepository,
                                  PasswordResetTokenRepository resetTokenRepository,
                                  RefreshTokenRepository refreshTokenRepository,
                                  PhoneVerificationService phoneVerificationService, PasswordEncoder passwordEncoder,
                                  EmailSender emailSender, RequestThrottle throttle, Clock clock,
                                  @Value("${app.mail.ip-limit}") int mailIpLimit) {
        this.userRepository = userRepository;
        this.socialAccountRepository = socialAccountRepository;
        this.resetTokenRepository = resetTokenRepository;
        this.refreshTokenRepository = refreshTokenRepository;
        this.phoneVerificationService = phoneVerificationService;
        this.passwordEncoder = passwordEncoder;
        this.emailSender = emailSender;
        this.throttle = throttle;
        this.clock = clock;
        this.mailIpLimit = mailIpLimit;
    }

    // ---------- 아이디 찾기 ----------

    @Transactional
    public FindIdResponse findId(String phoneProof) {
        PhoneVerification phone = phoneVerificationService.consumeProof(phoneProof);
        User user = userRepository.findByPhoneHash(phone.getPhoneHash())
                .filter(u -> u.getStatus() != UserStatus.WITHDRAWN)
                .orElseThrow(() -> new BusinessException(ErrorCode.ACCOUNT_NOT_FOUND));
        List<String> providers = socialAccountRepository.findByUserId(user.getId()).stream()
                .map(SocialAccount::getProvider).map(Enum::name).sorted().toList();
        String email = user.getEmail().endsWith(VIRTUAL_EMAIL_SUFFIX) ? null : maskEmail(user.getEmail());
        return new FindIdResponse(email, user.getPasswordHash() != null, providers, user.getCreatedAt());
    }

    /** tester@example.com → te****@example.com (앞 2글자만 보여준다. 2글자 이하면 1글자) */
    static String maskEmail(String email) {
        int at = email.indexOf('@');
        String local = email.substring(0, at);
        int visible = local.length() <= 2 ? 1 : 2;
        return local.substring(0, visible) + "*".repeat(Math.max(3, local.length() - visible)) + email.substring(at);
    }

    // ---------- 비밀번호 찾기 ----------

    /**
     * 비밀번호로 가입한 활성 회원이면 인증번호를 보낸다. 아니면 아무것도 하지 않는다(응답은 같다).
     * 데모 모드(실제 발송 안 함)면 인증번호를 돌려준다. 데모에서는 이 차이로 가입 여부가 드러나지만
     * 시연용 모드라 감수한다. 실제 발송 모드에서는 항상 null 이라 구분할 수 없다.
     */
    @Transactional
    public String requestPasswordReset(String rawEmail, String clientIp) {
        throttle.check("mail:" + clientIp, mailIpLimit, Duration.ofMinutes(10));
        LocalDateTime now = LocalDateTime.now(clock);
        User user = userRepository.findByEmail(normalizeEmail(rawEmail))
                .filter(u -> u.getPasswordHash() != null && u.getStatus() == UserStatus.ACTIVE)
                .orElse(null);
        if (user == null) {
            return null;
        }
        boolean tooSoon = resetTokenRepository.findFirstByUserIdOrderByIdDesc(user.getId())
                .map(last -> last.getExpiresAt().isAfter(now.plus(RESET_TTL).minus(RESEND_COOLDOWN)))
                .orElse(false);
        if (tooSoon) {
            // 연속 요청으로 메일 폭탄을 보내지 못하게 조용히 무시한다.
            return null;
        }
        String code = Hashing.randomDigits(6);
        resetTokenRepository.save(PasswordResetToken.issue(user.getId(), Hashing.sha256Hex(code), now.plus(RESET_TTL)));
        try {
            emailSender.send(user.getEmail(), "[갓생살기] 비밀번호 재설정 인증번호",
                    "인증번호 " + code + " 를 10분 안에 입력해 주세요. 요청하지 않았다면 이 메일을 무시하세요.");
        } catch (RuntimeException e) {
            // 실패를 응답으로 알리면 가입된 이메일이라는 사실이 드러나므로 로그만 남긴다.
            log.error("비밀번호 재설정 메일 발송 실패: userId={}", user.getId(), e);
            return null;
        }
        return emailSender.isDemo() ? code : null;
    }

    /** 인증번호가 맞으면 재설정 토큰 원문을 돌려준다. 틀린 횟수는 롤백되지 않고 남아야 한다. */
    @Transactional(noRollbackFor = BusinessException.class)
    public String confirmPasswordReset(String rawEmail, String code) {
        LocalDateTime now = LocalDateTime.now(clock);
        PasswordResetToken token = userRepository.findByEmail(normalizeEmail(rawEmail))
                .flatMap(u -> resetTokenRepository.findFirstByUserIdOrderByIdDesc(u.getId()))
                .filter(t -> t.isAwaitingCode(now))
                .orElseThrow(() -> new BusinessException(ErrorCode.INVALID_VERIFICATION_CODE));
        if (token.getAttemptCount() >= MAX_ATTEMPTS) {
            throw new BusinessException(ErrorCode.VERIFICATION_ATTEMPTS_EXCEEDED);
        }
        if (!Hashing.equalsConstantTime(token.getCodeHash(), Hashing.sha256Hex(code))) {
            token.recordFailedAttempt();
            throw new BusinessException(token.getAttemptCount() >= MAX_ATTEMPTS
                    ? ErrorCode.VERIFICATION_ATTEMPTS_EXCEEDED
                    : ErrorCode.INVALID_VERIFICATION_CODE);
        }
        String resetToken = Hashing.randomToken();
        token.markCodeVerified(Hashing.sha256Hex(resetToken));
        return resetToken;
    }

    /** 새 비밀번호 저장 후 모든 기기의 로그인을 끊는다. (탈취된 세션이 있다면 함께 무효화) */
    @Transactional
    public void completePasswordReset(String resetToken, String newPassword) {
        LocalDateTime now = LocalDateTime.now(clock);
        PasswordResetToken token = (resetToken == null || resetToken.isBlank() ? null
                : resetTokenRepository.findByTokenHash(Hashing.sha256Hex(resetToken)).orElse(null));
        if (token == null || !token.isUsable(now)) {
            throw new BusinessException(ErrorCode.INVALID_RESET_TOKEN);
        }
        User user = userRepository.findById(token.getUserId())
                .filter(u -> u.getStatus() == UserStatus.ACTIVE)
                .orElseThrow(() -> new BusinessException(ErrorCode.INVALID_RESET_TOKEN));
        user.changePassword(passwordEncoder.encode(newPassword));
        token.markUsed(now);
        refreshTokenRepository.revokeAllByUserId(user.getId(), now);
        log.info("비밀번호 재설정 완료: userId={}", user.getId());
    }

    private static String normalizeEmail(String email) {
        return email.trim().toLowerCase(Locale.ROOT);
    }
}
