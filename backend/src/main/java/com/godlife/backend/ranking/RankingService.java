package com.godlife.backend.ranking;

import com.godlife.backend.challenge.Challenge;
import com.godlife.backend.challenge.ChallengeParticipant;
import com.godlife.backend.challenge.ChallengeParticipantRepository;
import com.godlife.backend.challenge.ChallengeRepository;
import com.godlife.backend.challenge.ParticipantStatus;
import com.godlife.backend.challenge.dto.CategoryResponse;
import com.godlife.backend.follow.FollowRepository;
import com.godlife.backend.ranking.dto.ChallengeRankingResponse;
import com.godlife.backend.ranking.dto.UserRankingResponse;
import com.godlife.backend.ranking.dto.UserRankingResponse.Entry;
import com.godlife.backend.settlement.Period;
import com.godlife.backend.verification.VerificationRepository;
import lombok.RequiredArgsConstructor;
import org.springframework.jdbc.core.namedparam.MapSqlParameterSource;
import org.springframework.jdbc.core.namedparam.NamedParameterJdbcTemplate;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.time.Clock;
import java.time.LocalDate;
import java.time.temporal.ChronoUnit;
import java.util.ArrayList;
import java.util.Collection;
import java.util.Comparator;
import java.util.HashSet;
import java.util.List;
import java.util.Map;
import java.util.Set;

/**
 * 랭킹 메뉴.
 * - 전체(개인): 누적 성공 · 이번 달 성공 · 최장 연속 (· 이번 달 보상 · 누적 성공률). 상위 N명 + 내 순위. 같은 값이면 같은 순위.
 *   '성공' = 승인된 인증 한 번 (거절·검토 중인 사진은 세지 않는다).
 * - 친구: 같은 기준을 나 + 내가 팔로우한 사람 안에서만.
 * - 챌린지(팀): 진행 중인 공개 챌린지를 참가자 평균 달성률(어제까지 끝난 기간 기준)로.
 */
@Service
@RequiredArgsConstructor
public class RankingService {

    public static final int TOP = 50;
    private static final int CHALLENGE_TOP = 30;
    /** 팀 랭킹은 2명 이상 함께하는 챌린지만 */
    private static final int MIN_TEAM = 2;

    /** 기준별 '회원 id → 값' 집계 SQL. 모두 (uid, val) 두 칸을 돌려준다. */
    private static final Map<RankingMetric, String> METRIC_SQL = Map.of(
            RankingMetric.TOTAL_SUCCESS, """
                    SELECT p.user_id AS uid, COUNT(*) AS val
                    FROM verifications v JOIN challenge_participants p ON p.id = v.participant_id
                    WHERE v.status = 'APPROVED' AND p.status <> 'KICKED'
                    GROUP BY p.user_id
                    """,
            RankingMetric.MONTH_VERIFY, """
                    SELECT p.user_id AS uid, COUNT(*) AS val
                    FROM verifications v JOIN challenge_participants p ON p.id = v.participant_id
                    WHERE v.status = 'APPROVED' AND v.verify_date >= :monthStart AND p.status <> 'KICKED'
                    GROUP BY p.user_id
                    """,
            RankingMetric.MAX_STREAK, """
                    SELECT p.user_id AS uid, MAX(p.max_streak) AS val
                    FROM challenge_participants p
                    WHERE p.status NOT IN ('LEFT', 'KICKED')
                    GROUP BY p.user_id
                    """,
            RankingMetric.MONTH_REWARD, """
                    SELECT w.user_id AS uid, SUM(t.amount) AS val
                    FROM point_transactions t JOIN wallets w ON w.id = t.wallet_id
                    WHERE t.type = 'REWARD' AND t.created_at >= :monthStart
                    GROUP BY w.user_id
                    """,
            // 끝난 챌린지에서 필요한 인증 수(주 N회는 마지막 짧은 주를 남은 일수만큼) 중 채운 비율
            RankingMetric.SUCCESS_RATE, """
                    SELECT e.user_id AS uid, ROUND(100 * SUM(LEAST(e.success_days, e.target)) / SUM(e.target), 1) AS val
                    FROM (
                        SELECT p.user_id, p.success_days,
                               CASE WHEN c.frequency_type = 'DAILY' THEN DATEDIFF(c.end_date, c.start_date) + 1
                                    ELSE FLOOR((DATEDIFF(c.end_date, c.start_date) + 1) / 7) * c.weekly_count
                                         + LEAST(c.weekly_count, MOD(DATEDIFF(c.end_date, c.start_date) + 1, 7))
                               END AS target
                        FROM challenge_participants p JOIN challenges c ON c.id = p.challenge_id
                        WHERE c.status IN ('ENDED', 'SETTLED') AND p.status IN ('COMPLETED', 'FAILED', 'GAVE_UP')
                    ) e
                    GROUP BY e.user_id
                    """);

    private final NamedParameterJdbcTemplate jdbc;
    private final FollowRepository followRepository;
    private final ChallengeRepository challengeRepository;
    private final ChallengeParticipantRepository participantRepository;
    private final VerificationRepository verificationRepository;
    private final Clock clock;

    /** 전체 랭킹 상위 N명 + 내 줄 (viewerId 가 없거나 기록이 없으면 me = null) */
    @Transactional(readOnly = true)
    public UserRankingResponse users(RankingMetric metric, Long viewerId) {
        return users(metric, viewerId, null);
    }

    /** 친구 랭킹: 나 + 내가 팔로우한 사람끼리만 같은 기준으로 */
    @Transactional(readOnly = true)
    public UserRankingResponse friends(RankingMetric metric, Long viewerId) {
        Set<Long> scope = new HashSet<>(followRepository.followingIds(viewerId));
        scope.add(viewerId);
        return users(metric, viewerId, scope);
    }

    /** scope 가 있으면 그 회원들 안에서만 순위를 매긴다 (없으면 전체) */
    private UserRankingResponse users(RankingMetric metric, Long viewerId, Collection<Long> scope) {
        String metricSql = METRIC_SQL.get(metric);
        MapSqlParameterSource params = new MapSqlParameterSource()
                .addValue("monthStart", LocalDate.now(clock).withDayOfMonth(1))
                .addValue("limit", TOP)
                .addValue("scope", scope);
        String inScope = scope == null ? "" : " AND r.uid IN (:scope)";

        List<Row> rows = jdbc.query("""
                SELECT r.uid, r.val, u.nickname, u.profile_image_url
                FROM (%s) r JOIN users u ON u.id = r.uid
                WHERE u.status = 'ACTIVE' AND r.val > 0%s
                ORDER BY r.val DESC, u.id
                LIMIT :limit
                """.formatted(metricSql, inScope), params,
                (rs, i) -> new Row(rs.getLong("uid"), rs.getDouble("val"), rs.getString("nickname"),
                        rs.getString("profile_image_url")));

        List<Entry> top = new ArrayList<>(rows.size());
        int rank = 0;
        for (int i = 0; i < rows.size(); i++) {
            Row r = rows.get(i);
            if (i == 0 || r.val() != rows.get(i - 1).val()) {
                rank = i + 1;
            }
            top.add(new Entry(rank, r.uid(), r.nickname(), r.profileImageUrl(), r.val(), r.uid().equals(viewerId)));
        }
        return new UserRankingResponse(top, viewerId == null ? null : me(metricSql, inScope, params, viewerId));
    }

    /** 한 사람의 기준 값 (기록이 없으면 0) — 프로필용 */
    @Transactional(readOnly = true)
    public double valueOf(RankingMetric metric, Long userId) {
        Double v = valueOrNull(metric, userId);
        return v == null ? 0 : v;
    }

    /** 한 사람의 기준 값 (기록이 없으면 null: 누적 성공률처럼 '없음'과 0을 구분할 때) */
    @Transactional(readOnly = true)
    public Double valueOrNull(RankingMetric metric, Long userId) {
        List<Double> v = jdbc.query("SELECT r.val FROM (%s) r WHERE r.uid = :me".formatted(METRIC_SQL.get(metric)),
                new MapSqlParameterSource()
                        .addValue("monthStart", LocalDate.now(clock).withDayOfMonth(1))
                        .addValue("me", userId),
                (rs, i) -> rs.getDouble("val"));
        return v.isEmpty() ? null : v.get(0);
    }

    /** 내 값과 순위 (나보다 값이 큰 사람 수 + 1) */
    private Entry me(String metricSql, String inScope, MapSqlParameterSource params, Long viewerId) {
        params.addValue("me", viewerId);
        List<Row> mine = jdbc.query("""
                SELECT r.uid, r.val, u.nickname, u.profile_image_url
                FROM (%s) r JOIN users u ON u.id = r.uid
                WHERE r.uid = :me AND r.val > 0
                """.formatted(metricSql), params,
                (rs, i) -> new Row(rs.getLong("uid"), rs.getDouble("val"), rs.getString("nickname"),
                        rs.getString("profile_image_url")));
        if (mine.isEmpty()) {
            return null;
        }
        Row me = mine.get(0);
        params.addValue("myVal", me.val());
        Integer higher = jdbc.queryForObject("""
                SELECT COUNT(*) FROM (%s) r JOIN users u ON u.id = r.uid
                WHERE u.status = 'ACTIVE' AND r.val > :myVal%s
                """.formatted(metricSql, inScope), params, Integer.class);
        return new Entry((higher == null ? 0 : higher) + 1, me.uid(), me.nickname(), me.profileImageUrl(), me.val(),
                true);
    }

    /** 챌린지(팀) 랭킹: 진행 중인 공개 챌린지, 2명 이상, 끝난 기간이 하루 이상 있는 것만 */
    @Transactional(readOnly = true)
    public List<ChallengeRankingResponse> challenges() {
        LocalDate today = LocalDate.now(clock);
        List<Scored> scored = new ArrayList<>();
        for (Challenge c : challengeRepository.findRankingCandidates(today)) {
            List<ChallengeParticipant> members = participantRepository
                    .findByChallengeIdAndStatusIn(c.getId(), List.of(ParticipantStatus.ACTIVE));
            if (members.size() < MIN_TEAM) {
                continue;
            }
            List<Period> done = Period.all(c).stream().filter(p -> p.end().isBefore(today)).toList();
            if (done.isEmpty()) {
                continue;
            }
            int required = done.stream().mapToInt(Period::required).sum();
            long verified = verificationRepository.countByParticipants(
                            members.stream().map(ChallengeParticipant::getId).toList(),
                            done.get(0).start(), done.get(done.size() - 1).end()).stream()
                    .mapToLong(row -> Math.min((Long) row[1], required))
                    .sum();
            double rate = Math.round(1000.0 * verified / ((long) required * members.size())) / 10.0;
            scored.add(new Scored(c, members.size(), rate));
        }
        scored.sort(Comparator.comparingDouble(Scored::rate).reversed()
                .thenComparing(Comparator.comparingInt(Scored::members).reversed()));

        List<ChallengeRankingResponse> result = new ArrayList<>();
        int rank = 0;
        for (int i = 0; i < Math.min(scored.size(), CHALLENGE_TOP); i++) {
            Scored s = scored.get(i);
            if (i == 0 || s.rate() != scored.get(i - 1).rate()) {
                rank = i + 1;
            }
            Challenge c = s.challenge();
            result.add(new ChallengeRankingResponse(rank, c.getId(), c.getTitle(), CategoryResponse.from(c.getCategory()),
                    c.getMode(), s.members(), s.rate(), ChronoUnit.DAYS.between(c.getStartDate(), today) + 1,
                    c.totalDays()));
        }
        return result;
    }

    private record Row(Long uid, double val, String nickname, String profileImageUrl) {
    }

    private record Scored(Challenge challenge, int members, double rate) {
    }
}
