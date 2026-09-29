package com.godlife.backend.challenge;

import jakarta.persistence.Column;
import jakarta.persistence.Entity;
import jakarta.persistence.EnumType;
import jakarta.persistence.Enumerated;
import jakarta.persistence.GeneratedValue;
import jakarta.persistence.GenerationType;
import jakarta.persistence.Id;
import jakarta.persistence.Table;
import lombok.AccessLevel;
import lombok.Getter;
import lombok.NoArgsConstructor;
import org.hibernate.annotations.Generated;
import org.hibernate.generator.EventType;

import java.time.LocalDateTime;

/** (challenge_id, user_id) 는 유니크. 취소 후 다시 참여하면 같은 행을 되살린다. */
@Getter
@NoArgsConstructor(access = AccessLevel.PROTECTED)
@Entity
@Table(name = "challenge_participants")
public class ChallengeParticipant {

    @Id
    @GeneratedValue(strategy = GenerationType.IDENTITY)
    private Long id;

    @Column(name = "challenge_id", nullable = false, updatable = false)
    private Long challengeId;

    @Column(name = "user_id", nullable = false, updatable = false)
    private Long userId;

    /** 참가 시점 예치 포인트 스냅샷. 무료 챌린지는 0. */
    @Column(name = "deposit_amount", nullable = false)
    private long depositAmount;

    @Enumerated(EnumType.STRING)
    @Column(nullable = false)
    private ParticipantStatus status;

    @Column(name = "success_days", nullable = false)
    private int successDays;

    @Column(name = "current_streak", nullable = false)
    private int currentStreak;

    @Column(name = "max_streak", nullable = false)
    private int maxStreak;

    @Generated(event = EventType.INSERT)
    @Column(name = "joined_at", insertable = false, updatable = false)
    private LocalDateTime joinedAt;

    public static ChallengeParticipant join(Long challengeId, Long userId, long depositAmount) {
        ChallengeParticipant p = new ChallengeParticipant();
        p.challengeId = challengeId;
        p.userId = userId;
        p.depositAmount = depositAmount;
        p.status = ParticipantStatus.ACTIVE;
        return p;
    }

    public boolean isActive() {
        return status == ParticipantStatus.ACTIVE;
    }

    public boolean isKicked() {
        return status == ParticipantStatus.KICKED;
    }

    void kick() {
        status = ParticipantStatus.KICKED;
    }

    void leave() {
        status = ParticipantStatus.LEFT;
    }

    void rejoin(long depositAmount) {
        this.depositAmount = depositAmount;
        status = ParticipantStatus.ACTIVE;
    }
}
