package com.godlife.backend.record;

import com.godlife.backend.common.error.BusinessException;
import com.godlife.backend.common.error.ErrorCode;
import com.godlife.backend.common.upload.ImageStore;
import com.godlife.backend.record.dto.RecordDtos.ChallengeOption;
import com.godlife.backend.record.dto.RecordDtos.Day;
import com.godlife.backend.record.dto.RecordDtos.DayItem;
import com.godlife.backend.record.dto.RecordDtos.DayResponse;
import com.godlife.backend.record.dto.RecordDtos.DiaryEntry;
import com.godlife.backend.record.dto.RecordDtos.MonthResponse;
import com.godlife.backend.record.dto.RecordDtos.Summary;
import com.godlife.backend.record.dto.RecordDtos.WeekDay;
import lombok.RequiredArgsConstructor;
import org.springframework.jdbc.core.namedparam.MapSqlParameterSource;
import org.springframework.jdbc.core.namedparam.NamedParameterJdbcTemplate;
import org.springframework.jdbc.support.GeneratedKeyHolder;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;
import org.springframework.web.multipart.MultipartFile;

import java.nio.file.Files;
import java.nio.file.Path;
import java.time.Clock;
import java.time.LocalDate;
import java.time.YearMonth;
import java.time.temporal.ChronoUnit;
import java.util.ArrayList;
import java.util.HashMap;
import java.util.HashSet;
import java.util.List;
import java.util.Map;
import java.util.Set;

/**
 * 갓생기록: "내가 얼마나 해냈나". 캘린더(날짜별 성공·실패) · 그날 인증 사진과 결과 · 일기.
 * - 그날 인증했으면 성공, 안 했으면 실패 (오늘 아직 안 한 것은 실패가 아니라 '진행 중').
 * - 주 N회 챌린지는 이날 안 해도 그 주 횟수를 채울 수 있는 날(또는 이미 채운 뒤)은 '인증하지 않아도 되는 날'이라
 *   세지 않는다. 그래서 다른 챌린지만 인증해도 그날은 성공이다. 넘기면 횟수를 못 채우게 되는 날만 실패.
 * - 시작한 뒤에 들어온 챌린지는 들어온 날부터 센다. 포기한 챌린지는 남은 날이 모두 실패. 방장이 내보냈거나 시작 전에 나간 챌린지는 기록에 없다.
 * - 포인트 금액은 보여 주지 않는다 (일기장 분위기). 일기(글 · 기분 · 태그한 챌린지 · 사진 한 장)는 하루에 여러 개
 *   쓸 수 있고, 항상 본인만 보고, 포인트 보상이 없다.
 */
@Service
@RequiredArgsConstructor
public class RecordService {

    public static final String DONE = "DONE";
    public static final String FAIL = "FAIL";
    public static final String PENDING = "PENDING";
    static final String REST = "REST";
    /** 하루에 쓸 수 있는 일기 수 (도배 방지용 넉넉한 상한) */
    static final int MAX_PER_DAY = 30;

    private final NamedParameterJdbcTemplate jdbc;
    private final ImageStore imageStore;
    private final Clock clock;

    /** 한 달 캘린더 + 요약. challengeId 를 주면 그 챌린지만 본다 (내 챌린지가 아니면 전체로) */
    @Transactional(readOnly = true)
    public MonthResponse month(Long me, YearMonth month, Long challengeId) {
        LocalDate today = LocalDate.now(clock);
        List<Joined> all = joined(me, today);
        Long selected = all.stream().anyMatch(j -> j.challengeId().equals(challengeId)) ? challengeId : null;
        List<Joined> scope = selected == null ? all : all.stream().filter(j -> j.challengeId().equals(selected)).toList();
        Map<LocalDate, int[]> counts = counts(scope, verified(me), today);

        Set<LocalDate> diaries = new HashSet<>(jdbc.queryForList("""
                SELECT DISTINCT entry_date FROM diary_entries WHERE user_id = :me AND entry_date BETWEEN :from AND :to
                """, new MapSqlParameterSource("me", me).addValue("from", month.atDay(1)).addValue("to", month.atEndOfMonth()),
                LocalDate.class));

        List<Day> days = new ArrayList<>();
        int monthDone = 0;
        int monthTotal = 0;
        for (LocalDate d = month.atDay(1); !d.isAfter(month.atEndOfMonth()); d = d.plusDays(1)) {
            int[] c = counts.getOrDefault(d, new int[3]);
            days.add(new Day(d, c[0] + c[1] + c[2], c[0], c[2], diaries.contains(d)));
            monthDone += c[0];
            monthTotal += c[0] + c[1];
        }
        Integer rate = monthTotal == 0 ? null : (int) Math.round(100.0 * monthDone / monthTotal);
        return new MonthResponse(month.toString(), today, selected,
                all.stream().map(j -> new ChallengeOption(j.challengeId(), j.title(), j.categoryId(), j.start(), j.end()))
                        .toList(),
                days, new Summary(streak(counts, today), monthDone, monthTotal, rate));
    }

    /** 그날 챌린지별 결과 + 그날 쓴 일기들 + 이 날까지 최근 7일 */
    @Transactional(readOnly = true)
    public DayResponse day(Long me, LocalDate date) {
        LocalDate today = LocalDate.now(clock);
        Map<Long, Map<LocalDate, Long>> verified = verified(me);
        List<DayItem> items = new ArrayList<>();
        for (Joined j : joined(me, today)) {
            Map<LocalDate, Long> mine = verified.getOrDefault(j.participantId(), Map.of());
            String result = result(j, mine.keySet(), date, today);
            if (result != null) {
                items.add(new DayItem(j.challengeId(), j.title(), j.categoryId(), result, mine.get(date)));
            }
        }
        Map<Long, List<Long>> tags = new HashMap<>();
        jdbc.query("""
                SELECT t.diary_id, t.challenge_id FROM diary_tags t JOIN diary_entries d ON d.id = t.diary_id
                WHERE d.user_id = :me AND d.entry_date = :date ORDER BY t.challenge_id
                """, new MapSqlParameterSource("me", me).addValue("date", date), rs -> {
            tags.computeIfAbsent(rs.getLong("diary_id"), k -> new ArrayList<>()).add(rs.getLong("challenge_id"));
        });
        List<DiaryEntry> diaries = jdbc.query("""
                SELECT id, content, mood, photo_key, created_at FROM diary_entries
                WHERE user_id = :me AND entry_date = :date ORDER BY id
                """, new MapSqlParameterSource("me", me).addValue("date", date),
                (rs, i) -> new DiaryEntry(rs.getLong("id"), rs.getString("content"), rs.getString("mood"),
                        tags.getOrDefault(rs.getLong("id"), List.of()), rs.getString("photo_key") != null,
                        rs.getTimestamp("created_at").toLocalDateTime()));
        return new DayResponse(date, items, diaries, week(me, date));
    }

    /** 날짜 · 챌린지별 결과 한 줄 (주간 회고 리포트가 쓴다) */
    public record DayResult(LocalDate date, Long challengeId, String title, int categoryId, String result) {
    }

    /**
     * from~to 사이의 날짜 · 챌린지별 결과(성공 · 실패 · 오늘 아직). 캘린더와 같은 기준으로 센다.
     * 인증하지 않아도 되는 날(쉬는 날)과 챌린지 기간 밖 · 앞날은 싣지 않는다.
     */
    @Transactional(readOnly = true)
    public List<DayResult> results(Long me, LocalDate from, LocalDate to) {
        LocalDate today = LocalDate.now(clock);
        Map<Long, Map<LocalDate, Long>> verified = verified(me);
        List<DayResult> results = new ArrayList<>();
        for (Joined j : joined(me, today)) {
            Set<LocalDate> mine = verified.getOrDefault(j.participantId(), Map.of()).keySet();
            for (LocalDate d = from; !d.isAfter(to); d = d.plusDays(1)) {
                String result = result(j, mine, d, today);
                if (result != null && !REST.equals(result)) {
                    results.add(new DayResult(d, j.challengeId(), j.title(), j.categoryId(), result));
                }
            }
        }
        return results;
    }

    /** 일기 새로 쓰기 (지난 날짜도 가능, 앞날은 불가). 하루에 여러 개 쓸 수 있다. 글이나 기분 중 하나는 있어야 한다. */
    @Transactional
    public Long createDiary(Long me, LocalDate date, String content, String mood, List<Long> challengeIds) {
        LocalDate today = requireWritable(me, date);
        String text = content.strip();
        if (text.isEmpty() && mood == null) {
            throw new BusinessException(ErrorCode.VALIDATION_ERROR, "글이나 기분을 남겨 주세요.");
        }
        Long id = insert(me, date, text, mood, null);
        replaceTags(me, id, challengeIds, today);
        return id;
    }

    /** 사진으로 일기 시작하기: 사진만 붙은 일기를 만들고 id 를 돌려준다 (글 · 기분은 이어서 고치기로 채운다) */
    @Transactional
    public Long createDiaryWithPhoto(Long me, LocalDate date, MultipartFile file) {
        requireWritable(me, date);
        return insert(me, date, "", null, imageStore.storeDiaryImage(me, file));
    }

    /** 일기 고치기: 글 · 기분 · 태그한 챌린지. 글도 기분도 사진도 없으면 지운다. */
    @Transactional
    public void updateDiary(Long me, Long id, String content, String mood, List<Long> challengeIds) {
        Entry old = owned(me, id);
        String text = content.strip();
        if (text.isEmpty() && mood == null && old.photoKey() == null) {
            deleteDiary(me, id);
            return;
        }
        jdbc.update("UPDATE diary_entries SET content = :content, mood = :mood WHERE id = :id",
                new MapSqlParameterSource("id", id).addValue("content", text).addValue("mood", mood));
        replaceTags(me, id, challengeIds, LocalDate.now(clock));
    }

    @Transactional
    public void deleteDiary(Long me, Long id) {
        Entry old = owned(me, id);
        jdbc.update("DELETE FROM diary_entries WHERE id = :id", new MapSqlParameterSource("id", id));
        if (old.photoKey() != null) {
            imageStore.delete(old.photoKey());
        }
    }

    /** 일기에 사진 한 장 붙이기 (다시 올리면 바꾼다) */
    @Transactional
    public void saveDiaryPhoto(Long me, Long id, MultipartFile file) {
        Entry old = owned(me, id);
        String key = imageStore.storeDiaryImage(me, file);
        jdbc.update("UPDATE diary_entries SET photo_key = :key WHERE id = :id",
                new MapSqlParameterSource("id", id).addValue("key", key));
        if (old.photoKey() != null) {
            imageStore.delete(old.photoKey());
        }
    }

    /** 일기 사진 빼기. 글도 기분도 없던 일기면 일기째 지운다. */
    @Transactional
    public void deleteDiaryPhoto(Long me, Long id) {
        Entry old = owned(me, id);
        if (old.photoKey() == null) {
            return;
        }
        if (old.content().isEmpty() && old.mood() == null) {
            jdbc.update("DELETE FROM diary_entries WHERE id = :id", new MapSqlParameterSource("id", id));
        } else {
            jdbc.update("UPDATE diary_entries SET photo_key = NULL WHERE id = :id", new MapSqlParameterSource("id", id));
        }
        imageStore.delete(old.photoKey());
    }

    /** 내 일기 사진 파일 (본인만) */
    @Transactional(readOnly = true)
    public Path diaryPhoto(Long me, Long id) {
        Entry entry = owned(me, id);
        if (entry.photoKey() == null) {
            throw new BusinessException(ErrorCode.DIARY_NOT_FOUND, "사진을 찾을 수 없어요.");
        }
        Path path = imageStore.resolve(entry.photoKey());
        if (!Files.isRegularFile(path)) {
            throw new BusinessException(ErrorCode.DIARY_NOT_FOUND, "사진을 찾을 수 없어요.");
        }
        return path;
    }

    /** 앞날이 아니고, 그날 일기가 너무 많지 않은지 (도배 방지용 넉넉한 상한) */
    private LocalDate requireWritable(Long me, LocalDate date) {
        LocalDate today = LocalDate.now(clock);
        if (date.isAfter(today)) {
            throw new BusinessException(ErrorCode.VALIDATION_ERROR, "아직 오지 않은 날의 일기는 쓸 수 없어요.");
        }
        Long count = jdbc.queryForObject("SELECT COUNT(*) FROM diary_entries WHERE user_id = :me AND entry_date = :date",
                new MapSqlParameterSource("me", me).addValue("date", date), Long.class);
        if (count != null && count >= MAX_PER_DAY) {
            throw new BusinessException(ErrorCode.VALIDATION_ERROR, "하루에 일기는 " + MAX_PER_DAY + "개까지 쓸 수 있어요.");
        }
        return today;
    }

    private Long insert(Long me, LocalDate date, String content, String mood, String photoKey) {
        GeneratedKeyHolder keys = new GeneratedKeyHolder();
        jdbc.update("""
                INSERT INTO diary_entries (user_id, entry_date, content, mood, photo_key)
                VALUES (:me, :date, :content, :mood, :photoKey)
                """, new MapSqlParameterSource("me", me).addValue("date", date).addValue("content", content)
                .addValue("mood", mood).addValue("photoKey", photoKey), keys);
        return keys.getKey().longValue();
    }

    /** 내가 참여한 챌린지만 태그할 수 있다 */
    private void replaceTags(Long me, Long id, List<Long> challengeIds, LocalDate today) {
        jdbc.update("DELETE FROM diary_tags WHERE diary_id = :id", new MapSqlParameterSource("id", id));
        Set<Long> mine = new HashSet<>();
        joined(me, today).forEach(j -> mine.add(j.challengeId()));
        for (Long challengeId : challengeIds == null ? Set.<Long>of() : new HashSet<>(challengeIds)) {
            if (mine.contains(challengeId)) {
                jdbc.update("INSERT INTO diary_tags (diary_id, challenge_id) VALUES (:id, :cid)",
                        new MapSqlParameterSource("id", id).addValue("cid", challengeId));
            }
        }
    }

    /** 내 일기 (남의 것이거나 없으면 404) */
    private Entry owned(Long me, Long id) {
        List<Entry> rows = jdbc.query("""
                SELECT id, content, mood, photo_key FROM diary_entries WHERE id = :id AND user_id = :me
                """, new MapSqlParameterSource("id", id).addValue("me", me),
                (rs, i) -> new Entry(rs.getLong("id"), rs.getString("content"), rs.getString("mood"),
                        rs.getString("photo_key")));
        if (rows.isEmpty()) {
            throw new BusinessException(ErrorCode.DIARY_NOT_FOUND);
        }
        return rows.get(0);
    }

    /** 이 날까지 최근 7일의 일기 여부와 기분 (하루에 여러 개면 가장 나중에 고른 기분) */
    private List<WeekDay> week(Long me, LocalDate date) {
        Map<LocalDate, String> moods = new HashMap<>();
        jdbc.query("""
                SELECT entry_date, mood FROM diary_entries
                WHERE user_id = :me AND entry_date BETWEEN :from AND :to ORDER BY id
                """, new MapSqlParameterSource("me", me).addValue("from", date.minusDays(6)).addValue("to", date),
                rs -> {
                    LocalDate d = rs.getDate("entry_date").toLocalDate();
                    String mood = rs.getString("mood");
                    if (mood != null || !moods.containsKey(d)) {
                        moods.put(d, mood);
                    }
                });
        List<WeekDay> week = new ArrayList<>();
        for (LocalDate d = date.minusDays(6); !d.isAfter(date); d = d.plusDays(1)) {
            week.add(new WeekDay(d, moods.get(d), moods.containsKey(d)));
        }
        return week;
    }

    /** 내 인증 사진 파일 (포기했거나 끝난 챌린지의 것도 내 기록이라 볼 수 있다. 남의 사진은 404) */
    @Transactional(readOnly = true)
    public Path photo(Long me, Long verificationId) {
        List<String> keys = jdbc.queryForList("""
                SELECT v.image_url FROM verifications v JOIN challenge_participants p ON p.id = v.participant_id
                WHERE v.id = :id AND p.user_id = :me AND p.status <> 'KICKED'
                """, new MapSqlParameterSource("id", verificationId).addValue("me", me), String.class);
        if (keys.isEmpty()) {
            throw new BusinessException(ErrorCode.VERIFICATION_NOT_FOUND);
        }
        Path path = imageStore.resolve(keys.get(0));
        if (!Files.isRegularFile(path)) {
            throw new BusinessException(ErrorCode.VERIFICATION_NOT_FOUND);
        }
        return path;
    }

    // ---------- 계산 ----------

    /** 내가 참여한(시작된) 챌린지. 최근에 시작한 것부터 */
    private List<Joined> joined(Long me, LocalDate today) {
        return jdbc.query("""
                SELECT p.id AS pid, c.id AS cid, c.title, c.category_id, c.start_date, c.end_date,
                       c.frequency_type, c.weekly_count, DATE(p.joined_at) AS joined_date
                FROM challenge_participants p JOIN challenges c ON c.id = p.challenge_id
                WHERE p.user_id = :me AND p.status IN ('ACTIVE', 'COMPLETED', 'FAILED', 'GAVE_UP')
                  AND c.start_date <= :today
                ORDER BY c.start_date DESC, c.id DESC
                """, new MapSqlParameterSource("me", me).addValue("today", today),
                (rs, i) -> new Joined(rs.getLong("pid"), rs.getLong("cid"), rs.getString("title"),
                        rs.getInt("category_id"), rs.getDate("start_date").toLocalDate(),
                        rs.getDate("end_date").toLocalDate(), rs.getDate("joined_date").toLocalDate(),
                        "DAILY".equals(rs.getString("frequency_type")) ? 0 : rs.getInt("weekly_count")));
    }

    /** 내 인증: 참가 id → (날짜 → 인증 id). 거절된 사진은 뺀다 */
    private Map<Long, Map<LocalDate, Long>> verified(Long me) {
        Map<Long, Map<LocalDate, Long>> result = new HashMap<>();
        jdbc.query("""
                SELECT v.id, v.participant_id, v.verify_date
                FROM verifications v JOIN challenge_participants p ON p.id = v.participant_id
                WHERE p.user_id = :me AND v.status <> 'REJECTED'
                """, new MapSqlParameterSource("me", me), rs -> {
            result.computeIfAbsent(rs.getLong("participant_id"), k -> new HashMap<>())
                    .put(rs.getDate("verify_date").toLocalDate(), rs.getLong("id"));
        });
        return result;
    }

    /** 날짜별 [인증함, 못 함, 오늘 아직] 수 */
    private Map<LocalDate, int[]> counts(List<Joined> scope, Map<Long, Map<LocalDate, Long>> verified, LocalDate today) {
        Map<LocalDate, int[]> counts = new HashMap<>();
        for (Joined j : scope) {
            Set<LocalDate> mine = verified.getOrDefault(j.participantId(), Map.of()).keySet();
            LocalDate last = j.end().isAfter(today) ? today : j.end();
            for (LocalDate d = j.start(); !d.isAfter(last); d = d.plusDays(1)) {
                String result = result(j, mine, d, today);
                if (DONE.equals(result)) {
                    counts.computeIfAbsent(d, k -> new int[3])[0]++;
                } else if (FAIL.equals(result)) {
                    counts.computeIfAbsent(d, k -> new int[3])[1]++;
                } else if (PENDING.equals(result)) {
                    counts.computeIfAbsent(d, k -> new int[3])[2]++;
                }
            }
        }
        return counts;
    }

    /** 그날 이 챌린지의 결과. 챌린지 기간이 아니거나 앞날이면 null */
    private static String result(Joined j, Set<LocalDate> mine, LocalDate date, LocalDate today) {
        if (date.isBefore(j.start()) || date.isAfter(j.end()) || date.isAfter(today)) {
            return null;
        }
        if (mine.contains(date)) {
            return DONE;
        }
        if (date.isBefore(j.from())) {
            return null; // 들어오기 전 날은 내 실패가 아니다
        }
        if (j.weeklyCount() > 0) {
            // 한 주는 시작일부터 7일씩. 이날 안 해도 그 주 남은 날에 횟수를 채울 수 있으면(또는 이미 채웠으면)
            // 인증하지 않아도 되는 날이다. 이날을 넘기면 못 채우게 되는 날만 실패로 센다 → 실패한 날 수 = 모자란 횟수
            LocalDate weekStart = j.start().plusDays(ChronoUnit.DAYS.between(j.start(), date) / 7 * 7);
            LocalDate weekEnd = weekStart.plusDays(6).isAfter(j.end()) ? j.end() : weekStart.plusDays(6);
            int required = (int) Math.min(j.weeklyCount(), ChronoUnit.DAYS.between(weekStart, weekEnd) + 1);
            long doneSoFar = mine.stream().filter(d -> !d.isBefore(weekStart) && d.isBefore(date)).count();
            long daysLeft = ChronoUnit.DAYS.between(date, weekEnd);
            if (required - doneSoFar <= daysLeft) {
                return REST;
            }
        }
        return date.equals(today) ? PENDING : FAIL;
    }

    /** 연속 달성: 오늘부터 거꾸로, 해야 할 인증을 모두 한 날이 이어진 수 (오늘 아직 안 했으면 어제부터 센다) */
    private static int streak(Map<LocalDate, int[]> counts, LocalDate today) {
        LocalDate first = counts.keySet().stream().min(LocalDate::compareTo).orElse(null);
        int streak = 0;
        for (LocalDate d = today; first != null && !d.isBefore(first); d = d.minusDays(1)) {
            int[] c = counts.get(d);
            if (c == null) {
                continue; // 챌린지가 없던 날은 건너뛴다
            }
            if (c[1] > 0) {
                break;
            }
            if (c[2] == 0) {
                streak++;
            }
        }
        return streak;
    }

    private record Entry(Long id, String content, String mood, String photoKey) {
    }

    /** weeklyCount = 0 이면 매일 챌린지 */
    private record Joined(Long participantId, Long challengeId, String title, int categoryId, LocalDate start,
                          LocalDate end, LocalDate joined, int weeklyCount) {

        /** 기록을 세기 시작하는 날: 시작일, 시작한 뒤에 들어왔으면 들어온 날 (그 전은 내 실패가 아니다) */
        LocalDate from() {
            return joined.isAfter(start) ? joined : start;
        }
    }
}
