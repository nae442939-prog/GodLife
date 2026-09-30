package com.godlife.backend.settlement;

import com.godlife.backend.challenge.Challenge;
import com.godlife.backend.challenge.ChallengeMode;
import com.godlife.backend.challenge.ChallengeParticipant;
import com.godlife.backend.challenge.ChallengeParticipantRepository;
import com.godlife.backend.challenge.ChallengeRepository;
import com.godlife.backend.challenge.ChallengeService;
import com.godlife.backend.challenge.FrequencyType;
import com.godlife.backend.common.error.BusinessException;
import com.godlife.backend.common.error.ErrorCode;
import com.godlife.backend.settlement.dto.RankingResponse;
import com.godlife.backend.settlement.dto.RankingRow;
import com.godlife.backend.settlement.dto.SettlementSummaryResponse;
import com.godlife.backend.verification.VerificationRepository;
import lombok.RequiredArgsConstructor;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.time.Clock;
import java.time.LocalDate;
import java.util.ArrayList;
import java.util.List;
import java.util.Optional;

/** 챌린지 화면용: 어제(지난주) 정산 결과 · 챌린지 랭킹. 개설자·참가자만. */
@Service
@RequiredArgsConstructor
public class SettlementViewService {

    private final ChallengeService challengeService;
    private final ChallengeRepository challengeRepository;
    private final ChallengeParticipantRepository participantRepository;
    private final VerificationRepository verificationRepository;
    private final DailySettlementRepository dailyRepository;
    private final SettlementRepository settlementRepository;
    private final SettlementItemRepository itemRepository;
    private final Clock clock;

    /**
     * 가장 최근에 끝난 기간의 결과. 포인트 챌린지는 기간 결과 + 내 예정(끝나면 지급된) 금액을,
     * 무료 챌린지는 인증 기록으로 인원만 센다.
     * 아직 끝난 기간이 없으면 빈 값.
     */
    @Transactional(readOnly = true)
    public Optional<SettlementSummaryResponse> latest(Long challengeId, Long userId) {
        challengeService.requireMember(challengeId, userId);
        Challenge c = challengeRepository.findById(challengeId)
                .orElseThrow(() -> new BusinessException(ErrorCode.CHALLENGE_NOT_FOUND));
        LocalDate today = LocalDate.now(clock);
        Period period = Period.all(c).stream()
                .filter(p -> p.end().isBefore(today))
                .reduce((a, b) -> b)
                .orElse(null);
        if (period == null) {
            return Optional.empty();
        }
        boolean weekly = c.getFrequencyType() == FrequencyType.WEEKLY_N;
        ChallengeParticipant me = participantRepository.findByChallengeIdAndUserId(challengeId, userId)
                .filter(p -> SettlementService.SETTLED_STATUSES.contains(p.getStatus()))
                .orElse(null);
        Boolean mySuccess = me == null ? null : done(me, period) >= period.required();

        if (c.getMode() == ChallengeMode.BET) {
            DailySettlement d = dailyRepository.findByChallengeIdAndPeriodStart(challengeId, period.start())
                    .orElse(null);
            long[] totals = totals(c, me);
            boolean paid = totals[2] == 1;
            if (d == null) {
                return Optional.of(new SettlementSummaryResponse(period.index(), period.start(), period.end(), weekly,
                        true, false, 0, 0, 0, 0, mySuccess, 0, 0, totals[0], totals[1], paid));
            }
            long refund = me == null ? 0 : period.refund(c, me.getDepositAmount(), done(me, period));
            long reward = Boolean.TRUE.equals(mySuccess) ? d.getRewardShare() : 0;
            return Optional.of(new SettlementSummaryResponse(period.index(), period.start(), period.end(), weekly,
                    true, true, d.getSuccessCount(), d.getFailCount(), d.getForfeitedPool(), d.getRewardShare(),
                    mySuccess, refund, reward, totals[0], totals[1], paid));
        }

        List<ChallengeParticipant> all = participantRepository.findByChallengeIdAndStatusIn(challengeId,
                SettlementService.SETTLED_STATUSES);
        int success = (int) all.stream().filter(p -> done(p, period) >= period.required()).count();
        return Optional.of(new SettlementSummaryResponse(period.index(), period.start(), period.end(), weekly,
                false, true, success, all.size() - success, 0, 0, mySuccess, 0, 0, 0, 0, false));
    }

    /** 챌린지 랭킹: 인증 횟수 → 최장 연속 → 현재 연속. 같은 기록이면 같은 순위. */
    @Transactional(readOnly = true)
    public List<RankingResponse> ranking(Long challengeId, Long userId) {
        challengeService.requireMember(challengeId, userId);
        List<RankingRow> rows = participantRepository.findRanking(challengeId);
        List<RankingResponse> result = new ArrayList<>(rows.size());
        int rank = 0;
        RankingRow prev = null;
        for (int i = 0; i < rows.size(); i++) {
            RankingRow r = rows.get(i);
            if (prev == null || r.successDays() != prev.successDays() || r.maxStreak() != prev.maxStreak()
                    || r.currentStreak() != prev.currentStreak()) {
                rank = i + 1;
            }
            result.add(new RankingResponse(rank, r.nickname(), r.profileImageUrl(), r.successDays(), r.maxStreak(),
                    r.currentStreak(), r.userId().equals(userId)));
            prev = r;
        }
        return result;
    }

    /**
     * 내 환급·보상 합계 → [환급, 보상, 지급됨(1/0)].
     * 끝나서 지급됐으면 최종 정산 금액, 아니면 지금까지 기록된 기간 결과를 더한 예정 금액.
     */
    private long[] totals(Challenge c, ChallengeParticipant me) {
        if (me == null) {
            return new long[3];
        }
        Optional<Settlement> settlement = settlementRepository.findByChallengeId(c.getId());
        if (settlement.isPresent()) {
            return itemRepository.findBySettlementIdAndParticipantId(settlement.get().getId(), me.getId())
                    .map(i -> new long[]{i.getRefundAmount(), i.getRewardAmount(), 1})
                    .orElse(new long[]{0, 0, 1});
        }
        long refund = 0;
        long reward = 0;
        for (DailySettlement d : dailyRepository.findByChallengeId(c.getId())) {
            Period period = Period.all(c).stream().filter(p -> p.start().equals(d.getPeriodStart())).findFirst()
                    .orElse(null);
            if (period == null) {
                continue;
            }
            int n = done(me, period);
            refund += period.refund(c, me.getDepositAmount(), n);
            reward += n >= period.required() ? d.getRewardShare() : 0;
        }
        return new long[]{refund, reward, 0};
    }

    private int done(ChallengeParticipant p, Period period) {
        return verificationRepository.countByParticipants(List.of(p.getId()), period.start(), period.end()).stream()
                .findFirst()
                .map(row -> ((Long) row[1]).intValue())
                .orElse(0);
    }

}
