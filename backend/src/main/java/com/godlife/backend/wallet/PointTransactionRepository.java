package com.godlife.backend.wallet;

import org.springframework.data.domain.Page;
import org.springframework.data.domain.Pageable;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.repository.query.Param;

import java.time.LocalDateTime;
import java.util.Collection;
import java.util.List;

public interface PointTransactionRepository extends JpaRepository<PointTransaction, Long> {

    boolean existsByIdempotencyKey(String idempotencyKey);

    Page<PointTransaction> findByWalletIdOrderByIdDesc(Long walletId, Pageable pageable);

    /** from 이후 이 종류 거래 금액 합 (테스트 충전 하루 한도) */
    @Query("""
            SELECT COALESCE(SUM(t.amount), 0) FROM PointTransaction t
            WHERE t.walletId = :walletId AND t.type = :type AND t.createdAt >= :from
            """)
    long sumSince(@Param("walletId") Long walletId, @Param("type") PointTxType type,
                  @Param("from") LocalDateTime from);

    /**
     * from 이후 챌린지에 건 포인트 (참가비 - 취소·삭제·강퇴로 돌려받은 것). 베팅 한도 계산용.
     * 참가비는 음수, 환급은 양수로 쌓이므로 부호를 뒤집는다.
     */
    @Query("""
            SELECT COALESCE(-SUM(t.amount), 0) FROM PointTransaction t
            WHERE t.walletId = :walletId AND t.createdAt >= :from
              AND t.refType = 'participant'
              AND t.type IN (com.godlife.backend.wallet.PointTxType.ENTRY_FEE,
                             com.godlife.backend.wallet.PointTxType.REFUND)
            """)
    long betSince(@Param("walletId") Long walletId, @Param("from") LocalDateTime from);

    /** 이 참가 기록에 쌓인 이 종류 거래 수 (멱등 키 순번: 취소 후 다시 참여해도 키가 겹치지 않게) */
    long countByRefTypeAndRefIdAndType(String refType, Long refId, PointTxType type);

    /** 거래 내역에 챌린지 이름을 붙이려고: 참가 기록 id → [참가 기록 id, 챌린지 제목] */
    @Query("""
            SELECT p.id, c.title FROM com.godlife.backend.challenge.ChallengeParticipant p
              JOIN com.godlife.backend.challenge.Challenge c ON c.id = p.challengeId
            WHERE p.id IN :participantIds
            """)
    List<Object[]> findChallengeTitles(@Param("participantIds") Collection<Long> participantIds);
}
