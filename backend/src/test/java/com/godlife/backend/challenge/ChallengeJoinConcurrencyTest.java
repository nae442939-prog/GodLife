package com.godlife.backend.challenge;

import com.godlife.backend.challenge.dto.ChallengeCreateRequest;
import com.godlife.backend.common.error.BusinessException;
import com.godlife.backend.common.error.ErrorCode;
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
import java.util.concurrent.ConcurrentLinkedQueue;
import java.util.concurrent.CountDownLatch;
import java.util.concurrent.ExecutorService;
import java.util.concurrent.Executors;
import java.util.concurrent.TimeUnit;
import java.util.concurrent.atomic.AtomicInteger;

import static org.assertj.core.api.Assertions.assertThat;

/**
 * 여러 명이 동시에 참여를 눌러도 정원을 넘지 않는지. 스레드마다 트랜잭션이 따로 돌아야 해서
 * 이 테스트는 롤백하지 않고, 만든 데이터를 끝나고 직접 지운다.
 */
@SpringBootTest
@ActiveProfiles("test")
class ChallengeJoinConcurrencyTest {

    private static final int CAPACITY = 3;
    private static final int THREADS = 10;

    @Autowired ChallengeService challengeService;
    @Autowired UserRepository userRepository;
    @Autowired JdbcTemplate jdbc;
    @Autowired Clock clock;

    private final List<Long> userIds = new ArrayList<>();
    private Long challengeId;

    @AfterEach
    void cleanUp() {
        if (challengeId != null) {
            jdbc.update("DELETE FROM challenge_participants WHERE challenge_id = ?", challengeId);
            jdbc.update("DELETE FROM challenges WHERE id = ?", challengeId);
        }
        userIds.forEach(id -> jdbc.update("DELETE FROM users WHERE id = ?", id));
    }

    @Test
    @DisplayName("정원 3명 챌린지에 10명이 동시에 참여하면 정확히 3명만 들어간다")
    void capacityHoldsUnderConcurrency() throws Exception {
        LocalDate today = LocalDate.now(clock);
        Long hostId = newUser();
        challengeId = challengeService.create(hostId, new ChallengeCreateRequest(1, "동시성", "테스트",
                ChallengeMode.FREE, null, today.plusDays(1), today.plusDays(3), FrequencyType.DAILY, null, null,
                CAPACITY, null, null, false)).getId();

        List<Long> joiners = new ArrayList<>();
        for (int i = 0; i < THREADS; i++) {
            joiners.add(newUser());
        }

        AtomicInteger success = new AtomicInteger();
        ConcurrentLinkedQueue<Throwable> unexpected = new ConcurrentLinkedQueue<>();
        CountDownLatch start = new CountDownLatch(1);
        ExecutorService pool = Executors.newFixedThreadPool(THREADS);
        for (Long userId : joiners) {
            pool.submit(() -> {
                try {
                    start.await();
                    challengeService.join(challengeId, userId);
                    success.incrementAndGet();
                } catch (BusinessException e) {
                    if (e.getErrorCode() != ErrorCode.CHALLENGE_FULL) {
                        unexpected.add(e);
                    }
                } catch (Throwable t) {
                    unexpected.add(t);
                }
                return null;
            });
        }
        start.countDown();
        pool.shutdown();
        assertThat(pool.awaitTermination(30, TimeUnit.SECONDS)).isTrue();

        assertThat(unexpected).isEmpty();
        assertThat(success.get()).isEqualTo(CAPACITY);
        assertThat(jdbc.queryForObject("SELECT participant_count FROM challenges WHERE id = ?", Integer.class,
                challengeId)).isEqualTo(CAPACITY);
        assertThat(jdbc.queryForObject(
                "SELECT COUNT(*) FROM challenge_participants WHERE challenge_id = ? AND status = 'ACTIVE'",
                Integer.class, challengeId)).isEqualTo(CAPACITY);
    }

    private Long newUser() {
        String suffix = UUID.randomUUID().toString().replace("-", "").substring(0, 12);
        User user = userRepository.saveAndFlush(User.createWithPassword(suffix + "@example.com", "unused",
                "c" + suffix, "c".repeat(52) + suffix));
        userIds.add(user.getId());
        return user.getId();
    }
}
