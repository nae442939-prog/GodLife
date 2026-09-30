package com.godlife.backend.settlement;

import jakarta.persistence.Column;
import jakarta.persistence.Entity;
import jakarta.persistence.GeneratedValue;
import jakarta.persistence.GenerationType;
import jakarta.persistence.Id;
import jakarta.persistence.Table;
import lombok.AccessLevel;
import lombok.Getter;
import lombok.NoArgsConstructor;

import java.time.LocalDate;
import java.time.LocalDateTime;

/** 포인트 챌린지 하루(주 N회는 한 주) 정산 기록. (challenge_id, period_start) 유니크라 기간마다 한 번만 정산한다. */
@Getter
@NoArgsConstructor(access = AccessLevel.PROTECTED)
@Entity
@Table(name = "daily_settlements")
public class DailySettlement {

    @Id
    @GeneratedValue(strategy = GenerationType.IDENTITY)
    private Long id;

    @Column(name = "challenge_id", nullable = false, updatable = false)
    private Long challengeId;

    @Column(name = "period_start", nullable = false, updatable = false)
    private LocalDate periodStart;

    @Column(name = "period_end", nullable = false, updatable = false)
    private LocalDate periodEnd;

    @Column(name = "success_count", nullable = false)
    private int successCount;

    @Column(name = "fail_count", nullable = false)
    private int failCount;

    @Column(name = "forfeited_pool", nullable = false)
    private long forfeitedPool;

    @Column(name = "reward_share", nullable = false)
    private long rewardShare;

    @Column(nullable = false)
    private long distributed;

    @Column(name = "settled_at", nullable = false)
    private LocalDateTime settledAt;

    static DailySettlement of(Long challengeId, Period period, int successCount, int failCount, long forfeitedPool,
                              long rewardShare, LocalDateTime now) {
        DailySettlement d = new DailySettlement();
        d.challengeId = challengeId;
        d.periodStart = period.start();
        d.periodEnd = period.end();
        d.successCount = successCount;
        d.failCount = failCount;
        d.forfeitedPool = forfeitedPool;
        d.rewardShare = rewardShare;
        d.distributed = rewardShare * successCount;
        d.settledAt = now;
        return d;
    }
}
