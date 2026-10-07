package com.godlife.backend.settlement;

import com.godlife.backend.payment.TestCharger;
import com.godlife.backend.challenge.Challenge;
import com.godlife.backend.challenge.ChallengeLifecycleService;
import com.godlife.backend.challenge.ChallengeMode;
import com.godlife.backend.challenge.ChallengeService;
import com.godlife.backend.challenge.FrequencyType;
import com.godlife.backend.challenge.dto.ChallengeCreateRequest;
import com.godlife.backend.user.User;
import com.godlife.backend.user.UserRepository;
import com.godlife.backend.wallet.WalletService;
import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.test.context.ActiveProfiles;

import java.time.Clock;
import java.time.LocalDate;
import java.util.ArrayList;
import java.util.List;
import java.util.UUID;
import java.util.concurrent.CountDownLatch;
import java.util.concurrent.ExecutorService;
import java.util.concurrent.Executors;
import java.util.concurrent.Future;

import static org.assertj.core.api.Assertions.assertThat;

/**
 * 자정 정산이 동시에 여러 번 돌아도(서버 여러 대·재시작 겹침) 한 번만 지급되는지 (프로젝트 규칙 4).
 * 스레드마다 트랜잭션이 따로 돌아야 해서 롤백하지 않고, 만든 데이터를 끝나고 직접 지운다.
 */
@SpringBootTest
@ActiveProfiles("test")
class SettlementConcurrencyTest {

    private static final int THREADS = 6;

    @Autowired ChallengeLifecycleService lifecycle;
    @Autowired ChallengeService challengeService;
    @Autowired WalletService walletService;
    @Autowired TestCharger testCharger;
    @Autowired UserRepository userRepository;
    @Autowired JdbcTemplate jdbc;
    @Autowired Clock clock;

    private final List<Long> userIds = new ArrayList<>();
    private Long challengeId;

    @AfterEach
    void cleanUp() {
        if (challengeId != null) {
            jdbc.update("DELETE i FROM settlement_items i JOIN settlements s ON s.id = i.settlement_id "
                    + "WHERE s.challenge_id = ?", challengeId);
            jdbc.update("DELETE t FROM point_transactions t JOIN settlements s ON t.ref_type = 'settlement' "
                    + "AND t.ref_id = s.id WHERE s.challenge_id = ?", challengeId);
            jdbc.update("DELETE FROM settlements WHERE challenge_id = ?", challengeId);
            jdbc.update("DELETE FROM daily_settlements WHERE challenge_id = ?", challengeId);
            jdbc.update("DELETE v FROM verifications v JOIN challenge_participants p ON p.id = v.participant_id "
                    + "WHERE p.challenge_id = ?", challengeId);
            jdbc.update("DELETE FROM challenge_participants WHERE challenge_id = ?", challengeId);
            jdbc.update("DELETE FROM challenges WHERE id = ?", challengeId);
        }
        userIds.forEach(id -> {
            jdbc.update("DELETE FROM payments WHERE user_id = ?", id);
            jdbc.update("DELETE t FROM point_transactions t JOIN wallets w ON w.id = t.wallet_id WHERE w.user_id = ?", id);
            jdbc.update("DELETE FROM wallets WHERE user_id = ?", id);
            // 정산이 끝나면 참가자에게 알림이 가므로 회원을 지우기 전에 같이 지운다
            jdbc.update("DELETE FROM notifications WHERE user_id = ?", id);
            jdbc.update("DELETE FROM users WHERE id = ?", id);
        });
    }

    @Test
    @DisplayName("끝난 챌린지 정산이 동시에 6번 돌아도 환급·보상은 한 번씩만 들어간다")
    void settleOnceUnderConcurrency() throws Exception {
        LocalDate today = LocalDate.now(clock);
        Long host = newUser();
        Long winner = newUser();
        Long loser = newUser();
        testCharger.charge(winner, 10_000);
        testCharger.charge(loser, 10_000);
        Challenge c = challengeService.create(host, new ChallengeCreateRequest(1, "동시 정산", "테스트", ChallengeMode.BET,
                null, today, today, FrequencyType.DAILY, null, 3_000L, 10, null, null, false, null));
        challengeId = c.getId();
        challengeService.join(challengeId, winner);
        challengeService.join(challengeId, loser);
        jdbc.update("""
                INSERT INTO verifications (participant_id, verify_date, received_at, image_url, image_hash, status)
                SELECT id, ?, NOW(), 'verification/test.jpg', SHA2(UUID(), 256), 'APPROVED'
                FROM challenge_participants WHERE challenge_id = ? AND user_id = ?
                """, today, challengeId, winner);

        ExecutorService pool = Executors.newFixedThreadPool(THREADS);
        CountDownLatch start = new CountDownLatch(1);
        try {
            List<Future<Integer>> futures = new ArrayList<>();
            for (int i = 0; i < THREADS; i++) {
                futures.add(pool.submit(() -> {
                    start.await();
                    lifecycle.run(today.plusDays(1));
                    return 0;
                }));
            }
            start.countDown();
            for (Future<Integer> f : futures) {
                f.get();
            }
        } finally {
            pool.shutdownNow();
        }

        // 하루짜리 → 몫 3,000P. 7,000 + 3,000 환급 / 보상은 진 사람 몫 3,000 (상한 = 몫)
        assertThat(walletService.wallet(winner).chargedBalance()).isEqualTo(10_000);
        assertThat(walletService.wallet(winner).rewardBalance()).isEqualTo(3_000);
        assertThat(walletService.wallet(loser).chargedBalance()).isEqualTo(7_000);
        Long rows = jdbc.queryForObject("SELECT COUNT(*) FROM daily_settlements WHERE challenge_id = ?", Long.class,
                challengeId);
        assertThat(rows).isEqualTo(1);
        Long finals = jdbc.queryForObject("SELECT COUNT(*) FROM settlements WHERE challenge_id = ?", Long.class,
                challengeId);
        assertThat(finals).isEqualTo(1);
    }

    private Long newUser() {
        String suffix = UUID.randomUUID().toString().replace("-", "").substring(0, 12);
        Long id = userRepository.saveAndFlush(User.createWithPassword(suffix + "@example.com", "unused", "x" + suffix,
                "x".repeat(52) + suffix)).getId();
        userIds.add(id);
        return id;
    }
}
