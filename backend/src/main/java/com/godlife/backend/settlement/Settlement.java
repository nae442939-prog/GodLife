package com.godlife.backend.settlement;

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

import java.time.LocalDateTime;

/**
 * 챌린지 최종 정산 (챌린지당 한 번, challenge_id 유니크).
 * 매일 결과(DailySettlement)를 모두 더해 끝날 때 한 번에 환급·보상을 지급한다.
 */
@Getter
@NoArgsConstructor(access = AccessLevel.PROTECTED)
@Entity
@Table(name = "settlements")
public class Settlement {

    public enum Status {
        PENDING, DONE
    }

    @Id
    @GeneratedValue(strategy = GenerationType.IDENTITY)
    private Long id;

    @Column(name = "challenge_id", nullable = false, updatable = false)
    private Long challengeId;

    @Enumerated(EnumType.STRING)
    @Column(nullable = false)
    private Status status;

    /** 전체 참가 포인트 합 */
    @Column(name = "total_pool", nullable = false)
    private long totalPool;

    /** 못 한 날들에 잃은 포인트 합 (보상 재원) */
    @Column(name = "forfeited_pool", nullable = false)
    private long forfeitedPool;

    /** 실제로 나눠 준 보상 합 */
    @Column(nullable = false)
    private long distributed;

    /** 한 기간 보상 상한 (= 한 사람의 그 기간 몫). 기간마다 같으면 그 값 */
    @Column(name = "reward_cap", nullable = false)
    private long rewardCap;

    @Column(name = "settled_at")
    private LocalDateTime settledAt;

    static Settlement start(Long challengeId, long totalPool, long forfeitedPool, long distributed, long rewardCap) {
        Settlement s = new Settlement();
        s.challengeId = challengeId;
        s.status = Status.PENDING;
        s.totalPool = totalPool;
        s.forfeitedPool = forfeitedPool;
        s.distributed = distributed;
        s.rewardCap = rewardCap;
        return s;
    }

    void done(LocalDateTime now) {
        status = Status.DONE;
        settledAt = now;
    }
}
