package com.godlife.backend.challenge;

import jakarta.persistence.Column;
import jakarta.persistence.Entity;
import jakarta.persistence.EnumType;
import jakarta.persistence.Enumerated;
import jakarta.persistence.FetchType;
import jakarta.persistence.GeneratedValue;
import jakarta.persistence.GenerationType;
import jakarta.persistence.Id;
import jakarta.persistence.JoinColumn;
import jakarta.persistence.ManyToOne;
import jakarta.persistence.Table;
import lombok.AccessLevel;
import lombok.Getter;
import lombok.NoArgsConstructor;
import org.hibernate.annotations.Generated;
import org.hibernate.generator.EventType;

import java.time.LocalDate;
import java.time.LocalDateTime;
import java.time.LocalTime;
import java.time.temporal.ChronoUnit;

@Getter
@NoArgsConstructor(access = AccessLevel.PROTECTED)
@Entity
@Table(name = "challenges")
public class Challenge {

    @Id
    @GeneratedValue(strategy = GenerationType.IDENTITY)
    private Long id;

    @Column(name = "host_id", nullable = false, updatable = false)
    private Long hostId;

    @ManyToOne(fetch = FetchType.LAZY, optional = false)
    @JoinColumn(name = "category_id", nullable = false)
    private Category category;

    @Column(nullable = false)
    private String title;

    @Column(nullable = false)
    private String description;

    @Enumerated(EnumType.STRING)
    @Column(nullable = false, updatable = false)
    private ChallengeMode mode;

    @Column(name = "start_date", nullable = false)
    private LocalDate startDate;

    @Column(name = "end_date", nullable = false)
    private LocalDate endDate;

    @Enumerated(EnumType.STRING)
    @Column(name = "frequency_type", nullable = false)
    private FrequencyType frequencyType;

    /** WEEKLY_N 일 때만 값이 있다. */
    @Column(name = "weekly_count")
    private Integer weeklyCount;

    /** 참가 포인트. FREE 는 0. */
    @Column(name = "entry_fee", nullable = false)
    private long entryFee;

    /**
     * 1회 최소/최대 예치 포인트. 지금은 참가 포인트를 고정으로 받으므로 entryFee 와 같게 둔다.
     * (참가자가 금액을 고르는 방식으로 바꿀 때 이 두 값을 따로 받는다)
     */
    @Column(name = "min_bet", nullable = false)
    private long minBet;

    @Column(name = "max_bet", nullable = false)
    private long maxBet;

    @Column(name = "max_participants", nullable = false)
    private int maxParticipants;

    /** 인기순 정렬용 비정규화 카운터. 참여/취소 시 챌린지 행을 잠근 채로 바꾼다. */
    @Column(name = "participant_count", nullable = false)
    private int participantCount;

    /** 인증 가능 시간대(옵션). 둘 다 있거나 둘 다 없다. */
    @Column(name = "verify_from")
    private LocalTime verifyFrom;

    @Column(name = "verify_until")
    private LocalTime verifyUntil;

    /** 부분 성공 시 비례 환급 (포인트 챌린지만) */
    @Column(name = "partial_refund", nullable = false)
    private boolean partialRefund;

    @Enumerated(EnumType.STRING)
    @Column(nullable = false)
    private ChallengeStatus status;

    @Generated(event = EventType.INSERT)
    @Column(name = "created_at", insertable = false, updatable = false)
    private LocalDateTime createdAt;

    public static Challenge create(Long hostId, Category category, String title, String description,
                                   ChallengeMode mode, LocalDate startDate, LocalDate endDate,
                                   FrequencyType frequencyType, Integer weeklyCount, long entryFee,
                                   int maxParticipants, LocalTime verifyFrom, LocalTime verifyUntil,
                                   boolean partialRefund) {
        Challenge c = new Challenge();
        c.hostId = hostId;
        c.category = category;
        c.title = title;
        c.description = description;
        c.mode = mode;
        c.startDate = startDate;
        c.endDate = endDate;
        c.frequencyType = frequencyType;
        c.weeklyCount = frequencyType == FrequencyType.WEEKLY_N ? weeklyCount : null;
        boolean bet = mode == ChallengeMode.BET;
        c.entryFee = bet ? entryFee : 0;
        c.minBet = c.entryFee;
        c.maxBet = c.entryFee;
        c.partialRefund = bet && partialRefund;
        c.maxParticipants = maxParticipants;
        c.verifyFrom = verifyFrom;
        c.verifyUntil = verifyUntil;
        c.status = ChallengeStatus.RECRUITING;
        return c;
    }

    /** 시작일 당일까지 참여할 수 있다. */
    public boolean isRecruiting(LocalDate today) {
        return status == ChallengeStatus.RECRUITING && !today.isAfter(startDate);
    }

    /** 시작일 전날까지만 참여를 취소할 수 있다. */
    public boolean canLeave(LocalDate today) {
        return status == ChallengeStatus.RECRUITING && today.isBefore(startDate);
    }

    public boolean isFull() {
        return participantCount >= maxParticipants;
    }

    public long totalDays() {
        return ChronoUnit.DAYS.between(startDate, endDate) + 1;
    }

    void addParticipant() {
        participantCount++;
    }

    void removeParticipant() {
        participantCount--;
    }
}
