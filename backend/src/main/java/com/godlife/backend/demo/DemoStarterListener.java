package com.godlife.backend.demo;

import com.godlife.backend.notification.NotificationService;
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

import java.time.Clock;
import java.time.LocalDate;
import java.util.function.Supplier;

/**
 * 테스트 서버에서 회원에게 체험 데이터를 넣는다 (app.demo.starter-data=true 일 때만, 기본은 꺼짐).
 * - 가입이 끝나면(가입 트랜잭션이 커밋된 뒤) 그 회원에게
 * - 서버가 켜질 때 아직 받지 않은 실제 회원 모두에게 (이미 가입해 둔 테스트 계정용)
 * 넣고 나면 '오늘 인증하는 날이에요' 알림도 보낸다 (방금 참여한 챌린지는 오늘 아침 알림을 못 받았으므로).
 * 체험 데이터는 덤이라, 넣다가 실패해도 가입이나 서버 시작을 막지 않고 기록만 남긴다.
 */
@Slf4j
@Component
@RequiredArgsConstructor
public class DemoStarterListener {

    private final DemoStarter demoStarter;
    private final NotificationService notificationService;
    private final PlatformTransactionManager transactionManager;
    private final Clock clock;

    @Value("${app.demo.starter-data:false}")
    private boolean enabled;

    @TransactionalEventListener(fallbackExecution = true)
    public void onJoined(MemberJoinedEvent event) {
        if (enabled && Boolean.TRUE.equals(inNewTransaction(() -> demoStarter.seed(event.userId()), event.userId()))) {
            remindToday();
        }
    }

    @EventListener(ApplicationReadyEvent.class)
    public void onStartup() {
        if (!enabled) {
            return;
        }
        int seeded = 0;
        for (Long userId : demoStarter.membersWithoutStarter()) {
            if (Boolean.TRUE.equals(inNewTransaction(() -> demoStarter.seed(userId), userId))) {
                seeded++;
            }
        }
        // 환영 알림이 생기기 전에 체험 데이터를 받은 회원에게도 한 번
        for (Long userId : demoStarter.membersWithoutWelcome()) {
            inNewTransaction(() -> {
                demoStarter.welcome(userId);
                return true;
            }, userId);
        }
        if (seeded > 0) {
            log.info("체험 데이터를 넣었습니다: 회원 {}명", seeded);
        }
        remindToday();
    }

    /** '오늘 인증하는 날이에요' (하루 한 번만 만들어지므로 여러 번 불러도 된다) */
    private void remindToday() {
        inNewTransaction(() -> notificationService.remindToday(LocalDate.now(clock)) >= 0, null);
    }

    private Boolean inNewTransaction(Supplier<Boolean> work, Long userId) {
        TransactionTemplate tx = new TransactionTemplate(transactionManager);
        tx.setPropagationBehavior(TransactionDefinition.PROPAGATION_REQUIRES_NEW);
        try {
            return tx.execute(status -> work.get());
        } catch (RuntimeException e) {
            log.warn("체험 데이터 · 알림을 넣지 못했습니다 (회원 {}): {}", userId, e.getMessage());
            return false;
        }
    }
}
