package com.godlife.backend.challenge;

import com.godlife.backend.collusion.CollusionService;
import com.godlife.backend.notification.NotificationService;
import com.godlife.backend.settlement.SettlementService;
import com.godlife.backend.tier.TierService;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.boot.context.event.ApplicationReadyEvent;
import org.springframework.context.event.EventListener;
import org.springframework.scheduling.annotation.Scheduled;
import org.springframework.stereotype.Service;
import org.springframework.transaction.support.TransactionTemplate;

import java.time.Clock;
import java.time.LocalDate;

/**
 * 챌린지 진행 관리 (매일 밤 12시, 서울 시간).
 * 1) 종료일이 지난 챌린지를 종료하고 참가자마다 성공/실패를 판정한다 (정산은 이 결과를 쓴다)
 * 2) 매일 챌린지에서 어제 인증을 빼먹은 참가자의 연속 기록을 0으로 되돌린다
 * 3) 포인트 챌린지 매일 정산 (어제까지 끝난 기간, 밀린 날 포함) → 다 끝나면 정산 완료
 * 4) 시작일이 된 챌린지를 진행 중으로 바꾼다
 * 5) 정산이 끝난 챌린지에서 담합 의심 조합을 찾아 표시한다 (관리자 화면에서 본다)
 * 서버가 자정에 꺼져 있었을 수 있어 켜질 때도 한 번 돈다. 여러 번 돌아도 결과가 같다.
 */
@Slf4j
@Service
@RequiredArgsConstructor
public class ChallengeLifecycleService {

    private final ChallengeRepository challengeRepository;
    private final ChallengeParticipantRepository participantRepository;
    private final SettlementService settlementService;
    private final NotificationService notificationService;
    private final CollusionService collusionService;
    private final TierService tierService;
    private final TransactionTemplate transactionTemplate;
    private final Clock clock;

    /** 테스트는 켜질 때 돌지 않게 끈다 (테스트가 직접 run 을 부른다) */
    @Value("${app.lifecycle.run-on-startup:true}")
    private boolean runOnStartup;

    @Scheduled(cron = "0 0 0 * * *", zone = "Asia/Seoul")
    public void midnight() {
        run(LocalDate.now(clock));
    }

    @EventListener(ApplicationReadyEvent.class)
    public void onStartup() {
        if (runOnStartup) {
            run(LocalDate.now(clock));
        }
    }

    /** today 기준으로 한 번 처리한다. 챌린지 하나가 실패해도 나머지는 계속한다. */
    public void run(LocalDate today) {
        int ended = 0;
        for (Long id : challengeRepository.findIdsToEnd(today)) {
            try {
                transactionTemplate.executeWithoutResult(status -> end(id, today));
                ended++;
            } catch (RuntimeException e) {
                log.error("챌린지 {} 종료 처리 실패", id, e);
            }
        }
        int settled = settlementService.settleDue(today);
        int tierChanged = 0;
        try {
            // 끝난 챌린지의 완주 · 실패가 정해진 뒤에 칭호를 점수에 맞춘다 (승급 · 강등)
            tierChanged = tierService.refreshAll();
        } catch (RuntimeException e) {
            log.error("칭호 맞추기 실패", e);
        }
        Integer reset = transactionTemplate.execute(
                status -> participantRepository.resetMissedStreaks(today.minusDays(1)));
        Integer started = transactionTemplate.execute(status -> challengeRepository.startDue(today));
        int flagged = 0;
        try {
            flagged = collusionService.scan();
        } catch (RuntimeException e) {
            log.error("담합 의심 조합 찾기 실패", e);
        }
        int reminded = 0;
        try {
            // 오늘 시작한 챌린지까지 포함하도록 시작 처리 뒤에 만든다
            reminded = notificationService.remindToday(today);
        } catch (RuntimeException e) {
            log.error("오늘 인증 알림 만들기 실패", e);
        }
        log.info("챌린지 진행 관리 {}: 종료 {}개, 정산 {}건, 연속 기록 초기화 {}명, 시작 {}개, 인증 알림 {}건, 담합 의심 표시 {}건, 칭호 변경 {}명",
                today, ended, settled, reset, started, reminded, flagged, tierChanged);
    }

    /** 챌린지 행을 잠근 채로 종료하고, 아직 판정 전(ACTIVE)인 참가자를 성공/실패로 판정한다. */
    private void end(Long challengeId, LocalDate today) {
        Challenge c = challengeRepository.findForUpdate(challengeId).orElse(null);
        if (c == null || !c.getEndDate().isBefore(today) || c.getStatus() == ChallengeStatus.ENDED
                || c.getStatus() == ChallengeStatus.SETTLED) {
            return;
        }
        int target = c.targetCount();
        participantRepository.findByChallengeIdAndStatus(challengeId, ParticipantStatus.ACTIVE)
                .forEach(p -> p.finish(target));
        c.end();
    }
}
