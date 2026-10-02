package com.godlife.backend.wallet;

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
import org.hibernate.annotations.Immutable;

import java.time.LocalDateTime;

/** 거래 원장 한 줄. 쓰기만 하고 고치거나 지우지 않는다. idempotency_key 가 유니크라 같은 요청은 한 번만 들어간다. */
@Getter
@Immutable
@NoArgsConstructor(access = AccessLevel.PROTECTED)
@Entity
@Table(name = "point_transactions")
public class PointTransaction {

    /** ref_type 값 */
    public static final String REF_PARTICIPANT = "participant";
    /** 챌린지 최종 정산 (settlements.id) */
    public static final String REF_SETTLEMENT = "settlement";
    /** 포인트 상점 주문 (orders.id) */
    public static final String REF_ORDER = "order";

    @Id
    @GeneratedValue(strategy = GenerationType.IDENTITY)
    private Long id;

    @Column(name = "wallet_id", nullable = false)
    private Long walletId;

    @Enumerated(EnumType.STRING)
    @Column(nullable = false)
    private PointTxType type;

    @Enumerated(EnumType.STRING)
    @Column(nullable = false)
    private PointSource source;

    /** 부호 있는 증감액 */
    @Column(nullable = false)
    private long amount;

    /** 거래 후 그 출처의 잔액 */
    @Column(name = "balance_after", nullable = false)
    private long balanceAfter;

    @Column(name = "ref_type")
    private String refType;

    @Column(name = "ref_id")
    private Long refId;

    @Column(name = "idempotency_key", nullable = false)
    private String idempotencyKey;

    @Column(name = "created_at", nullable = false)
    private LocalDateTime createdAt;

    static PointTransaction of(Long walletId, PointTxType type, PointSource source, long amount, long balanceAfter,
                               String refType, Long refId, String idempotencyKey, LocalDateTime now) {
        PointTransaction tx = new PointTransaction();
        tx.walletId = walletId;
        tx.type = type;
        tx.source = source;
        tx.amount = amount;
        tx.balanceAfter = balanceAfter;
        tx.refType = refType;
        tx.refId = refId;
        tx.idempotencyKey = idempotencyKey;
        tx.createdAt = now;
        return tx;
    }
}
