package com.godlife.backend.demo;

import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.boot.context.event.ApplicationReadyEvent;
import org.springframework.context.event.EventListener;
import org.springframework.stereotype.Component;
import org.springframework.transaction.PlatformTransactionManager;
import org.springframework.transaction.TransactionDefinition;
import org.springframework.transaction.event.TransactionalEventListener;
import org.springframework.transaction.support.TransactionTemplate;

/**
 * 테스트 서버에서 회원에게 체험 데이터를 넣는다 (app.demo.starter-data=true 일 때만, 기본은 꺼짐).
 * - 가입이 끝나면(가입 트랜잭션이 커밋된 뒤) 그 회원에게
 * - 서버가 켜질 때 아직 받지 않은 실제 회원 모두에게 (이미 가입해 둔 테스트 계정용)
 * 체험 데이터는 덤이라, 넣다가 실패해도 가입이나 서버 시작을 막지 않고 기록만 남긴다.
 */
@Slf4j
@Component
@RequiredArgsConstructor
public class DemoStarterListener {

    private final DemoStarter demoStarter;
    private final PlatformTransactionManager transactionManager;

    @Value("${app.demo.starter-data:false}")
    private boolean enabled;

    @TransactionalEventListener(fallbackExecution = true)
    public void onJoined(MemberJoinedEvent event) {
        if (enabled) {
            seed(event.userId());
        }
    }

    @EventListener(ApplicationReadyEvent.class)
    public void onStartup() {
        if (!enabled) {
            return;
        }
        int seeded = 0;
        for (Long userId : demoStarter.membersWithoutStarter()) {
            if (seed(userId)) {
                seeded++;
            }
        }
        if (seeded > 0) {
            log.info("체험 데이터를 넣었습니다: 회원 {}명", seeded);
        }
    }

    private boolean seed(Long userId) {
        TransactionTemplate tx = new TransactionTemplate(transactionManager);
        tx.setPropagationBehavior(TransactionDefinition.PROPAGATION_REQUIRES_NEW);
        try {
            return Boolean.TRUE.equals(tx.execute(status -> demoStarter.seed(userId)));
        } catch (RuntimeException e) {
            log.warn("체험 데이터를 넣지 못했습니다 (회원 {}): {}", userId, e.getMessage());
            return false;
        }
    }
}
