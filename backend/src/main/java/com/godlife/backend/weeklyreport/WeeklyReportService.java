package com.godlife.backend.weeklyreport;

import com.godlife.backend.notification.Notification;
import com.godlife.backend.notification.NotificationService;
import com.godlife.backend.record.RecordService;
import com.godlife.backend.record.RecordService.DayResult;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.boot.context.event.ApplicationReadyEvent;
import org.springframework.context.event.EventListener;
import org.springframework.jdbc.core.namedparam.MapSqlParameterSource;
import org.springframework.jdbc.core.namedparam.NamedParameterJdbcTemplate;
import org.springframework.scheduling.annotation.Scheduled;
import org.springframework.stereotype.Service;
import tools.jackson.databind.ObjectMapper;

import java.math.BigDecimal;
import java.math.RoundingMode;
import java.time.Clock;
import java.time.DayOfWeek;
import java.time.LocalDate;
import java.util.ArrayList;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;

/**
 * 주간 회고 리포트: 한 주(월~일)의 인증 기록으로 성공률 · 요일별 · 챌린지별 통계와 코칭 문구를 만든다.
 * 성공 · 실패는 갓생기록 캘린더와 같은 기준(RecordService)으로 센다 — 쉬는 날은 세지 않는다.
 * - 끝난 주: 매일 자정 조금 지나(서울 시간) 지난주 리포트를 회원마다 하나씩 만들어 weekly_reports 에 남기고 알림을 보낸다.
 *   서버가 꺼져 있었을 수 있어 켜질 때도 돌고, 최근 4주까지 빠진 주를 채운다(알림은 바로 지난주 것만).
 *   (user_id, week_start) 가 유일해서 여러 번 돌아도 하나만 남는다. 한 번 만든 리포트는 그 뒤에 고치지 않는다.
 * - 이번 주: 저장하지 않고 볼 때마다 지금까지의 기록으로 계산한다(진행 중).
 * 코칭 문구는 외부 AI API 가 아니라 기록을 보고 규칙으로 고른다 (WeeklyCoach).
 */
@Slf4j
@Service
@RequiredArgsConstructor
public class WeeklyReportService {

    /** 서버가 꺼져 있던 동안 빠진 주를 이만큼까지 채운다 */
    static final int BACKFILL_WEEKS = 4;
    /** 화면에서 넘겨 볼 수 있는 지난 리포트 수 */
    static final int HISTORY = 12;

    private final NamedParameterJdbcTemplate jdbc;
    private final RecordService recordService;
    private final NotificationService notificationService;
    private final ObjectMapper objectMapper;
    private final Clock clock;

    /** 테스트는 켜질 때 돌지 않게 끈다 (테스트가 직접 run 을 부른다) — ChallengeLifecycleService 와 같은 설정값을 쓴다 */
    @Value("${app.lifecycle.run-on-startup:true}")
    private boolean runOnStartup;

    @Scheduled(cron = "0 5 0 * * *", zone = "Asia/Seoul")
    public void midnight() {
        run(LocalDate.now(clock));
    }

    @EventListener(ApplicationReadyEvent.class)
    public void onStartup() {
        if (runOnStartup) {
            run(LocalDate.now(clock));
        }
    }

    /** weekday: 1=월 ~ 7=일 */
    public record WeekdayStat(int weekday, int done, int fail) {
    }

    public record ChallengeStat(Long challengeId, String title, int categoryId, int done, int fail) {
    }

    /** @param previousRate 그 전 주 성공률(%). 그 전 주 리포트가 없으면 null */
    public record Stats(int done, int total, List<WeekdayStat> weekdays, List<ChallengeStat> challenges,
                        Integer previousRate) {
    }

    /**
     * @param inProgress   아직 끝나지 않은 이번 주면 true (볼 때마다 다시 계산한 것)
     * @param successRate  인증한 수 / 인증해야 했던 수 (%). 인증할 날이 없었으면 null
     * @param worstWeekday 가장 많이 놓친 요일(1=월 ~ 7=일). 놓친 날이 없으면 null
     * @param weeks        넘겨 볼 수 있는 주의 시작일(월요일)들, 최근 것부터. 맨 앞은 이번 주
     */
    public record Report(LocalDate weekStart, LocalDate weekEnd, boolean inProgress, Integer successRate,
                         Integer worstWeekday, Stats stats, String coaching, List<LocalDate> weeks) {
    }

    /** 끝난 주 가운데 아직 리포트가 없는 것을 만든다. 만든 리포트 수를 돌려준다. */
    public int run(LocalDate today) {
        LocalDate thisWeek = monday(today);
        int made = 0;
        // 오래된 주부터: 다음 주 리포트가 '그 전 주 성공률'을 읽을 수 있게
        for (int back = BACKFILL_WEEKS; back >= 1; back--) {
            LocalDate weekStart = thisWeek.minusWeeks(back);
            for (Long userId : usersWithout(weekStart)) {
                try {
                    if (generate(userId, weekStart, back == 1)) {
                        made++;
                    }
                } catch (RuntimeException e) {
                    log.error("주간 회고 리포트 생성 실패 (user={}, week={})", userId, weekStart, e);
                }
            }
        }
        if (made > 0) {
            log.info("주간 회고 리포트 {}건 생성", made);
        }
        return made;
    }

    /**
     * 내 주간 회고. week 를 주면 그 날짜가 속한 주(앞날이면 이번 주), 안 주면 이번 주 —
     * 이번 주에 아직 기록이 없고 지난 리포트가 있으면 가장 최근 리포트를 보여 준다(월요일 아침에 빈 화면이 안 되게).
     */
    public Report view(Long userId, LocalDate week) {
        LocalDate today = LocalDate.now(clock);
        LocalDate thisWeek = monday(today);
        List<LocalDate> stored = jdbc.queryForList("""
                SELECT week_start FROM weekly_reports WHERE user_id = :user AND week_start < :thisWeek
                ORDER BY week_start DESC LIMIT :limit
                """, new MapSqlParameterSource("user", userId).addValue("thisWeek", thisWeek)
                .addValue("limit", HISTORY), LocalDate.class);
        List<LocalDate> weeks = new ArrayList<>(stored.size() + 1);
        weeks.add(thisWeek);
        weeks.addAll(stored);

        LocalDate target;
        if (week == null) {
            Report live = computed(userId, thisWeek, today, true, weeks);
            if (live.stats().total() > 0 || stored.isEmpty()) {
                return live;
            }
            target = stored.get(0);
        } else {
            target = monday(week).isAfter(thisWeek) ? thisWeek : monday(week);
        }
        if (target.equals(thisWeek)) {
            return computed(userId, thisWeek, today, true, weeks);
        }
        Report saved = saved(userId, target, weeks);
        // 리포트가 없는 지난 주(가입 전이거나 4주보다 오래 꺼져 있던 때)는 그 자리에서 계산해 보여 준다
        return saved != null ? saved : computed(userId, target, target.plusDays(6), false, weeks);
    }

    private List<Long> usersWithout(LocalDate weekStart) {
        return jdbc.queryForList("""
                SELECT DISTINCT p.user_id
                FROM challenge_participants p JOIN challenges c ON c.id = p.challenge_id
                    JOIN users u ON u.id = p.user_id AND u.status = 'ACTIVE'
                WHERE p.status IN ('ACTIVE', 'COMPLETED', 'FAILED', 'GAVE_UP')
                  AND c.start_date <= :end AND c.end_date >= :start
                  AND NOT EXISTS (SELECT 1 FROM weekly_reports w
                                  WHERE w.user_id = p.user_id AND w.week_start = :start)
                ORDER BY p.user_id
                """, new MapSqlParameterSource("start", weekStart).addValue("end", weekStart.plusDays(6)),
                Long.class);
    }

    /** 그 주 리포트를 만들어 남긴다. 그 주에 인증할 날이 하루도 없던 회원은 만들지 않는다. */
    private boolean generate(Long userId, LocalDate weekStart, boolean notify) {
        Stats stats = stats(userId, weekStart, weekStart.plusDays(6));
        if (stats.total() == 0) {
            return false;
        }
        Integer rate = rate(stats);
        Integer worst = worstWeekday(stats);
        int inserted = jdbc.update("""
                INSERT IGNORE INTO weekly_reports (user_id, week_start, success_rate, worst_weekday, stats_json,
                                                   coaching_text)
                VALUES (:user, :week, :rate, :worst, :stats, :coaching)
                """, new MapSqlParameterSource("user", userId).addValue("week", weekStart)
                .addValue("rate", BigDecimal.valueOf(100L * stats.done())
                        .divide(BigDecimal.valueOf(stats.total()), 2, RoundingMode.HALF_UP))
                .addValue("worst", worst).addValue("stats", objectMapper.writeValueAsString(stats))
                .addValue("coaching", WeeklyCoach.write(stats, rate, worst, true)));
        if (inserted > 0 && notify) {
            notificationService.notify(userId, Notification.Type.WEEKLY_REPORT, "지난주 회고 리포트가 도착했어요 📊",
                    "성공률 %d%% · %d번 중 %d번 인증했어요. 어떤 요일에 놓쳤는지 확인해 보세요."
                            .formatted(rate, stats.total(), stats.done()),
                    "/records/report?week=" + weekStart, "weekly-report:%d:%s".formatted(userId, weekStart));
        }
        return inserted > 0;
    }

    private Report saved(Long userId, LocalDate weekStart, List<LocalDate> weeks) {
        return jdbc.query("""
                SELECT ROUND(success_rate) AS rate, worst_weekday, stats_json, coaching_text
                FROM weekly_reports WHERE user_id = :user AND week_start = :week
                """, new MapSqlParameterSource("user", userId).addValue("week", weekStart),
                (rs, i) -> new Report(weekStart, weekStart.plusDays(6), false, rs.getInt("rate"),
                        (Integer) rs.getObject("worst_weekday"),
                        objectMapper.readValue(rs.getString("stats_json"), Stats.class),
                        rs.getString("coaching_text"), weeks)).stream().findFirst().orElse(null);
    }

    private Report computed(Long userId, LocalDate weekStart, LocalDate to, boolean inProgress,
                            List<LocalDate> weeks) {
        Stats stats = stats(userId, weekStart, to);
        Integer rate = rate(stats);
        Integer worst = worstWeekday(stats);
        return new Report(weekStart, weekStart.plusDays(6), inProgress, rate, worst, stats,
                WeeklyCoach.write(stats, rate, worst, !inProgress), weeks);
    }

    /** weekStart~to 의 성공 · 실패를 요일별 · 챌린지별로 센다 ('오늘 아직'은 성공도 실패도 아니라 세지 않는다) */
    private Stats stats(Long userId, LocalDate weekStart, LocalDate to) {
        int[] done = new int[8];
        int[] fail = new int[8];
        Map<Long, int[]> counts = new LinkedHashMap<>();
        Map<Long, DayResult> names = new LinkedHashMap<>();
        for (DayResult r : recordService.results(userId, weekStart, to)) {
            boolean ok = RecordService.DONE.equals(r.result());
            if (!ok && !RecordService.FAIL.equals(r.result())) {
                continue;
            }
            int weekday = r.date().getDayOfWeek().getValue();
            (ok ? done : fail)[weekday]++;
            counts.computeIfAbsent(r.challengeId(), k -> new int[2])[ok ? 0 : 1]++;
            names.putIfAbsent(r.challengeId(), r);
        }
        List<WeekdayStat> weekdays = new ArrayList<>(7);
        int doneAll = 0;
        int failAll = 0;
        for (int d = 1; d <= 7; d++) {
            weekdays.add(new WeekdayStat(d, done[d], fail[d]));
            doneAll += done[d];
            failAll += fail[d];
        }
        List<ChallengeStat> challenges = new ArrayList<>(counts.size());
        counts.forEach((id, c) -> challenges.add(
                new ChallengeStat(id, names.get(id).title(), names.get(id).categoryId(), c[0], c[1])));

        Integer previousRate = jdbc.query("""
                SELECT ROUND(success_rate) AS rate FROM weekly_reports WHERE user_id = :user AND week_start = :week
                """, new MapSqlParameterSource("user", userId).addValue("week", weekStart.minusWeeks(1)),
                (rs, i) -> rs.getInt("rate")).stream().findFirst().orElse(null);
        return new Stats(doneAll, doneAll + failAll, weekdays, challenges, previousRate);
    }

    private static Integer rate(Stats stats) {
        return stats.total() == 0 ? null : (int) Math.round(100.0 * stats.done() / stats.total());
    }

    /** 가장 많이 놓친 요일. 같으면 앞 요일. 놓친 날이 없으면 null */
    private static Integer worstWeekday(Stats stats) {
        WeekdayStat worst = null;
        for (WeekdayStat w : stats.weekdays()) {
            if (w.fail() > 0 && (worst == null || w.fail() > worst.fail())) {
                worst = w;
            }
        }
        return worst == null ? null : worst.weekday();
    }

    private static LocalDate monday(LocalDate date) {
        return date.minusDays(date.getDayOfWeek().getValue() - DayOfWeek.MONDAY.getValue());
    }
}
