package com.godlife.backend.settlement;

import org.springframework.data.jpa.repository.JpaRepository;

import java.time.LocalDate;
import java.util.List;
import java.util.Optional;

public interface DailySettlementRepository extends JpaRepository<DailySettlement, Long> {

    boolean existsByChallengeIdAndPeriodStart(Long challengeId, LocalDate periodStart);

    Optional<DailySettlement> findByChallengeIdAndPeriodStart(Long challengeId, LocalDate periodStart);

    List<DailySettlement> findByChallengeId(Long challengeId);
}
