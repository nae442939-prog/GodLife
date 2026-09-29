package com.godlife.backend.phone;

import jakarta.persistence.Column;
import jakarta.persistence.Entity;
import jakarta.persistence.GeneratedValue;
import jakarta.persistence.GenerationType;
import jakarta.persistence.Id;
import jakarta.persistence.Table;
import lombok.AccessLevel;
import lombok.Getter;
import lombok.NoArgsConstructor;

import java.time.LocalDateTime;

/** SMS 인증 1건. 번호/인증번호/증표 모두 원문이 아니라 해시로만 저장한다. */
@Getter
@NoArgsConstructor(access = AccessLevel.PROTECTED)
@Entity
@Table(name = "phone_verifications")
public class PhoneVerification {

    @Id
    @GeneratedValue(strategy = GenerationType.IDENTITY)
    private Long id;

    @Column(name = "phone_hash", nullable = false, updatable = false)
    private String phoneHash;

    @Column(name = "code_hash", nullable = false, updatable = false)
    private String codeHash;

    @Column(name = "attempt_count", nullable = false)
    private int attemptCount;

    @Column(name = "expires_at", nullable = false, updatable = false)
    private LocalDateTime expiresAt;

    @Column(name = "verified_at")
    private LocalDateTime verifiedAt;

    @Column(name = "proof_hash")
    private String proofHash;

    @Column(name = "proof_used_at")
    private LocalDateTime proofUsedAt;

    @Column(name = "user_id")
    private Long userId;

    public static PhoneVerification issue(String phoneHash, String codeHash, LocalDateTime expiresAt) {
        PhoneVerification v = new PhoneVerification();
        v.phoneHash = phoneHash;
        v.codeHash = codeHash;
        v.expiresAt = expiresAt;
        return v;
    }

    public boolean isExpired(LocalDateTime now) {
        return !expiresAt.isAfter(now);
    }

    public boolean isVerified() {
        return verifiedAt != null;
    }

    public void recordFailedAttempt() {
        attemptCount++;
    }

    public void markVerified(LocalDateTime now, String proofHash) {
        this.verifiedAt = now;
        this.proofHash = proofHash;
    }

    public void useProof(LocalDateTime now) {
        this.proofUsedAt = now;
    }

    /** 이 인증으로 번호를 등록한 회원. */
    public void linkUser(Long userId) {
        this.userId = userId;
    }
}
