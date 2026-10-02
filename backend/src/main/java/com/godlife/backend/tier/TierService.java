package com.godlife.backend.tier;

import com.godlife.backend.notification.Notification;
import com.godlife.backend.notification.NotificationService;
import com.godlife.backend.user.Role;
import com.godlife.backend.user.User;
import com.godlife.backend.user.UserRepository;
import jakarta.persistence.EntityManager;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.jdbc.core.namedparam.MapSqlParameterSource;
import org.springframework.jdbc.core.namedparam.NamedParameterJdbcTemplate;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;
import org.springframework.transaction.support.TransactionTemplate;

import java.time.Clock;
import java.time.LocalDate;
import java.util.List;
import java.util.Map;

/**
 * 칭호(티어): 브론즈 → 실버 → 골드 → 플래티넘 → 다이아몬드.
 * 점수는 따로 저장하지 않고 기록으로 그때그때 계산한다 — 포인트 · 돈과는 상관없이 노력만으로 정해진다.
 *   인증 성공 1회 +10 · 챌린지 완주 +50 · 챌린지 실패 -30 · 중간 포기 -50 (0점 아래로는 내려가지 않는다)
 * 칭호는 언제나 지금 점수에 맞춘다: 점수가 오르면 승급하고, 실패 · 포기로 내려가면 강등된다. 바뀌면 알림을 보낸다.
 * 혜택은 챌린지에 걸 수 있는 포인트 한도(하루 · 한 달)와 고액 챌린지 참여다.
 * 관리자는 칭호 대상이 아니다: 점수 · 승급 · 강등 없이 '관리자'로 표시하고, 혜택은 가장 높은 칭호와 같게 준다.
 */
@Slf4j
@Service
@RequiredArgsConstructor
public class TierService {

    public static final int VERIFY_POINTS = 10;
    public static final int COMPLETE_POINTS = 50;
    public static final int FAIL_PENALTY = 30;
    public static final int GIVE_UP_PENALTY = 50;
    /** 참가 포인트가 이 값 이상이면 고액 챌린지다 (tiers.high_stake_allowed 인 칭호만 참여) */
    public static final long HIGH_STAKE_MIN_FEE = 30_000;
    /** 관리자에게 보여 주는 칭호 이름 (tiers 테이블에는 없다) */
    public static final String ADMIN = "ADMIN";

    /** 알림 문구용: 칭호 이름 + 조사 */
    private static final Map<String, String> TO_LABEL = Map.of("BRONZE", "브론즈로", "SILVER", "실버로", "GOLD", "골드로",
            "PLATINUM", "플래티넘으로", "DIAMOND", "다이아몬드로");

    /** 회원별 기록 집계. 승인된 인증(내보내진 챌린지 제외) · 완주 · 실패 · 포기 수 */
    private static final String RECORD_SQL = """
            SELECT u.id AS uid,
                   (SELECT COUNT(*) FROM verifications v JOIN challenge_participants p ON p.id = v.participant_id
                     WHERE p.user_id = u.id AND v.status = 'APPROVED' AND p.status <> 'KICKED') AS verified,
                   (SELECT COUNT(*) FROM challenge_participants p WHERE p.user_id = u.id AND p.status = 'COMPLETED') AS completed,
                   (SELECT COUNT(*) FROM challenge_participants p WHERE p.user_id = u.id AND p.status = 'FAILED') AS failed,
                   (SELECT COUNT(*) FROM challenge_participants p WHERE p.user_id = u.id AND p.status = 'GAVE_UP') AS gave_up
            FROM users u
            """;

    private final NamedParameterJdbcTemplate jdbc;
    private final UserRepository userRepository;
    private final NotificationService notificationService;
    private final TransactionTemplate transactionTemplate;
    private final EntityManager entityManager;
    private final Clock clock;

    /** 칭호 하나 (tiers 테이블 한 줄) */
    public record Tier(int id, String name, int minScore, long dailyBetLimit, long monthlyBetLimit,
                       boolean highStakeAllowed) {
    }

    /** 점수를 만든 기록 */
    public record Record(long verified, long completed, long failed, long gaveUp) {

        public long score() {
            return Math.max(0, verified * VERIFY_POINTS + completed * COMPLETE_POINTS
                    - failed * FAIL_PENALTY - gaveUp * GIVE_UP_PENALTY);
        }
    }

    /**
     * 내 칭호 화면용.
     * @param tier     지금 칭호
     * @param next     다음 칭호 (다이아몬드면 null)
     * @param tiers    전체 칭호와 혜택
     * @param admin    관리자인지 (칭호 대상이 아니라 점수 · 다음 칭호가 없다)
     */
    public record MyTier(Tier tier, long score, Record record, Tier next, List<Tier> tiers, long highStakeMinFee,
                         boolean admin) {
    }

    /** 전체 칭호 (낮은 것부터). 다섯 줄짜리 표라 그때그때 읽는다 */
    public List<Tier> tiers() {
        return jdbc.query("""
                SELECT id, name, min_score, daily_bet_limit, monthly_bet_limit, high_stake_allowed
                FROM tiers ORDER BY min_score
                """, (rs, i) -> new Tier(rs.getInt("id"), rs.getString("name"), rs.getInt("min_score"),
                rs.getLong("daily_bet_limit"), rs.getLong("monthly_bet_limit"), rs.getBoolean("high_stake_allowed")));
    }

    /** 회원의 지금 칭호 (users.tier_id). 관리자는 가장 높은 칭호의 혜택을 가진 '관리자' */
    @Transactional(readOnly = true)
    public Tier tierOf(Long userId) {
        List<Tier> tiers = tiers();
        User user = userRepository.findById(userId).orElse(null);
        if (user != null && user.getRole() == Role.ADMIN) {
            return adminTier(tiers);
        }
        Integer tierId = user == null ? null : user.getTierId();
        return tiers.stream().filter(t -> tierId != null && t.id() == tierId).findFirst().orElse(tiers.get(0));
    }

    private static Tier adminTier(List<Tier> tiers) {
        Tier top = tiers.get(tiers.size() - 1);
        return new Tier(0, ADMIN, 0, top.dailyBetLimit(), top.monthlyBetLimit(), true);
    }

    @Transactional(readOnly = true)
    public MyTier mine(Long userId) {
        List<Tier> tiers = tiers();
        if (userRepository.findById(userId).map(u -> u.getRole() == Role.ADMIN).orElse(false)) {
            return new MyTier(adminTier(tiers), 0, new Record(0, 0, 0, 0), null, tiers, HIGH_STAKE_MIN_FEE, true);
        }
        Record record = recordOf(userId);
        long score = record.score();
        Tier tier = tierFor(tiers, score);
        Tier next = tiers.stream().filter(t -> t.minScore() > score).findFirst().orElse(null);
        return new MyTier(tier, score, record, next, tiers, HIGH_STAKE_MIN_FEE, false);
    }

    /**
     * 이 회원의 점수를 다시 계산해 칭호를 맞춘다 (승급 또는 강등). 바뀌면 알림을 보낸다.
     * 인증 · 포기 · 관리자 검토처럼 점수가 바뀌는 일이 생긴 트랜잭션 안에서 부른다.
     */
    @Transactional
    public void refresh(Long userId) {
        // 같은 트랜잭션에서 방금 바뀐 참가 상태(포기 등)가 아래 집계 쿼리에 보이도록 먼저 DB 로 내보낸다
        entityManager.flush();
        apply(userId, recordOf(userId).score(), tiers());
    }

    /**
     * 모든 회원의 칭호를 점수에 맞춘다. 자정 진행 관리(챌린지 종료 → 완주 · 실패 판정) 뒤에 부른다.
     * 서버가 켜질 때도 돌아서, 칭호 기능이 생기기 전의 기록도 한 번에 반영된다. 바뀐 회원 수를 돌려준다.
     */
    public int refreshAll() {
        List<Tier> tiers = tiers();
        List<long[]> rows = jdbc.query(RECORD_SQL + " WHERE u.status = 'ACTIVE'", (rs, i) -> new long[]{
                rs.getLong("uid"), new Record(rs.getLong("verified"), rs.getLong("completed"), rs.getLong("failed"),
                rs.getLong("gave_up")).score()});
        int changed = 0;
        for (long[] row : rows) {
            try {
                Boolean moved = transactionTemplate.execute(status -> apply(row[0], row[1], tiers));
                if (Boolean.TRUE.equals(moved)) {
                    changed++;
                }
            } catch (RuntimeException e) {
                log.error("회원 {} 칭호 맞추기 실패", row[0], e);
            }
        }
        return changed;
    }

    private boolean apply(Long userId, long score, List<Tier> tiers) {
        User user = userRepository.findById(userId).orElse(null);
        // 관리자는 칭호 대상이 아니다
        if (user == null || user.getRole() == Role.ADMIN) {
            return false;
        }
        Tier target = tierFor(tiers, score);
        int current = user.getTierId() == null ? User.DEFAULT_TIER_ID : user.getTierId();
        if (target.id() == current) {
            return false;
        }
        user.changeTier(target.id());
        Tier before = tiers.stream().filter(t -> t.id() == current).findFirst().orElse(tiers.get(0));
        boolean up = target.minScore() > before.minScore();
        String label = TO_LABEL.getOrDefault(target.name(), target.name());
        notificationService.notify(userId, Notification.Type.TIER,
                up ? label + " 승급했어요 🎉" : label + " 내려갔어요",
                up ? "꾸준히 인증한 덕분이에요. 챌린지에 걸 수 있는 포인트 한도가 하루 %,dP로 늘었어요."
                        .formatted(target.dailyBetLimit())
                        : "챌린지 실패 · 포기로 점수가 내려갔어요. 다시 인증을 쌓으면 금방 올라갈 수 있어요.",
                "/me/tier", "tier:%d:%d>%d:%s".formatted(userId, current, target.id(), LocalDate.now(clock)));
        return true;
    }

    private Record recordOf(Long userId) {
        return jdbc.query(RECORD_SQL + " WHERE u.id = :id", new MapSqlParameterSource("id", userId),
                (rs, i) -> new Record(rs.getLong("verified"), rs.getLong("completed"), rs.getLong("failed"),
                        rs.getLong("gave_up"))).stream().findFirst().orElse(new Record(0, 0, 0, 0));
    }

    /** 이 점수로 될 수 있는 가장 높은 칭호 */
    private static Tier tierFor(List<Tier> tiers, long score) {
        Tier result = tiers.get(0);
        for (Tier t : tiers) {
            if (t.minScore() <= score) {
                result = t;
            }
        }
        return result;
    }
}
