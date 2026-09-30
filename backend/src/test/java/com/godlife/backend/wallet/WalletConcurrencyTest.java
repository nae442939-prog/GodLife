package com.godlife.backend.wallet;

import com.godlife.backend.challenge.Challenge;
import com.godlife.backend.challenge.ChallengeService;
import com.godlife.backend.challenge.dto.ChallengeCreateRequest;
import com.godlife.backend.challenge.ChallengeMode;
import com.godlife.backend.challenge.FrequencyType;
import com.godlife.backend.user.User;
import com.godlife.backend.user.UserRepository;
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
import java.util.concurrent.Callable;
import java.util.concurrent.CountDownLatch;
import java.util.concurrent.ExecutorService;
import java.util.concurrent.Executors;
import java.util.concurrent.Future;

import static org.assertj.core.api.Assertions.assertThat;

/**
 * 동시에 요청이 몰려도 이중 충전·잔액 초과 차감이 없는지 (CLAUDE.md 규칙 4).
 * 스레드마다 트랜잭션이 따로 돌아야 해서 롤백하지 않고, 만든 데이터를 끝나고 직접 지운다.
 */
@SpringBootTest
@ActiveProfiles("test")
class WalletConcurrencyTest {

    private static final int THREADS = 8;

    @Autowired WalletService walletService;
    @Autowired ChallengeService challengeService;
    @Autowired UserRepository userRepository;
    @Autowired JdbcTemplate jdbc;
    @Autowired Clock clock;

    private final List<Long> userIds = new ArrayList<>();
    private final List<Long> challengeIds = new ArrayList<>();

    @AfterEach
    void cleanUp() {
        challengeIds.forEach(id -> {
            jdbc.update("DELETE FROM challenge_participants WHERE challenge_id = ?", id);
            jdbc.update("DELETE FROM challenges WHERE id = ?", id);
        });
        userIds.forEach(id -> {
            jdbc.update("DELETE t FROM point_transactions t JOIN wallets w ON w.id = t.wallet_id WHERE w.user_id = ?", id);
            jdbc.update("DELETE FROM wallets WHERE user_id = ?", id);
            jdbc.update("DELETE FROM users WHERE id = ?", id);
        });
    }

    @Test
    @DisplayName("같은 충전 요청이 동시에 8번 와도 한 번만 충전된다")
    void sameChargeKeyOnce() throws Exception {
        Long me = newUser();
        runAll(() -> walletService.testCharge(me, 10_000, "double-click"));

        assertThat(walletService.wallet(me).chargedBalance()).isEqualTo(10_000);
        assertThat(count("SELECT COUNT(*) FROM point_transactions t JOIN wallets w ON w.id = t.wallet_id "
                + "WHERE w.user_id = ?", me)).isEqualTo(1);
    }

    @Test
    @DisplayName("5,000P 로 3,000P 챌린지 8개에 동시에 참여해도 1개만 들어가고 잔액이 음수가 되지 않는다")
    void noOverspendUnderConcurrency() throws Exception {
        Long host = newUser();
        Long me = newUser();
        walletService.testCharge(me, 5_000, "seed");
        List<Long> ids = new ArrayList<>();
        for (int i = 0; i < THREADS; i++) {
            ids.add(betChallenge(host, 3_000));
        }

        List<Callable<Object>> tasks = new ArrayList<>();
        for (Long id : ids) {
            tasks.add(() -> {
                challengeService.join(id, me);
                return null;
            });
        }
        int ok = runAll(tasks);

        assertThat(ok).isEqualTo(1);
        assertThat(walletService.wallet(me).chargedBalance()).isEqualTo(2_000);
        // 거절된 참여는 참가 기록도 남지 않는다 (트랜잭션 전체가 취소됨)
        assertThat(count("SELECT COUNT(*) FROM challenge_participants WHERE user_id = ?", me)).isEqualTo(1);
    }

    // ---------- helpers ----------

    private int runAll(Callable<Object> task) throws Exception {
        List<Callable<Object>> tasks = new ArrayList<>();
        for (int i = 0; i < THREADS; i++) {
            tasks.add(task);
        }
        return runAll(tasks);
    }

    /** 모두 한꺼번에 출발시키고 성공한 수를 센다 */
    private int runAll(List<Callable<Object>> tasks) throws Exception {
        ExecutorService pool = Executors.newFixedThreadPool(tasks.size());
        CountDownLatch start = new CountDownLatch(1);
        try {
            List<Future<Object>> futures = new ArrayList<>();
            for (Callable<Object> task : tasks) {
                futures.add(pool.submit(() -> {
                    start.await();
                    return task.call();
                }));
            }
            start.countDown();
            int ok = 0;
            for (Future<Object> f : futures) {
                try {
                    f.get();
                    ok++;
                } catch (Exception ignored) {
                    // 한도·잔액 부족·중복으로 거절된 요청
                }
            }
            return ok;
        } finally {
            pool.shutdownNow();
        }
    }

    private Long betChallenge(Long hostId, long fee) {
        LocalDate today = LocalDate.now(clock);
        Challenge c = challengeService.create(hostId, new ChallengeCreateRequest(1, "동시 참여", "테스트", ChallengeMode.BET,
                null, today.plusDays(1), today.plusDays(7), FrequencyType.DAILY, null, fee, 10, null, null, false));
        challengeIds.add(c.getId());
        return c.getId();
    }

    private Long newUser() {
        String suffix = UUID.randomUUID().toString().replace("-", "").substring(0, 12);
        Long id = userRepository.saveAndFlush(User.createWithPassword(suffix + "@example.com", "unused", "c" + suffix,
                "c".repeat(52) + suffix)).getId();
        userIds.add(id);
        return id;
    }

    private long count(String sql, Object arg) {
        Long n = jdbc.queryForObject(sql, Long.class, arg);
        return n == null ? 0 : n;
    }
}
