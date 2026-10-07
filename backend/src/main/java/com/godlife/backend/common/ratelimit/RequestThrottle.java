package com.godlife.backend.common.ratelimit;

import com.godlife.backend.common.error.BusinessException;
import com.godlife.backend.common.error.ErrorCode;
import lombok.RequiredArgsConstructor;
import org.springframework.stereotype.Component;

import java.time.Clock;
import java.time.Duration;
import java.time.Instant;
import java.util.ArrayDeque;
import java.util.Deque;
import java.util.Map;
import java.util.concurrent.ConcurrentHashMap;

/**
 * 키(예: "sms:1.2.3.4")별 요청 횟수 제한. 서버 메모리에 두므로 재시작하면 초기화되고, 서버 1대 기준이다.
 * (여러 대로 늘리면 Redis 같은 공유 저장소로 옮겨야 한다)
 */
@Component
@RequiredArgsConstructor
public class RequestThrottle {

    private static final int MAX_KEYS = 10_000;

    private final Map<String, Deque<Instant>> hits = new ConcurrentHashMap<>();
    private final Clock clock;

    /** window 동안 max 번까지 허용한다. 넘으면 TOO_MANY_REQUESTS. */
    public void check(String key, int max, Duration window) {
        Instant now = clock.instant();
        Instant from = now.minus(window);
        if (hits.size() > MAX_KEYS) {
            hits.values().removeIf(q -> {
                synchronized (q) {
                    return q.isEmpty() || q.peekLast().isBefore(from);
                }
            });
        }
        Deque<Instant> q = hits.computeIfAbsent(key, k -> new ArrayDeque<>());
        synchronized (q) {
            while (!q.isEmpty() && q.peekFirst().isBefore(from)) {
                q.pollFirst();
            }
            if (q.size() >= max) {
                java.time.Duration wait = java.time.Duration.between(now, q.peekFirst().plus(window));
                throw new BusinessException(ErrorCode.TOO_MANY_REQUESTS,
                        "요청이 너무 많아요. " + BusinessException.after(wait) + " 다시 시도해 주세요.");
            }
            q.addLast(now);
        }
    }
}
