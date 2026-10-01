package com.godlife.backend.phone;

import com.godlife.backend.common.crypto.Hashing;
import com.godlife.backend.common.error.BusinessException;
import com.godlife.backend.common.error.ErrorCode;
import com.godlife.backend.common.notify.SmsSender;
import com.godlife.backend.common.ratelimit.RequestThrottle;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.time.Clock;
import java.time.Duration;
import java.time.LocalDateTime;

/**
 * 휴대폰 SMS 인증: 인증번호 발송 → 확인 → 1회용 증표(proof) 발급 → 가입/아이디 찾기/번호 등록에서 증표 사용.
 * 증표를 쓰는 쪽은 {@link #consumeProof} 를 자기 트랜잭션 안에서 호출해, 실패하면 증표 사용도 함께 롤백되게 한다.
 */
@Service
public class PhoneVerificationService {

    static final Duration CODE_TTL = Duration.ofMinutes(3);
    static final Duration RESEND_COOLDOWN = Duration.ofSeconds(10);
    /** 같은 번호는 LIMIT_WINDOW 동안 MAX_SENDS_PER_WINDOW 번까지, 같은 IP 는 LIMIT_WINDOW 동안 ipLimit 번까지. */
    static final Duration LIMIT_WINDOW = Duration.ofMinutes(10);
    static final Duration PROOF_TTL = Duration.ofMinutes(15);
    static final int MAX_ATTEMPTS = 5;
    static final int MAX_SENDS_PER_WINDOW = 20;

    private final PhoneVerificationRepository repository;
    private final PhoneHasher phoneHasher;
    private final PhoneCipher phoneCipher;
    private final SmsSender smsSender;
    private final RequestThrottle throttle;
    private final Clock clock;
    private final int ipLimit;

    public PhoneVerificationService(PhoneVerificationRepository repository, PhoneHasher phoneHasher,
                                    PhoneCipher phoneCipher, SmsSender smsSender, RequestThrottle throttle, Clock clock,
                                    @Value("${app.sms.ip-limit}") int ipLimit) {
        this.repository = repository;
        this.phoneHasher = phoneHasher;
        this.phoneCipher = phoneCipher;
        this.smsSender = smsSender;
        this.throttle = throttle;
        this.clock = clock;
        this.ipLimit = ipLimit;
    }

    /**
     * 인증번호를 보낸다. 데모 모드(실제 발송 안 함)면 인증번호를 돌려주고, 실제 발송이면 null.
     * 같은 번호/IP 로 인증을 마구 시도하지 못하게 번호당·IP당 횟수를 제한한다.
     */
    @Transactional
    public String send(String phone, String clientIp) {
        throttle.check("sms:" + clientIp, ipLimit, LIMIT_WINDOW);
        String phoneHash = phoneHasher.hash(phone);
        LocalDateTime now = LocalDateTime.now(clock);

        // 발송 시각 컬럼이 없으므로 만료 시각에서 거꾸로 계산한다. (만료 = 발송 + CODE_TTL)
        repository.findFirstByPhoneHashOrderByIdDesc(phoneHash).ifPresent(last -> {
            LocalDateTime sentAt = last.getExpiresAt().minus(CODE_TTL);
            Duration wait = Duration.between(now, sentAt.plus(RESEND_COOLDOWN));
            if (!wait.isNegative() && !wait.isZero()) {
                throw new BusinessException(ErrorCode.TOO_MANY_REQUESTS,
                        "인증번호는 " + BusinessException.after(wait) + " 다시 받을 수 있어요.");
            }
        });
        if (repository.countByPhoneHashAndExpiresAtAfter(phoneHash, now.minus(LIMIT_WINDOW).plus(CODE_TTL))
                >= MAX_SENDS_PER_WINDOW) {
            throw new BusinessException(ErrorCode.TOO_MANY_REQUESTS,
                    "이 번호로 인증번호를 너무 많이 받았어요. 10분 안에 다시 시도해 주세요.");
        }

        String code = Hashing.randomDigits(6);
        repository.save(PhoneVerification.issue(phoneHash, phoneCipher.encrypt(phone), Hashing.sha256Hex(code),
                now.plus(CODE_TTL)));
        smsSender.send(PhoneHasher.normalize(phone), "[갓생살기] 인증번호 " + code + " (3분 안에 입력해 주세요)");
        return smsSender.isDemo() ? code : null;
    }

    /** 인증번호가 맞으면 증표 원문을 돌려준다. 틀린 횟수는 롤백되지 않고 남아야 하므로 BusinessException 에서 커밋한다. */
    @Transactional(noRollbackFor = BusinessException.class)
    public String confirm(String phone, String code) {
        LocalDateTime now = LocalDateTime.now(clock);
        PhoneVerification v = repository.findFirstByPhoneHashOrderByIdDesc(phoneHasher.hash(phone))
                .filter(x -> !x.isVerified() && !x.isExpired(now))
                .orElseThrow(() -> new BusinessException(ErrorCode.INVALID_VERIFICATION_CODE));
        if (v.getAttemptCount() >= MAX_ATTEMPTS) {
            throw new BusinessException(ErrorCode.VERIFICATION_ATTEMPTS_EXCEEDED);
        }
        if (!Hashing.equalsConstantTime(v.getCodeHash(), Hashing.sha256Hex(code))) {
            v.recordFailedAttempt();
            throw new BusinessException(v.getAttemptCount() >= MAX_ATTEMPTS
                    ? ErrorCode.VERIFICATION_ATTEMPTS_EXCEEDED
                    : ErrorCode.INVALID_VERIFICATION_CODE);
        }
        String proof = Hashing.randomToken();
        v.markVerified(now, Hashing.sha256Hex(proof));
        return proof;
    }

    /**
     * 증표를 한 번 사용 처리하고 인증 기록을 돌려준다. 호출자의 트랜잭션에 참여하므로
     * 호출자가 실패하면 증표 사용도 롤백된다. 인증된 번호는 {@link PhoneVerification#getPhoneHash()}.
     */
    @Transactional
    public PhoneVerification consumeProof(String proof) {
        LocalDateTime now = LocalDateTime.now(clock);
        PhoneVerification v = (proof == null || proof.isBlank() ? null
                : repository.findByProofHash(Hashing.sha256Hex(proof)).orElse(null));
        if (v == null || v.getProofUsedAt() != null || v.getVerifiedAt().plus(PROOF_TTL).isBefore(now)) {
            throw new BusinessException(ErrorCode.PHONE_VERIFICATION_REQUIRED);
        }
        v.useProof(now);
        return v;
    }
}
