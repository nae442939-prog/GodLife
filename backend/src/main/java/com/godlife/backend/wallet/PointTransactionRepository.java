package com.godlife.backend.wallet;

import org.springframework.data.domain.Page;
import org.springframework.data.domain.Pageable;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.repository.query.Param;

import java.time.LocalDateTime;
import java.util.Collection;
import java.util.List;
import java.util.Optional;

public interface PointTransactionRepository extends JpaRepository<PointTransaction, Long> {

    boolean existsByIdempotencyKey(String idempotencyKey);

    boolean existsByIdempotencyKeyStartingWith(String prefix);

    Optional<PointTransaction> findByIdempotencyKey(String idempotencyKey);

    Page<PointTransaction> findByWalletIdOrderByIdDesc(Long walletId, Pageable pageable);

    /**
     * from 이후 결제로 충전한 금액 합 (충전 하루 한도). 결제를 가리키는 충전만 센다 —
     * 결제 연동 전에 있던 테스트 충전(가상 지급)은 결제가 아니라서 한도에 넣지 않는다.
     */
    @Query("""
            SELECT COALESCE(SUM(t.amount), 0) FROM PointTransaction t
            WHERE t.walletId = :walletId AND t.createdAt >= :from
              AND t.type = com.godlife.backend.wallet.PointTxType.CHARGE AND t.refType = 'payment'
            """)
    long chargedSince(@Param("walletId") Long walletId, @Param("from") LocalDateTime from);

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

    /** 거래 내역에 챌린지 이름을 붙이려고: 최종 정산 id → [정산 id, 챌린지 제목] */
    @Query("""
            SELECT s.id, c.title FROM com.godlife.backend.settlement.Settlement s
              JOIN com.godlife.backend.challenge.Challenge c ON c.id = s.challengeId
            WHERE s.id IN :settlementIds
            """)
    List<Object[]> findSettlementTitles(@Param("settlementIds") Collection<Long> settlementIds);

    /** 거래 내역에 주문한 상품을 붙이려고: 상점 주문 id → [주문 id, 첫 상품 이름, 상품 가짓수] */
    @Query(value = """
            SELECT o.id, p.name, (SELECT COUNT(*) FROM order_items x WHERE x.order_id = o.id)
            FROM orders o
              JOIN order_items i ON i.id = (SELECT MIN(f.id) FROM order_items f WHERE f.order_id = o.id)
              JOIN products p ON p.id = i.product_id
            WHERE o.id IN (:orderIds)
            """, nativeQuery = true)
    List<Object[]> findOrderTitles(@Param("orderIds") Collection<Long> orderIds);
}
