package com.godlife.backend.demo;

import com.godlife.backend.challenge.InviteCodeGenerator;
import lombok.RequiredArgsConstructor;
import org.springframework.jdbc.core.namedparam.MapSqlParameterSource;
import org.springframework.jdbc.core.namedparam.NamedParameterJdbcTemplate;
import org.springframework.jdbc.support.GeneratedKeyHolder;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.time.Clock;
import java.time.LocalDate;
import java.util.List;

/**
 * 테스트 서버용 체험 데이터 (app.demo.starter-data=true 일 때만 DemoStarterListener 가 부른다).
 * 가입하자마자 써 볼 거리가 있도록 두 가지를 넣는다. 포인트는 건드리지 않는다 (무료 챌린지만).
 * ① 아직 참여한 챌린지가 없으면: 오늘 시작하는 공개 무료 챌린지 3개에 참여 → 바로 인증해 볼 수 있다
 * ② 본인만 보는 '갓생 체험' 챌린지: 6일 전에 시작해 지난 기록(성공 5일 · 실패 1일)이 들어 있어 캘린더 · 갓생기록에
 *    표시가 찍히고, 오늘은 '오늘 아직'이라 아무 사진으로나 인증해 볼 수 있다 (종류가 '기타'라 AI 가 사진 종류를 보지 않는다)
 * 회원마다 한 번만 들어간다 (체험 챌린지가 이미 있으면 건너뛴다).
 */
@Service
@RequiredArgsConstructor
public class DemoStarter {

    static final String TITLE = "갓생 체험 · 아무 사진이나 인증해 보세요";
    private static final String DESCRIPTION = """
            가입하면 자동으로 생기는 체험용 챌린지예요. 나만 볼 수 있어요.
            지난 6일 기록이 미리 들어 있어서 캘린더에서 성공 · 실패를 눌러 볼 수 있어요.
            오늘은 아무 사진이나 찍어서 인증해 보세요.""";
    /** 체험 챌린지의 지난 기록: 며칠 전에 성공했는지 (4일 전은 실패로 남긴다) */
    private static final List<Integer> DONE_DAYS_AGO = List.of(6, 5, 3, 2, 1);
    private static final int STARTED_DAYS_AGO = 6;
    private static final int TOTAL_DAYS = 14;
    private static final int TODAY_JOINS = 3;

    private final NamedParameterJdbcTemplate jdbc;
    private final InviteCodeGenerator inviteCodeGenerator;
    private final Clock clock;

    /** 체험 데이터를 아직 받지 않은 실제 회원 (더미 · 관리자 · 정지 · 탈퇴 제외) */
    public List<Long> membersWithoutStarter() {
        return jdbc.queryForList("""
                SELECT u.id FROM users u
                WHERE u.role = 'USER' AND u.status = 'ACTIVE' AND u.email NOT LIKE '%@dummy.godlife'
                  AND NOT EXISTS (SELECT 1 FROM challenges c WHERE c.host_id = u.id AND c.title = :title)
                ORDER BY u.id
                """, new MapSqlParameterSource("title", TITLE), Long.class);
    }

    /** 한 회원에게 체험 데이터를 넣는다. 이미 받았으면 아무것도 하지 않는다. 넣었으면 true */
    @Transactional
    public boolean seed(Long userId) {
        MapSqlParameterSource p = new MapSqlParameterSource("u", userId)
                .addValue("title", TITLE)
                .addValue("today", LocalDate.now(clock));
        Long already = jdbc.queryForObject(
                "SELECT COUNT(*) FROM challenges WHERE host_id = :u AND title = :title", p, Long.class);
        if (already != null && already > 0) {
            return false;
        }
        Long joined = jdbc.queryForObject(
                "SELECT COUNT(*) FROM challenge_participants WHERE user_id = :u", p, Long.class);
        if (joined == null || joined == 0) {
            joinTodayChallenges(p);
        }
        createStarterChallenge(p);
        return true;
    }

    private void joinTodayChallenges(MapSqlParameterSource p) {
        List<Long> ids = jdbc.queryForList("""
                SELECT id FROM challenges
                WHERE mode = 'FREE' AND visibility = 'PUBLIC' AND status IN ('RECRUITING', 'ONGOING')
                  AND start_date = :today AND participant_count < max_participants AND host_id <> :u
                ORDER BY participant_count DESC, id
                LIMIT %d
                """.formatted(TODAY_JOINS), p, Long.class);
        for (Long id : ids) {
            MapSqlParameterSource c = new MapSqlParameterSource(p.getValues()).addValue("c", id);
            jdbc.update("INSERT INTO challenge_participants (challenge_id, user_id, deposit_amount) VALUES (:c, :u, 0)", c);
            jdbc.update("UPDATE challenges SET participant_count = participant_count + 1 WHERE id = :c", c);
        }
    }

    private void createStarterChallenge(MapSqlParameterSource p) {
        LocalDate today = (LocalDate) p.getValue("today");
        LocalDate start = today.minusDays(STARTED_DAYS_AGO);
        MapSqlParameterSource c = new MapSqlParameterSource(p.getValues())
                .addValue("description", DESCRIPTION)
                .addValue("code", inviteCode())
                .addValue("start", start)
                .addValue("end", start.plusDays(TOTAL_DAYS - 1));
        GeneratedKeyHolder challengeKey = new GeneratedKeyHolder();
        // 종류는 '기타'(세부 종류 없음) → AI 가 사진 종류를 확인하지 않아 아무 사진이나 인증된다
        jdbc.update("""
                INSERT INTO challenges
                  (host_id, category_id, sub_type_id, title, description, mode, visibility, invite_code, start_date, end_date,
                   frequency_type, weekly_count, entry_fee, min_bet, max_bet, max_participants, verify_from, verify_until,
                   partial_refund, status, participant_count)
                VALUES (:u, (SELECT id FROM categories WHERE ai_label = 'other'), NULL, :title, :description, 'FREE', 'PRIVATE',
                        :code, :start, :end, 'DAILY', NULL, 0, 0, 0, 1, NULL, NULL, FALSE, 'ONGOING', 1)
                """, c, challengeKey, new String[] {"id"});
        long challengeId = challengeKey.getKey().longValue();

        // 지난 기록: 성공 5일 · 어제까지 3일 연속
        GeneratedKeyHolder participantKey = new GeneratedKeyHolder();
        jdbc.update("""
                INSERT INTO challenge_participants (challenge_id, user_id, deposit_amount, success_days, current_streak, max_streak, joined_at)
                VALUES (:challenge, :u, 0, :done, 3, 3, :start)
                """, new MapSqlParameterSource(c.getValues()).addValue("challenge", challengeId)
                .addValue("done", DONE_DAYS_AGO.size()), participantKey, new String[] {"id"});
        long participantId = participantKey.getKey().longValue();

        // 사진은 더미 회원의 인증 사진 하나를 같이 쓴다 (없으면 사진만 비어 보인다)
        List<String> images = jdbc.queryForList("""
                SELECT v.image_url FROM verifications v
                JOIN challenge_participants cp ON cp.id = v.participant_id
                JOIN users du ON du.id = cp.user_id
                WHERE du.email LIKE '%@dummy.godlife' AND v.status = 'APPROVED'
                ORDER BY v.id DESC LIMIT 1
                """, new MapSqlParameterSource(), String.class);
        String image = images.isEmpty() ? "demo/starter.jpg" : images.get(0);
        for (int daysAgo : DONE_DAYS_AGO) {
            LocalDate day = today.minusDays(daysAgo);
            // 해시는 회원 · 날짜마다 달라서, 나중에 올리는 실제 사진의 '같은 사진' 검사와 부딪히지 않는다
            jdbc.update("""
                    INSERT INTO verifications (participant_id, verify_date, received_at, image_url, image_hash, status)
                    VALUES (:pid, :day, TIMESTAMP(:day, '20:00:00'), :image,
                            SHA2(CONCAT('demo-starter-', :pid, '-', :day), 256), 'APPROVED')
                    """, new MapSqlParameterSource("pid", participantId).addValue("day", day).addValue("image", image));
        }
    }

    private String inviteCode() {
        for (int i = 0; i < 5; i++) {
            String code = inviteCodeGenerator.next();
            Long used = jdbc.queryForObject("SELECT COUNT(*) FROM challenges WHERE invite_code = :code",
                    new MapSqlParameterSource("code", code), Long.class);
            if (used == null || used == 0) {
                return code;
            }
        }
        throw new IllegalStateException("초대 코드를 만들지 못했습니다.");
    }
}
