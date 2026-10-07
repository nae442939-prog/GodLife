package com.godlife.backend.settlement;

import org.springframework.data.jpa.repository.JpaRepository;

import java.util.Optional;

public interface SettlementItemRepository extends JpaRepository<SettlementItem, Long> {

    Optional<SettlementItem> findBySettlementIdAndParticipantId(Long settlementId, Long participantId);
}
