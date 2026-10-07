package com.godlife.backend.account;

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

/**
 * 비밀번호 재설정 1건: 메일로 보낸 인증번호(code_hash) → 확인되면 재설정 토큰(token_hash) 발급 → 새 비밀번호 저장 시 used_at.
 */
@Getter
@NoArgsConstructor(access = AccessLevel.PROTECTED)
@Entity
@Table(name = "password_reset_tokens")
public class PasswordResetToken {

    @Id
    @GeneratedValue(strategy = GenerationType.IDENTITY)
    private Long id;

    @Column(name = "user_id", nullable = false, updatable = false)
    private Long userId;

    @Column(name = "code_hash", nullable = false, updatable = false)
    private String codeHash;

    @Column(name = "attempt_count", nullable = false)
    private int attemptCount;

    @Column(name = "token_hash")
    private String tokenHash;

    @Column(name = "expires_at", nullable = false, updatable = false)
    private LocalDateTime expiresAt;

    @Column(name = "used_at")
    private LocalDateTime usedAt;

    public static PasswordResetToken issue(Long userId, String codeHash, LocalDateTime expiresAt) {
        PasswordResetToken t = new PasswordResetToken();
        t.userId = userId;
        t.codeHash = codeHash;
        t.expiresAt = expiresAt;
        return t;
    }

    /** 아직 인증번호 확인 전이고, 쓰이지 않았고, 만료되지 않음. */
    public boolean isAwaitingCode(LocalDateTime now) {
        return tokenHash == null && usedAt == null && expiresAt.isAfter(now);
    }

    public boolean isUsable(LocalDateTime now) {
        return tokenHash != null && usedAt == null && expiresAt.isAfter(now);
    }

    public void recordFailedAttempt() {
        attemptCount++;
    }

    public void markCodeVerified(String tokenHash) {
        this.tokenHash = tokenHash;
    }

    public void markUsed(LocalDateTime now) {
        this.usedAt = now;
    }
}
