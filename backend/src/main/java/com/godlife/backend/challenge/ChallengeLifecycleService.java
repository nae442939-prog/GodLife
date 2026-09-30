package com.godlife.backend.challenge;

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
 * 3) 시작일이 된 챌린지를 진행 중으로 바꾼다
 * 서버가 자정에 꺼져 있었을 수 있어 켜질 때도 한 번 돈다. 여러 번 돌아도 결과가 같다.
 */
@Slf4j
@Service
@RequiredArgsConstructor
public class ChallengeLifecycleService {

    private final ChallengeRepository challengeRepository;
    private final ChallengeParticipantRepository participantRepository;
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
        Integer reset = transactionTemplate.execute(
                status -> participantRepository.resetMissedStreaks(today.minusDays(1)));
        Integer started = transactionTemplate.execute(status -> challengeRepository.startDue(today));
        log.info("챌린지 진행 관리 {}: 종료 {}개, 연속 기록 초기화 {}명, 시작 {}개", today, ended, reset, started);
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
