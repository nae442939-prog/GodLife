package com.godlife.backend.demo;

import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.jdbc.core.namedparam.MapSqlParameterSource;
import org.springframework.jdbc.core.namedparam.NamedParameterJdbcTemplate;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.sql.Date;
import java.time.LocalDate;
import java.time.temporal.ChronoUnit;

/**
 * 시연용 더미 데이터가 날짜가 지나도 그대로 보이게 한다 (app.demo.keep-dates=true 일 때만, 기본은 꺼짐).
 * 더미 회원(이메일이 @dummy.godlife)의 마지막 인증일이 늘 '오늘'이 되도록, 더미 회원이 연 챌린지와
 * 더미 회원의 활동(참여 · 인증 · 채팅 · 글 · 댓글) 날짜를 그만큼 뒤로 민다.
 * → 모집 중이던 더미 챌린지는 계속 모집 중, 진행 중이던 것은 계속 진행 중이고 랭킹도 비지 않는다.
 * 실제 회원의 활동(인증 · 지갑 · 주문 …)은 건드리지 않는다. 그래서 더미 챌린지에 실제 회원이 참여해도
 * 그 챌린지는 끝나지 않고 정산도 되지 않는다 (정산까지 보려면 직접 연 챌린지를 쓴다).
 * 자정 진행 관리(ChallengeLifecycleService)보다 먼저 돈다.
 */
@Slf4j
@Service
@RequiredArgsConstructor
public class DemoDataKeeper {

    private static final String DUMMY_USERS = "SELECT id FROM users WHERE email LIKE '%@dummy.godlife'";
    private static final String DUMMY_CHALLENGES = "SELECT id FROM challenges WHERE host_id IN (" + DUMMY_USERS + ")";
    /** MySQL 은 고치는 테이블을 같은 문장의 서브쿼리에서 바로 읽지 못해서 한 번 감싼다 */
    private static final String DUMMY_CHALLENGE_IDS = "SELECT id FROM (" + DUMMY_CHALLENGES + ") dc";
    private static final String DUMMY_PARTICIPANTS =
            "SELECT id FROM challenge_participants WHERE user_id IN (" + DUMMY_USERS + ")";

    private final NamedParameterJdbcTemplate jdbc;

    @Value("${app.demo.keep-dates:false}")
    private boolean enabled;

    /** 더미 회원의 마지막 인증일을 today 로 맞춘다. 민 날 수를 돌려준다 (이미 맞으면 0). 여러 번 돌아도 결과가 같다. */
    @Transactional
    public int roll(LocalDate today) {
        if (!enabled) {
            return 0;
        }
        Date last = jdbc.queryForObject(
                "SELECT MAX(verify_date) FROM verifications WHERE participant_id IN (" + DUMMY_PARTICIPANTS + ")",
                new MapSqlParameterSource(), Date.class);
        if (last == null) {
            return 0;
        }
        int days = (int) ChronoUnit.DAYS.between(last.toLocalDate(), today);
        if (days <= 0) {
            return 0;
        }
        MapSqlParameterSource p = new MapSqlParameterSource("n", days).addValue("today", today);
        // 날짜가 유니크 키에 든 테이블은 늦은 날짜부터 밀어야 밀린 날짜끼리 부딪히지 않는다
        jdbc.update("""
                UPDATE daily_settlements
                SET period_start = period_start + INTERVAL :n DAY, period_end = period_end + INTERVAL :n DAY,
                    settled_at = settled_at + INTERVAL :n DAY
                WHERE challenge_id IN (%s) ORDER BY period_start DESC
                """.formatted(DUMMY_CHALLENGES), p);
        jdbc.update("""
                UPDATE verifications
                SET verify_date = verify_date + INTERVAL :n DAY,
                    received_at = LEAST(received_at + INTERVAL :n DAY, NOW(3))
                WHERE participant_id IN (%s) ORDER BY verify_date DESC
                """.formatted(DUMMY_PARTICIPANTS), p);
        jdbc.update("""
                UPDATE challenges
                SET start_date = start_date + INTERVAL :n DAY, end_date = end_date + INTERVAL :n DAY,
                    created_at = created_at + INTERVAL :n DAY,
                    notice_updated_at = notice_updated_at + INTERVAL :n DAY
                WHERE id IN (%s)
                """.formatted(DUMMY_CHALLENGE_IDS), p);
        // 꺼 둔 사이에 시작일이 지나 '진행 중'으로 넘어간 것도 날짜를 밀고 나면 다시 모집 중이다
        jdbc.update("""
                UPDATE challenges SET status = 'RECRUITING'
                WHERE status = 'ONGOING' AND start_date > :today AND id IN (%s)
                """.formatted(DUMMY_CHALLENGE_IDS), p);
        jdbc.update("UPDATE challenge_participants SET joined_at = joined_at + INTERVAL :n DAY WHERE user_id IN ("
                + DUMMY_USERS + ")", p);
        jdbc.update("UPDATE chat_messages SET created_at = created_at + INTERVAL :n DAY WHERE sender_id IN ("
                + DUMMY_USERS + ")", p);
        jdbc.update("""
                UPDATE posts
                SET verify_date = verify_date + INTERVAL :n DAY, created_at = created_at + INTERVAL :n DAY,
                    updated_at = updated_at + INTERVAL :n DAY
                WHERE user_id IN (%s)
                """.formatted(DUMMY_USERS), p);
        jdbc.update("UPDATE post_comments SET created_at = created_at + INTERVAL :n DAY WHERE user_id IN ("
                + DUMMY_USERS + ")", p);
        jdbc.update("UPDATE comments SET created_at = created_at + INTERVAL :n DAY WHERE user_id IN ("
                + DUMMY_USERS + ")", p);
        log.info("더미 데이터 날짜를 {}일 밀었습니다 (마지막 더미 인증일 {} → {})", days, last, today);
        return days;
    }

    /**
     * 매일 챌린지에 참여 중인 더미 회원의 연속 기록을 인증 기록에 맞춘다 (오늘이나 어제에서 끝나는 연속 일수).
     * 자정 진행 관리가 '어제 인증이 없으면 0'으로 되돌리는데, 더미 회원은 이미 오늘 인증이 있어서 다시 맞춰 준다.
     */
    @Transactional
    public int syncStreaks(LocalDate today) {
        if (!enabled) {
            return 0;
        }
        return jdbc.update("""
                UPDATE challenge_participants p
                JOIN users u ON u.id = p.user_id AND u.email LIKE '%@dummy.godlife'
                JOIN challenges c ON c.id = p.challenge_id AND c.frequency_type = 'DAILY'
                LEFT JOIN (
                    SELECT participant_id, COUNT(*) AS len
                    FROM (
                        SELECT participant_id, verify_date,
                               verify_date - INTERVAL ROW_NUMBER() OVER (PARTITION BY participant_id ORDER BY verify_date) DAY AS run_key,
                               MAX(verify_date) OVER (PARTITION BY participant_id) AS last_date
                        FROM verifications WHERE status <> 'REJECTED'
                    ) v
                    WHERE last_date >= :today - INTERVAL 1 DAY
                      AND run_key = last_date - INTERVAL (SELECT COUNT(*) FROM verifications x
                                                          WHERE x.participant_id = v.participant_id
                                                            AND x.status <> 'REJECTED') DAY
                    GROUP BY participant_id
                ) s ON s.participant_id = p.id
                SET p.current_streak = COALESCE(s.len, 0),
                    p.max_streak = GREATEST(p.max_streak, COALESCE(s.len, 0))
                WHERE p.status = 'ACTIVE' AND p.current_streak <> COALESCE(s.len, 0)
                """, new MapSqlParameterSource("today", today));
    }
}
