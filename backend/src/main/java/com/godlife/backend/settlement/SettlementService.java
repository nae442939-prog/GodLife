package com.godlife.backend.settlement;

import com.godlife.backend.challenge.Challenge;
import com.godlife.backend.challenge.ChallengeMode;
import com.godlife.backend.challenge.ChallengeParticipant;
import com.godlife.backend.challenge.ChallengeParticipantRepository;
import com.godlife.backend.challenge.ChallengeRepository;
import com.godlife.backend.challenge.ChallengeStatus;
import com.godlife.backend.challenge.ParticipantStatus;
import com.godlife.backend.notification.Notification;
import com.godlife.backend.notification.NotificationService;
import com.godlife.backend.verification.VerificationRepository;
import com.godlife.backend.wallet.PointSource;
import com.godlife.backend.wallet.PointTransaction;
import com.godlife.backend.wallet.PointTxType;
import com.godlife.backend.wallet.WalletService;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.stereotype.Service;
import org.springframework.transaction.support.TransactionTemplate;

import java.time.Clock;
import java.time.LocalDate;
import java.time.LocalDateTime;
import java.util.Comparator;
import java.util.EnumSet;
import java.util.HashMap;
import java.util.List;
import java.util.Map;
import java.util.Set;

/**
 * 포인트 챌린지 정산.
 * 참가 포인트를 인증 1회당 몫으로 나눠, 기간(하루 / 주 N회는 한 주)이 끝날 때마다 결과만 기록한다 (DailySettlement):
 * - 그 기간 인증한 만큼 자기 몫을 돌려받을 예정이고
 * - 못 한 사람들의 몫은 그 기간 목표를 다 채운 사람들이 똑같이 나눠 받을 예정이다 (한 사람 한 기간 상한 = 자기 몫,
 *   아무도 못 채운 기간의 몫은 아무에게도 가지 않는다).
 * 챌린지가 끝나면 매일 결과를 모두 더해 한 번에 지급한다: 환급은 충전 포인트, 보상은 보상 포인트(상점 전용).
 * 챌린지 행을 잠그고, (챌린지, 기간) · 챌린지당 최종 정산 유니크 + 지급마다 멱등 키라 여러 번 돌아도 이중 지급이 없다.
 */
@Slf4j
@Service
@RequiredArgsConstructor
public class SettlementService {

    /** 참가 포인트를 걸고 끝까지 정산 대상인 상태 (취소·강퇴는 이미 전액 환급됨) */
    static final Set<ParticipantStatus> SETTLED_STATUSES = EnumSet.of(ParticipantStatus.ACTIVE,
            ParticipantStatus.COMPLETED, ParticipantStatus.FAILED, ParticipantStatus.GAVE_UP);

    private final ChallengeRepository challengeRepository;
    private final ChallengeParticipantRepository participantRepository;
    private final VerificationRepository verificationRepository;
    private final DailySettlementRepository dailyRepository;
    private final SettlementRepository settlementRepository;
    private final SettlementItemRepository itemRepository;
    private final WalletService walletService;
    private final NotificationService notificationService;
    private final TransactionTemplate transactionTemplate;
    private final Clock clock;

    /** today 기준으로 어제까지 끝난 기간의 결과를 기록하고(밀린 날 포함), 끝난 챌린지는 한 번에 지급한다. 기록한 기간 수. */
    public int settleDue(LocalDate today) {
        int settled = 0;
        for (Long id : challengeRepository.findIdsToSettle(today.minusDays(1))) {
            try {
                Integer n = transactionTemplate.execute(status -> settleChallenge(id, today));
                settled += n == null ? 0 : n;
            } catch (RuntimeException e) {
                log.error("챌린지 {} 정산 실패", id, e);
            }
        }
        return settled;
    }

    private int settleChallenge(Long challengeId, LocalDate today) {
        Challenge c = challengeRepository.findForUpdate(challengeId).orElse(null);
        if (c == null || c.getStatus() == ChallengeStatus.SETTLED) {
            return 0;
        }
        int count = 0;
        if (c.getMode() == ChallengeMode.BET) {
            List<ChallengeParticipant> participants = participants(challengeId);
            for (Period period : Period.all(c)) {
                if (!period.end().isBefore(today)) {
                    break;
                }
                if (!dailyRepository.existsByChallengeIdAndPeriodStart(challengeId, period.start())) {
                    tally(c, period, participants);
                    count++;
                }
            }
            if (c.getStatus() == ChallengeStatus.ENDED) {
                payOut(c, participants);
            }
        }
        if (c.getStatus() == ChallengeStatus.ENDED) {
            c.markSettled();
            for (ChallengeParticipant p : participants(challengeId)) {
                notificationService.notify(p.getUserId(), Notification.Type.SETTLEMENT, "챌린지가 끝났어요",
                        "'" + c.getTitle() + "' 결과를 확인해 보세요.", "/challenges/" + challengeId,
                        "settled:" + challengeId);
            }
        }
        return count;
    }

    /** 한 기간의 결과를 기록한다 (지급은 끝날 때 한 번에). */
    private void tally(Challenge c, Period period, List<ChallengeParticipant> participants) {
        Map<Long, Integer> done = counts(participants, period);
        long pool = 0;
        int winners = 0;
        for (ChallengeParticipant p : participants) {
            int n = done.getOrDefault(p.getId(), 0);
            pool += period.value(c, p.getDepositAmount()) - period.refund(c, p.getDepositAmount(), n);
            if (n >= period.required()) {
                winners++;
            }
        }
        long share = winners == 0 ? 0 : Math.min(pool / winners, cap(c, period, participants));
        dailyRepository.saveAndFlush(DailySettlement.of(c.getId(), period, winners, participants.size() - winners,
                pool, share, LocalDateTime.now(clock)));
    }

    /**
     * 챌린지가 끝나면 매일 결과를 더해 참가자마다 환급 1건 + 보상 1건을 지급한다.
     * 최종 정산 행(챌린지당 하나)을 먼저 만들어, 두 번째 실행은 여기서 멈춘다.
     */
    private void payOut(Challenge c, List<ChallengeParticipant> participants) {
        if (settlementRepository.findByChallengeId(c.getId()).isPresent()) {
            return;
        }
        List<Period> periods = Period.all(c);
        Map<LocalDate, DailySettlement> daily = new HashMap<>();
        dailyRepository.findByChallengeId(c.getId()).forEach(d -> daily.put(d.getPeriodStart(), d));

        Map<Long, long[]> totals = new HashMap<>(); // participantId → [환급, 보상, 채운 인증 수, 상한에 걸림]
        long totalPool = 0;
        long forfeited = 0;
        long distributed = 0;
        for (ChallengeParticipant p : participants) {
            totals.put(p.getId(), new long[4]);
            totalPool += p.getDepositAmount();
        }
        for (Period period : periods) {
            DailySettlement d = daily.get(period.start());
            if (d == null) {
                continue;
            }
            forfeited += d.getForfeitedPool();
            distributed += d.getDistributed();
            Map<Long, Integer> done = counts(participants, period);
            long cap = cap(c, period, participants);
            boolean capped = d.getSuccessCount() > 0 && d.getForfeitedPool() / d.getSuccessCount() > cap;
            for (ChallengeParticipant p : participants) {
                int n = done.getOrDefault(p.getId(), 0);
                long[] t = totals.get(p.getId());
                t[0] += period.refund(c, p.getDepositAmount(), n);
                t[2] += Math.min(n, period.required());
                if (n >= period.required()) {
                    t[1] += d.getRewardShare();
                    t[3] |= capped ? 1 : 0;
                }
            }
        }

        Settlement settlement = settlementRepository.saveAndFlush(Settlement.start(c.getId(), totalPool, forfeited,
                distributed, participants.isEmpty() ? 0 : Period.unit(c, participants.get(0).getDepositAmount())));
        for (ChallengeParticipant p : participants) {
            long[] t = totals.get(p.getId());
            SettlementItem item = itemRepository.save(SettlementItem.of(settlement.getId(), p.getId(), (int) t[2],
                    c.targetCount(), t[0], t[1], p.getDepositAmount() - t[0], t[3] == 1));
            Long refundTx = walletService.settle(p.getUserId(), PointTxType.REFUND, PointSource.CHARGED, t[0],
                    PointTransaction.REF_SETTLEMENT, settlement.getId(), refundKey(settlement.getId(), p.getId()));
            Long rewardTx = walletService.settle(p.getUserId(), PointTxType.REWARD, PointSource.REWARD, t[1],
                    PointTransaction.REF_SETTLEMENT, settlement.getId(), rewardKey(settlement.getId(), p.getId()));
            item.paid(refundTx, rewardTx);
        }
        settlement.done(LocalDateTime.now(clock));
    }

    /** 정산 대상 참가자. 지갑을 늘 같은 순서(회원 id)로 잠그도록 정렬한다. */
    private List<ChallengeParticipant> participants(Long challengeId) {
        return participantRepository.findByChallengeIdAndStatusIn(challengeId, SETTLED_STATUSES).stream()
                .filter(p -> p.getDepositAmount() > 0)
                .sorted(Comparator.comparing(ChallengeParticipant::getUserId))
                .toList();
    }

    /** 보상 상한 = 그 기간 한 사람 몫 (참가 포인트가 모두 같아서 한 사람 기준) */
    private static long cap(Challenge c, Period period, List<ChallengeParticipant> participants) {
        return participants.isEmpty() ? 0 : period.value(c, participants.get(0).getDepositAmount());
    }

    Map<Long, Integer> counts(List<ChallengeParticipant> participants, Period period) {
        Map<Long, Integer> done = new HashMap<>();
        if (participants.isEmpty()) {
            return done;
        }
        for (Object[] row : verificationRepository.countByParticipants(
                participants.stream().map(ChallengeParticipant::getId).toList(), period.start(), period.end())) {
            done.put((Long) row[0], ((Long) row[1]).intValue());
        }
        return done;
    }

    static String refundKey(Long settlementId, Long participantId) {
        return "settle-refund:" + settlementId + ":" + participantId;
    }

    static String rewardKey(Long settlementId, Long participantId) {
        return "settle-reward:" + settlementId + ":" + participantId;
    }
}
