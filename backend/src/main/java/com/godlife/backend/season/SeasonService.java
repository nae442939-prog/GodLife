package com.godlife.backend.season;

import com.godlife.backend.notification.Notification;
import com.godlife.backend.notification.NotificationService;
import com.godlife.backend.wallet.PointSource;
import com.godlife.backend.wallet.PointTxType;
import com.godlife.backend.wallet.WalletService;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.boot.context.event.ApplicationReadyEvent;
import org.springframework.context.event.EventListener;
import org.springframework.jdbc.core.namedparam.MapSqlParameterSource;
import org.springframework.jdbc.core.namedparam.NamedParameterJdbcTemplate;
import org.springframework.scheduling.annotation.Scheduled;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;
import org.springframework.transaction.support.TransactionTemplate;

import java.time.Clock;
import java.time.DayOfWeek;
import java.time.LocalDate;
import java.util.ArrayList;
import java.util.List;
import java.util.Map;

/**
 * 시즌제 랭킹: 주간(월~일) · 월간(1일~말일) 두 시즌이 같이 돌아간다.
 * 점수는 그 시즌 기간 동안 승인된 인증 횟수(= 일반 랭킹과 같은 '성공' 기준). 시즌이 끝나면
 * 상위 3명에게 차등 보너스(1위 5,000P · 2위 3,000P · 3위 1,000P, reward 포인트 — 상점에서만 쓴다)를 주고 알림을 보낸다.
 * 다음 시즌은 바로 새로 시작한다 (끊기지 않는다).
 * 매일 자정(서울 시간) 따로 돈다 — 지갑에 보너스를 지급하는 로직이라 챌린지 정산 배치(ChallengeLifecycleService)와는
 * 독립적으로 둔다(정산 테스트가 날짜를 여러 번 돌릴 때 시즌 보너스까지 같이 받는 걸 막기 위함).
 * 서버가 꺼져 있었을 수 있어 켜질 때도 돈다. 여러 번 돌아도 결과가 같다: 순위는 덮어써도 되고,
 * 보너스 지급은 지갑의 멱등 키로 한 번만 나간다.
 */
@Slf4j
@Service
@RequiredArgsConstructor
public class SeasonService {

    public enum Type {
        WEEKLY, MONTHLY
    }

    /** 순위별 보너스(reward 포인트). 공동 순위면 그 순위 보너스를 각자 받는다 (예: 1위가 둘이면 둘 다 5,000P, 2위는 없음) */
    private static final Map<Integer, Long> BONUS = Map.of(1, 5_000L, 2, 3_000L, 3, 1_000L);
    private static final Map<Type, String> TYPE_LABEL = Map.of(Type.WEEKLY, "이번 주", Type.MONTHLY, "이번 달");
    /** 화면에 보여줄 상위 인원 (보너스는 이 중 1~3위만) */
    public static final int TOP = 50;

    private final NamedParameterJdbcTemplate jdbc;
    private final WalletService walletService;
    private final NotificationService notificationService;
    private final TransactionTemplate transactionTemplate;
    private final Clock clock;

    /** 테스트는 켜질 때 돌지 않게 끈다 (테스트가 직접 run 을 부른다) — ChallengeLifecycleService 와 같은 설정값을 쓴다 */
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

    public record Season(long id, Type type, LocalDate start, LocalDate end, String status, boolean bonusPaid) {
    }

    public record Entry(int rank, long userId, String nickname, String profileImageUrl, long score, long bonusPoints,
                        boolean mine) {
    }

    /**
     * 시즌 하나 보기 (지금 진행 중인 시즌이든, 끝난 시즌이든 같은 모양).
     * bonusConfirmed = false 면 진행 중이라 지금 순위대로라면 받을 보너스를 보여주는 것뿐, 시즌이 끝나야 확정 · 지급된다.
     */
    public record SeasonView(long id, Type type, LocalDate start, LocalDate end, String status, long daysLeft,
                             boolean bonusConfirmed, List<Entry> top, Entry me, Long previousSeasonId) {
    }

    /** today 기준으로: 이번 주 · 이번 달 시즌이 없으면 만들고, 끝난 시즌은 정산(순위 · 보너스)한다. */
    public void run(LocalDate today) {
        for (Type type : Type.values()) {
            try {
                ensureActive(type, today);
            } catch (RuntimeException e) {
                log.error("{} 시즌 시작 실패", type, e);
            }
            for (Season season : findActive(type)) {
                if (!season.end().isBefore(today)) {
                    continue;
                }
                try {
                    transactionTemplate.executeWithoutResult(status -> close(season));
                } catch (RuntimeException e) {
                    log.error("{} 시즌(id={}) 정산 실패", type, season.id(), e);
                }
            }
        }
    }

    /** today 가 속한 기간의 시즌이 없으면 하나 만든다 (주간 = 월~일, 월간 = 1일~말일) */
    private void ensureActive(Type type, LocalDate today) {
        LocalDate start = periodStart(type, today);
        LocalDate end = periodEnd(type, today);
        Integer exists = jdbc.queryForObject(
                "SELECT COUNT(*) FROM seasons WHERE type = :type AND start_date = :start",
                new MapSqlParameterSource("type", type.name()).addValue("start", start), Integer.class);
        if (exists != null && exists > 0) {
            return;
        }
        jdbc.update("INSERT INTO seasons (type, start_date, end_date, status, bonus_paid) "
                        + "VALUES (:type, :start, :end, 'ACTIVE', 0)",
                new MapSqlParameterSource("type", type.name()).addValue("start", start).addValue("end", end));
        log.info("{} 시즌 시작 {}~{}", type, start, end);
    }

    private List<Season> findActive(Type type) {
        return jdbc.query("SELECT id, type, start_date, end_date, status, bonus_paid FROM seasons "
                        + "WHERE type = :type AND status = 'ACTIVE' ORDER BY start_date",
                new MapSqlParameterSource("type", type.name()), this::toSeason);
    }

    /** 그 시즌 기간 동안 승인된 인증 수로 점수를 매긴다(동점은 같은 순위). 닫힌 시즌은 안 부른다 — 확정된 순위는 season_rankings 에 있다. */
    private List<ScoredUser> scoreNow(Season season) {
        List<ScoredUser> scored = jdbc.query("""
                SELECT p.user_id AS uid, COUNT(*) AS score, u.nickname AS nickname, u.profile_image_url AS img
                FROM verifications v JOIN challenge_participants p ON p.id = v.participant_id
                    JOIN users u ON u.id = p.user_id
                WHERE v.status = 'APPROVED' AND p.status <> 'KICKED'
                  AND v.verify_date BETWEEN :start AND :end
                GROUP BY p.user_id, u.nickname, u.profile_image_url
                ORDER BY score DESC, p.user_id
                """, new MapSqlParameterSource("start", season.start()).addValue("end", season.end()),
                (rs, i) -> new ScoredUser(rs.getLong("uid"), rs.getLong("score"), rs.getString("nickname"),
                        rs.getString("img")));
        int rank = 0;
        List<ScoredUser> ranked = new ArrayList<>(scored.size());
        for (int i = 0; i < scored.size(); i++) {
            ScoredUser s = scored.get(i);
            if (i == 0 || s.score() != scored.get(i - 1).score()) {
                rank = i + 1;
            }
            ranked.add(s.withRank(rank));
        }
        return ranked;
    }

    /** 순위를 확정해 season_rankings 에 적고, 1~3위에 보너스를 준 뒤 닫는다. 이미 닫혀 있으면 아무것도 안 한다. */
    private void close(Season season) {
        String status = jdbc.queryForObject("SELECT status FROM seasons WHERE id = :id",
                new MapSqlParameterSource("id", season.id()), String.class);
        if ("CLOSED".equals(status)) {
            return;
        }
        List<ScoredUser> scored = scoreNow(season);
        int paid = 0;
        for (ScoredUser s : scored) {
            long bonus = BONUS.getOrDefault(s.rank(), 0L);
            jdbc.update("""
                    INSERT INTO season_rankings (season_id, user_id, score, rank_no, bonus_points)
                    VALUES (:season, :user, :score, :rank, :bonus)
                    ON DUPLICATE KEY UPDATE score = VALUES(score), rank_no = VALUES(rank_no),
                        bonus_points = VALUES(bonus_points)
                    """, new MapSqlParameterSource("season", season.id()).addValue("user", s.uid())
                    .addValue("score", s.score()).addValue("rank", s.rank()).addValue("bonus", bonus));
            if (bonus > 0) {
                walletService.settle(s.uid(), PointTxType.SEASON_BONUS, PointSource.REWARD, bonus, "season",
                        season.id(), "season:%d:%d".formatted(season.id(), s.uid()));
                notificationService.notify(s.uid(), Notification.Type.SEASON,
                        "%s 시즌 %d위에 올랐어요 🏆".formatted(TYPE_LABEL.get(season.type()), s.rank()),
                        "%,dP 보너스를 받았어요. 포인트 상점에서 쓸 수 있어요.".formatted(bonus),
                        "/rankings?tab=season", "season:%d:%d".formatted(season.id(), s.uid()));
                paid++;
            }
        }
        jdbc.update("UPDATE seasons SET status = 'CLOSED', bonus_paid = 1 WHERE id = :id",
                new MapSqlParameterSource("id", season.id()));
        log.info("{} 시즌(id={}) {}~{} 정산: {}명 순위, {}명 보너스", season.type(), season.id(), season.start(), season.end(),
                scored.size(), paid);
    }

    /** 지금 진행 중인 시즌 (상위 50명 + 내 순위, 진행 중이면 실시간 순위). 아직 시즌이 없으면(배치가 안 돈 상태) null */
    @Transactional(readOnly = true)
    public SeasonView current(Type type, Long viewerId) {
        List<Season> active = findActive(type);
        if (active.isEmpty()) {
            return null;
        }
        return view(active.get(active.size() - 1), viewerId);
    }

    /** 시즌 하나 상세 (지난 시즌 결과 보기) */
    @Transactional(readOnly = true)
    public SeasonView byId(long id, Long viewerId) {
        Season season = jdbc.query(
                "SELECT id, type, start_date, end_date, status, bonus_paid FROM seasons WHERE id = :id",
                new MapSqlParameterSource("id", id), this::toSeason).stream().findFirst().orElse(null);
        return season == null ? null : view(season, viewerId);
    }

    private SeasonView view(Season season, Long viewerId) {
        boolean closed = "CLOSED".equals(season.status());
        List<Entry> all = closed ? closedEntries(season, viewerId) : liveEntries(season, viewerId);
        List<Entry> top = all.size() > TOP ? all.subList(0, TOP) : all;
        Entry me = viewerId == null ? null : all.stream().filter(Entry::mine).findFirst().orElse(null);

        Long previousId = jdbc.query("""
                SELECT id FROM seasons WHERE type = :type AND status = 'CLOSED' AND start_date < :start
                ORDER BY start_date DESC LIMIT 1
                """, new MapSqlParameterSource("type", season.type().name()).addValue("start", season.start()),
                (rs, i) -> rs.getLong("id")).stream().findFirst().orElse(null);
        long daysLeft = Math.max(0, season.end().toEpochDay() - LocalDate.now(clock).toEpochDay());
        return new SeasonView(season.id(), season.type(), season.start(), season.end(), season.status(), daysLeft,
                closed, top, me, previousId);
    }

    /** 끝난 시즌: season_rankings 에 적힌 확정 순위 · 지급된 보너스 그대로 */
    private List<Entry> closedEntries(Season season, Long viewerId) {
        return jdbc.query("""
                SELECT r.rank_no, r.user_id, r.score, r.bonus_points, u.nickname, u.profile_image_url
                FROM season_rankings r JOIN users u ON u.id = r.user_id
                WHERE r.season_id = :season
                ORDER BY r.rank_no, r.user_id
                """, new MapSqlParameterSource("season", season.id()),
                (rs, i) -> new Entry(rs.getInt("rank_no"), rs.getLong("user_id"), rs.getString("nickname"),
                        rs.getString("profile_image_url"), rs.getLong("score"), rs.getLong("bonus_points"),
                        rs.getLong("user_id") == (viewerId == null ? -1 : viewerId)));
    }

    /** 진행 중인 시즌: 지금까지 쌓인 인증으로 실시간 계산. bonusPoints 는 '지금 순위를 유지하면' 받을 예상치일 뿐, 확정 · 지급 전이다 */
    private List<Entry> liveEntries(Season season, Long viewerId) {
        List<Entry> entries = new ArrayList<>();
        for (ScoredUser s : scoreNow(season)) {
            entries.add(new Entry(s.rank(), s.uid(), s.nickname(), s.profileImageUrl(), s.score(),
                    BONUS.getOrDefault(s.rank(), 0L), s.uid() == (viewerId == null ? -1 : viewerId)));
        }
        return entries;
    }

    private static LocalDate periodStart(Type type, LocalDate today) {
        return type == Type.WEEKLY ? today.minusDays(today.getDayOfWeek().getValue() - DayOfWeek.MONDAY.getValue())
                : today.withDayOfMonth(1);
    }

    private static LocalDate periodEnd(Type type, LocalDate today) {
        return type == Type.WEEKLY ? periodStart(type, today).plusDays(6) : today.withDayOfMonth(today.lengthOfMonth());
    }

    private Season toSeason(java.sql.ResultSet rs, int i) throws java.sql.SQLException {
        return new Season(rs.getLong("id"), Type.valueOf(rs.getString("type")), rs.getDate("start_date").toLocalDate(),
                rs.getDate("end_date").toLocalDate(), rs.getString("status"), rs.getBoolean("bonus_paid"));
    }

    /** rank 는 scoreNow() 가 매기기 전엔 0 (아직 모른다는 뜻) */
    private record ScoredUser(long uid, long score, String nickname, String profileImageUrl, int rank) {
        ScoredUser(long uid, long score, String nickname, String profileImageUrl) {
            this(uid, score, nickname, profileImageUrl, 0);
        }

        ScoredUser withRank(int rank) {
            return new ScoredUser(uid, score, nickname, profileImageUrl, rank);
        }
    }
}
