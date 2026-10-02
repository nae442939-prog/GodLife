package com.godlife.backend.wallet;

import com.godlife.backend.common.error.BusinessException;
import com.godlife.backend.common.error.ErrorCode;
import com.godlife.backend.user.User;
import com.godlife.backend.user.UserRepository;
import com.godlife.backend.wallet.dto.PointTransactionResponse;
import com.godlife.backend.wallet.dto.WalletResponse;
import jakarta.persistence.EntityManager;
import jakarta.persistence.LockModeType;
import lombok.RequiredArgsConstructor;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.dao.DataIntegrityViolationException;
import org.springframework.data.domain.PageRequest;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.time.Clock;
import java.time.LocalDate;
import java.time.LocalDateTime;
import java.util.List;
import java.util.Map;
import java.util.Optional;
import java.util.Set;
import java.util.stream.Collectors;

/**
 * 포인트 지갑. 모든 증감은 지갑 행을 잠근 채(SELECT ... FOR UPDATE) 원장 한 줄을 쓰고 잔액 캐시를 바꾼다.
 * 원장의 idempotency_key 가 유니크라 같은 요청이 두 번 와도 한 번만 반영된다 (프로젝트 규칙 4).
 * 챌린지 참가비는 충전 포인트에서만 뺀다. 보상 포인트는 상점 전용이다 (규칙 2).
 * 상점에서는 보상 포인트를 먼저 쓰고 모자란 만큼 충전 포인트를 쓴다.
 */
@Service
@RequiredArgsConstructor
public class WalletService {

    public static final int PAGE_SIZE = 20;
    /** 테스트 충전 버튼 금액 (결제 연동 전 가상 지급) */
    static final Set<Long> TEST_CHARGE_AMOUNTS = Set.of(1_000L, 5_000L, 10_000L);

    private final WalletRepository walletRepository;
    private final PointTransactionRepository txRepository;
    private final UserRepository userRepository;
    private final Clock clock;
    private final EntityManager entityManager;

    @Value("${app.wallet.test-charge-daily-limit:50000}")
    private long testChargeDailyLimit;
    @Value("${app.wallet.bet-daily-limit:30000}")
    private long betDailyLimit;
    @Value("${app.wallet.bet-monthly-limit:200000}")
    private long betMonthlyLimit;
    /** 가입 30일 이내 신규 회원은 한도를 낮춘다 (과도한 손실 방지) */
    @Value("${app.wallet.newbie-days:30}")
    private int newbieDays;
    @Value("${app.wallet.newbie-bet-daily-limit:10000}")
    private long newbieBetDailyLimit;
    @Value("${app.wallet.newbie-bet-monthly-limit:50000}")
    private long newbieBetMonthlyLimit;

    /** 잔액 + 거래 내역 첫 페이지 + 오늘/이번 달 베팅 한도 */
    @Transactional
    public WalletResponse wallet(Long userId) {
        Wallet wallet = ensure(userId);
        return toResponse(wallet, userId);
    }

    @Transactional(readOnly = true)
    public List<PointTransactionResponse> transactions(Long userId, int page) {
        return walletRepository.findByUserId(userId)
                .map(w -> page(w.getId(), page))
                .orElse(List.of());
    }

    /**
     * 테스트 충전 (결제 연동 전 가상 지급). 정해진 금액만, 하루 한도까지.
     * requestKey 는 화면이 버튼을 누를 때마다 만드는 값이라, 같은 요청이 두 번 와도(더블클릭·재전송) 한 번만 충전된다.
     */
    @Transactional
    public WalletResponse testCharge(Long userId, long amount, String requestKey) {
        if (!TEST_CHARGE_AMOUNTS.contains(amount)) {
            throw new BusinessException(ErrorCode.INVALID_CHARGE_AMOUNT);
        }
        String key = "charge:" + userId + ":" + requestKey;
        Wallet wallet = lock(userId);
        if (txRepository.existsByIdempotencyKey(key)) {
            return toResponse(wallet, userId);
        }
        long today = txRepository.sumSince(wallet.getId(), PointTxType.CHARGE, startOfToday());
        if (today + amount > testChargeDailyLimit) {
            throw new BusinessException(ErrorCode.CHARGE_LIMIT_EXCEEDED,
                    "테스트 충전은 하루 %,dP까지예요. 오늘 %,dP 더 충전할 수 있어요."
                            .formatted(testChargeDailyLimit, Math.max(0, testChargeDailyLimit - today)));
        }
        record(wallet, PointTxType.CHARGE, PointSource.CHARGED, amount, null, null, key);
        return toResponse(wallet, userId);
    }

    /**
     * 충전 포인트 환불 (프로젝트 규칙 2): 쓰지 않은 충전 포인트까지만. 보상 포인트는 환불·현금화하지 않는다.
     * 출금·송금이 아니라 결제 취소다. 결제 연동 전이라 지금은 원장에 '충전 취소'를 남기고 충전 포인트를 뺀다.
     * (결제 연동 후에는 여기서 PG 결제 취소 API 를 테스트 모드로 부르고, 충전 건별로 나눠 취소한다)
     */
    @Transactional
    public WalletResponse refundCharged(Long userId, long amount, String requestKey) {
        if (amount < 100 || amount % 100 != 0) {
            throw new BusinessException(ErrorCode.INVALID_REFUND_AMOUNT);
        }
        String key = "charge-cancel:" + userId + ":" + requestKey;
        Wallet wallet = lock(userId);
        if (txRepository.existsByIdempotencyKey(key)) {
            return toResponse(wallet, userId);
        }
        if (amount > wallet.getChargedBalance()) {
            throw new BusinessException(ErrorCode.REFUND_EXCEEDS_CHARGED,
                    "쓰지 않은 충전 포인트 %,dP까지만 환불할 수 있어요.".formatted(wallet.getChargedBalance()));
        }
        record(wallet, PointTxType.CHARGE_CANCEL, PointSource.CHARGED, -amount, null, null, key);
        return toResponse(wallet, userId);
    }

    /**
     * 참가 기록을 만들기 전에 먼저 확인한다: 베팅 한도 안이고 충전 포인트가 충분한지 (실제로 뺄 때 잠근 채 다시 확인).
     * 참여 트랜잭션 안에서 불린다. readOnly 로 두면 같은 세션이 읽기 전용이 되어 뒤이은 차감이 저장되지 않으므로 두지 않는다.
     */
    @Transactional
    public void checkCanPay(Long userId, long amount) {
        Wallet wallet = walletRepository.findByUserId(userId).orElse(null);
        long charged = wallet == null ? 0 : wallet.getChargedBalance();
        if (wallet != null) {
            checkBetLimit(wallet, userId, amount);
        }
        if (charged < amount) {
            throw insufficient(amount, charged);
        }
    }

    /**
     * 챌린지 참가비를 충전 포인트에서 뺀다. 부르는 쪽(참여)이 챌린지 행을 잠근 트랜잭션 안에서 부른다.
     * 베팅 한도(하루·한 달, 신규 회원은 더 낮게)를 넘거나 충전 포인트가 모자라면 거절한다.
     */
    @Transactional
    public void payEntryFee(Long userId, long amount, Long participantId) {
        Wallet wallet = lock(userId);
        checkBetLimit(wallet, userId, amount);
        if (wallet.getChargedBalance() < amount) {
            throw insufficient(amount, wallet.getChargedBalance());
        }
        long seq = txRepository.countByRefTypeAndRefIdAndType(PointTransaction.REF_PARTICIPANT, participantId,
                PointTxType.ENTRY_FEE) + 1;
        record(wallet, PointTxType.ENTRY_FEE, PointSource.CHARGED, -amount, PointTransaction.REF_PARTICIPANT,
                participantId, "entry:" + participantId + ":" + seq);
    }

    /** 참가비를 충전 포인트로 돌려준다 (시작 전 취소 · 챌린지 삭제 · 강퇴). */
    @Transactional
    public void refundEntryFee(Long userId, long amount, Long participantId) {
        if (amount <= 0) {
            return;
        }
        Wallet wallet = lock(userId);
        long seq = txRepository.countByRefTypeAndRefIdAndType(PointTransaction.REF_PARTICIPANT, participantId,
                PointTxType.REFUND) + 1;
        record(wallet, PointTxType.REFUND, PointSource.CHARGED, amount, PointTransaction.REF_PARTICIPANT,
                participantId, "refund:" + participantId + ":" + seq);
    }

    /**
     * 정산 지급 (환급·보상). 지갑을 잠그고, 같은 멱등 키가 이미 있으면 그 줄을 돌려준다 → 정산이 두 번 돌아도 한 번만 지급.
     * 지급한 원장 줄 id (금액이 0이면 null).
     * 부르는 쪽(정산)이 챌린지 행을 잠근 트랜잭션 안에서 부른다.
     */
    @Transactional
    public Long settle(Long userId, PointTxType type, PointSource source, long amount,
                       String refType, Long refId, String key) {
        if (amount <= 0) {
            return null;
        }
        Wallet wallet = lock(userId);
        Optional<PointTransaction> existing = txRepository.findByIdempotencyKey(key);
        if (existing.isPresent()) {
            return existing.get().getId();
        }
        return record(wallet, type, source, amount, refType, refId, key).getId();
    }

    /** 상점 결제에 쓴 포인트 (출처별). 주문을 취소하면 이 값대로 원래 출처에 돌려준다 */
    public record ShopPayment(long reward, long charged) {
    }

    /**
     * 상점 결제: 상점에서만 쓸 수 있는 보상 포인트를 먼저 쓰고, 모자란 만큼 충전 포인트로 채운다 (환불할 수 있는 충전 포인트를 되도록 남긴다).
     * 부르는 쪽(주문)이 상품 행을 잠근 트랜잭션 안에서 부른다. 주문마다 한 번만 빠진다 (멱등 키 = 주문 id).
     */
    @Transactional
    public ShopPayment payShop(Long userId, long amount, Long orderId) {
        Wallet wallet = lock(userId);
        if (wallet.getBalance() < amount) {
            throw new BusinessException(ErrorCode.INSUFFICIENT_POINTS,
                    "포인트가 모자라요. 결제할 포인트 %,dP, 지금 %,dP 있어요.".formatted(amount, wallet.getBalance()));
        }
        long reward = Math.min(wallet.getRewardBalance(), amount);
        long charged = amount - reward;
        if (reward > 0) {
            record(wallet, PointTxType.PURCHASE, PointSource.REWARD, -reward, PointTransaction.REF_ORDER, orderId,
                    "shop:" + orderId + ":reward");
        }
        if (charged > 0) {
            record(wallet, PointTxType.PURCHASE, PointSource.CHARGED, -charged, PointTransaction.REF_ORDER, orderId,
                    "shop:" + orderId + ":charged");
        }
        return new ShopPayment(reward, charged);
    }

    /**
     * 상점 주문 취소: 낸 포인트를 원래 출처 그대로 돌려준다 (보상으로 낸 만큼은 보상으로, 충전으로 낸 만큼은 충전으로).
     * 출처를 섞으면 보상 포인트가 환불 가능한 충전 포인트로 바뀌어 현금화 통로가 되므로 반드시 나눠서 돌려준다 (규칙 2).
     */
    @Transactional
    public void refundShop(Long userId, ShopPayment paid, Long orderId) {
        Wallet wallet = lock(userId);
        if (paid.reward() > 0) {
            record(wallet, PointTxType.PURCHASE_CANCEL, PointSource.REWARD, paid.reward(), PointTransaction.REF_ORDER,
                    orderId, "shop-cancel:" + orderId + ":reward");
        }
        if (paid.charged() > 0) {
            record(wallet, PointTxType.PURCHASE_CANCEL, PointSource.CHARGED, paid.charged(),
                    PointTransaction.REF_ORDER, orderId, "shop-cancel:" + orderId + ":charged");
        }
    }

    private static BusinessException insufficient(long amount, long charged) {
        return new BusinessException(ErrorCode.INSUFFICIENT_POINTS,
                "충전 포인트가 모자라요. 참가 포인트 %,dP, 지금 %,dP 있어요.".formatted(amount, charged));
    }

    private void checkBetLimit(Wallet wallet, Long userId, long amount) {
        boolean newbie = isNewbie(userId);
        long daily = newbie ? newbieBetDailyLimit : betDailyLimit;
        long monthly = newbie ? newbieBetMonthlyLimit : betMonthlyLimit;
        long today = txRepository.betSince(wallet.getId(), startOfToday());
        if (today + amount > daily) {
            throw new BusinessException(ErrorCode.BET_LIMIT_EXCEEDED,
                    "하루에 걸 수 있는 포인트는 %,dP까지예요%s. 오늘 %,dP 더 걸 수 있어요."
                            .formatted(daily, newbie ? " (가입 30일 이내)" : "", Math.max(0, daily - today)));
        }
        long month = txRepository.betSince(wallet.getId(), startOfMonth());
        if (month + amount > monthly) {
            throw new BusinessException(ErrorCode.BET_LIMIT_EXCEEDED,
                    "한 달에 걸 수 있는 포인트는 %,dP까지예요%s. 이번 달 %,dP 더 걸 수 있어요."
                            .formatted(monthly, newbie ? " (가입 30일 이내)" : "", Math.max(0, monthly - month)));
        }
    }

    private PointTransaction record(Wallet wallet, PointTxType type, PointSource source, long amount,
                                    String refType, Long refId, String key) {
        long after = wallet.apply(source, amount);
        try {
            return txRepository.saveAndFlush(PointTransaction.of(wallet.getId(), type, source, amount, after, refType,
                    refId, key, LocalDateTime.now(clock)));
        } catch (DataIntegrityViolationException e) {
            // 잠금으로 이미 막히지만, 멱등 키 유니크 제약이 마지막 안전장치다
            throw new BusinessException(ErrorCode.DUPLICATE_REQUEST);
        }
    }

    private WalletResponse toResponse(Wallet wallet, Long userId) {
        boolean newbie = isNewbie(userId);
        long daily = newbie ? newbieBetDailyLimit : betDailyLimit;
        long monthly = newbie ? newbieBetMonthlyLimit : betMonthlyLimit;
        List<PointTransactionResponse> recent = page(wallet.getId(), 0);
        return new WalletResponse(wallet.getBalance(), wallet.getChargedBalance(), wallet.getRewardBalance(),
                daily, txRepository.betSince(wallet.getId(), startOfToday()),
                monthly, txRepository.betSince(wallet.getId(), startOfMonth()),
                testChargeDailyLimit, txRepository.sumSince(wallet.getId(), PointTxType.CHARGE, startOfToday()),
                newbie, recent);
    }

    /** 거래 내역 한 페이지 (최신순). 챌린지 참가비·환급·정산에는 챌린지 제목을, 상점 구매·취소에는 주문한 상품을 붙인다. */
    private List<PointTransactionResponse> page(Long walletId, int page) {
        List<PointTransaction> txs = txRepository
                .findByWalletIdOrderByIdDesc(walletId, PageRequest.of(Math.max(page, 0), PAGE_SIZE)).getContent();
        Set<Long> participantIds = txs.stream()
                .filter(t -> PointTransaction.REF_PARTICIPANT.equals(t.getRefType()) && t.getRefId() != null)
                .map(PointTransaction::getRefId)
                .collect(Collectors.toSet());
        Map<Long, String> titles = participantIds.isEmpty() ? Map.of()
                : txRepository.findChallengeTitles(participantIds).stream()
                        .collect(Collectors.toMap(row -> (Long) row[0], row -> (String) row[1]));
        Set<Long> settlementIds = txs.stream()
                .filter(t -> PointTransaction.REF_SETTLEMENT.equals(t.getRefType()) && t.getRefId() != null)
                .map(PointTransaction::getRefId)
                .collect(Collectors.toSet());
        Map<Long, String> settlementTitles = settlementIds.isEmpty() ? Map.of()
                : txRepository.findSettlementTitles(settlementIds).stream()
                        .collect(Collectors.toMap(row -> (Long) row[0], row -> (String) row[1]));
        Set<Long> orderIds = txs.stream()
                .filter(t -> PointTransaction.REF_ORDER.equals(t.getRefType()) && t.getRefId() != null)
                .map(PointTransaction::getRefId)
                .collect(Collectors.toSet());
        Map<Long, String> orderTitles = orderIds.isEmpty() ? Map.of()
                : txRepository.findOrderTitles(orderIds).stream()
                        .collect(Collectors.toMap(row -> ((Number) row[0]).longValue(), row -> {
                            long kinds = ((Number) row[2]).longValue();
                            return kinds > 1 ? row[1] + " 외 " + (kinds - 1) + "가지" : (String) row[1];
                        }));
        return txs.stream()
                .map(t -> PointTransactionResponse.from(t,
                        PointTransaction.REF_SETTLEMENT.equals(t.getRefType()) ? settlementTitles.get(t.getRefId())
                                : PointTransaction.REF_PARTICIPANT.equals(t.getRefType()) ? titles.get(t.getRefId())
                                : null,
                        PointTransaction.REF_ORDER.equals(t.getRefType()) && t.getRefId() != null
                                ? orderTitles.get(t.getRefId()) : null))
                .toList();
    }

    private Wallet ensure(Long userId) {
        walletRepository.createIfMissing(userId);
        return walletRepository.findByUserId(userId).orElseThrow();
    }

    /**
     * 지갑 행을 SELECT ... FOR UPDATE 로 잠그고 최신 값으로 다시 읽는다.
     * 같은 트랜잭션에서 이미 (잠그지 않고) 읽은 지갑이 영속성 컨텍스트에 있으면, 잠금 쿼리를 보내도
     * Hibernate 가 예전 값을 그대로 돌려준다. 그러면 동시 요청이 서로의 차감을 덮어쓰므로 refresh 로 다시 읽는다.
     */
    private Wallet lock(Long userId) {
        walletRepository.createIfMissing(userId);
        Wallet wallet = walletRepository.findByUserId(userId).orElseThrow();
        entityManager.refresh(wallet, LockModeType.PESSIMISTIC_WRITE);
        return wallet;
    }

    private boolean isNewbie(Long userId) {
        return userRepository.findById(userId)
                .map(User::getCreatedAt)
                .map(created -> created.isAfter(LocalDateTime.now(clock).minusDays(newbieDays)))
                .orElse(true);
    }

    private LocalDateTime startOfToday() {
        return LocalDate.now(clock).atStartOfDay();
    }

    private LocalDateTime startOfMonth() {
        return LocalDate.now(clock).withDayOfMonth(1).atStartOfDay();
    }
}
