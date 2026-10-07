package com.godlife.backend.record;

import com.godlife.backend.auth.JwtProvider;
import com.godlife.backend.user.User;
import com.godlife.backend.user.UserRepository;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.boot.webmvc.test.autoconfigure.AutoConfigureMockMvc;
import org.springframework.http.MediaType;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.mock.web.MockMultipartFile;
import org.springframework.test.context.ActiveProfiles;
import org.springframework.test.web.servlet.MockMvc;
import org.springframework.test.web.servlet.ResultActions;
import org.springframework.transaction.annotation.Transactional;

import javax.imageio.ImageIO;
import java.awt.Color;
import java.awt.Graphics2D;
import java.awt.image.BufferedImage;
import java.io.ByteArrayOutputStream;
import java.io.IOException;
import java.time.Clock;
import java.time.LocalDate;
import java.time.YearMonth;
import java.util.UUID;
import java.util.concurrent.ThreadLocalRandom;
import java.util.regex.Matcher;
import java.util.regex.Pattern;

import static org.assertj.core.api.Assertions.assertThat;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.delete;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.multipart;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.post;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.put;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.jsonPath;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

/** 갓생기록: 캘린더(성공·실패·오늘 진행 중) · 그날 결과와 사진 · 일기(하루에 여러 개, 본인만) */
@SpringBootTest
@AutoConfigureMockMvc
@ActiveProfiles("test")
@Transactional
class RecordApiTest {

    private static final Pattern ID = Pattern.compile("\"id\":(\\d+)");

    @Autowired MockMvc mvc;
    @Autowired UserRepository userRepository;
    @Autowired JwtProvider jwtProvider;
    @Autowired JdbcTemplate jdbc;
    @Autowired Clock clock;

    private String host;
    private String a;
    private String b;
    private LocalDate today;

    @BeforeEach
    void setUp() {
        host = tokenOf(newUser());
        a = tokenOf(newUser());
        b = tokenOf(newUser());
        today = LocalDate.now(clock);
    }

    @Test
    @DisplayName("인증한 날은 성공, 안 한 지난 날은 실패, 오늘 아직 안 한 것은 진행 중으로 센다")
    void calendar() throws Exception {
        long one = challenge("DAILY", null);
        long two = challenge("DAILY", null);
        join(one, a);
        join(two, a);
        verify(one, a);
        // 둘 다 이틀 전에 시작한 것으로: 그제·어제는 둘 다 못 했고, 오늘은 하나만 했다
        startedDaysAgo(one, 2);
        startedDaysAgo(two, 2);

        month(a, YearMonth.from(today), null)
                .andExpect(jsonPath("$.challenges.length()").value(2))
                .andExpect(jsonPath(day(today) + ".total").value(2))
                .andExpect(jsonPath(day(today) + ".done").value(1))
                .andExpect(jsonPath(day(today) + ".pending").value(1))
                .andExpect(jsonPath("$.summary.streak").value(0));

        // 챌린지 하나만 고르면 그 챌린지 결과만: 오늘 성공 → 연속 1일
        month(a, YearMonth.from(today), one)
                .andExpect(jsonPath("$.challengeId").value(one))
                .andExpect(jsonPath(day(today) + ".total").value(1))
                .andExpect(jsonPath(day(today) + ".done").value(1))
                .andExpect(jsonPath(day(today) + ".pending").value(0));

        LocalDate yesterday = today.minusDays(1);
        month(a, YearMonth.from(yesterday), null)
                .andExpect(jsonPath(day(yesterday) + ".total").value(2))
                .andExpect(jsonPath(day(yesterday) + ".done").value(0))
                .andExpect(jsonPath(day(yesterday) + ".pending").value(0));

        // 참여하지 않은 사람의 기록은 비어 있다
        month(b, YearMonth.from(today), null)
                .andExpect(jsonPath("$.challenges.length()").value(0))
                .andExpect(jsonPath(day(today) + ".total").value(0))
                .andExpect(jsonPath("$.summary.monthRate").doesNotExist());
    }

    @Test
    @DisplayName("주 N회 챌린지에서 인증하지 않아도 되는 날은 세지 않고, 넘기면 횟수를 못 채우는 날만 실패다")
    void weeklyRest() throws Exception {
        long id = challenge("WEEKLY_N", 1);
        join(id, a);
        verify(id, a);
        startedDaysAgo(id, 1);

        LocalDate yesterday = today.minusDays(1);
        month(a, YearMonth.from(yesterday), null).andExpect(jsonPath(day(yesterday) + ".total").value(0));
        mvc.perform(get("/api/records/days/" + yesterday).header("Authorization", "Bearer " + a))
                .andExpect(jsonPath("$.items[0].result").value("REST"));

        // 매일 챌린지를 같이 하면: 주 N회가 쉬는 날에는 매일 챌린지만 인증해도 그날은 성공(1/1)
        long daily = challenge("DAILY", null);
        join(daily, a);
        verify(daily, a);
        month(a, YearMonth.from(today), null)
                .andExpect(jsonPath(day(today) + ".total").value(2))
                .andExpect(jsonPath(day(today) + ".done").value(2));

        // 주 6회를 사흘 전에 시작해 한 번도 안 했으면: 첫날은 쉬어도 됐고(남은 6일에 6번), 둘째 날부터는 실패
        long six = challenge("WEEKLY_N", 6);
        join(six, b);
        startedDaysAgo(six, 3);
        mvc.perform(get("/api/records/days/" + today.minusDays(3)).header("Authorization", "Bearer " + b))
                .andExpect(jsonPath("$.items[0].result").value("REST"));
        mvc.perform(get("/api/records/days/" + today.minusDays(2)).header("Authorization", "Bearer " + b))
                .andExpect(jsonPath("$.items[0].result").value("FAIL"));
        mvc.perform(get("/api/records/days/" + today).header("Authorization", "Bearer " + b))
                .andExpect(jsonPath("$.items[0].result").value("PENDING"));
    }

    @Test
    @DisplayName("날짜를 누르면 그날 결과와 내 인증 사진이 오고, 남의 사진은 볼 수 없다")
    void dayAndPhoto() throws Exception {
        long id = challenge("DAILY", null);
        join(id, a);
        join(id, b);
        verify(id, a);

        String body = mvc.perform(get("/api/records/days/" + today).header("Authorization", "Bearer " + a))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.items[0].challengeId").value(id))
                .andExpect(jsonPath("$.items[0].result").value("DONE"))
                .andExpect(jsonPath("$.diaries.length()").value(0))
                .andReturn().getResponse().getContentAsString();
        Matcher m = Pattern.compile("\"verificationId\":(\\d+)").matcher(body);
        assertThat(m.find()).isTrue();
        String photo = "/api/records/photos/" + m.group(1);

        mvc.perform(get(photo).header("Authorization", "Bearer " + a)).andExpect(status().isOk());
        mvc.perform(get(photo).header("Authorization", "Bearer " + b)).andExpect(status().isNotFound());
        mvc.perform(get("/api/records/days/" + today).header("Authorization", "Bearer " + b))
                .andExpect(jsonPath("$.items[0].result").value("PENDING"))
                .andExpect(jsonPath("$.items[0].verificationId").doesNotExist());
        mvc.perform(get("/api/records")).andExpect(status().isUnauthorized());

        // 시작한 뒤에 들어온 챌린지는 들어온 날부터 센다 (그 전 날은 내 실패가 아니다)
        jdbc.update("UPDATE challenges SET start_date = ? WHERE id = ?", today.minusDays(2), id);
        LocalDate yesterday = today.minusDays(1);
        month(a, YearMonth.from(yesterday), null).andExpect(jsonPath(day(yesterday) + ".total").value(0));
    }

    @Test
    @DisplayName("일기는 하루에 여러 개 쓸 수 있고, 지난 날짜도 쓰고 고칠 수 있고, 본인만 보고, 앞날은 쓸 수 없다")
    void diary() throws Exception {
        LocalDate past = today.minusDays(3);
        long first = createDiary(a, past, "{\"content\":\"아침에는 조금 힘들었다\"}");
        long second = createDiary(a, past, "{\"content\":\"저녁에는 해냈다\",\"mood\":\"GREAT\"}");
        updateDiary(a, first, "{\"content\":\"아침에는 꽤 힘들었다\"}").andExpect(status().isNoContent());

        // 쓴 순서대로 두 개가 따로 남는다
        mvc.perform(get("/api/records/days/" + past).header("Authorization", "Bearer " + a))
                .andExpect(jsonPath("$.diaries.length()").value(2))
                .andExpect(jsonPath("$.diaries[0].id").value(first))
                .andExpect(jsonPath("$.diaries[0].content").value("아침에는 꽤 힘들었다"))
                .andExpect(jsonPath("$.diaries[1].content").value("저녁에는 해냈다"))
                .andExpect(jsonPath("$.week[6].mood").value("GREAT"));
        month(a, YearMonth.from(past), null).andExpect(jsonPath(day(past) + ".diary").value(true));

        // 다른 사람에게는 보이지 않고, 고치거나 지울 수도 없다
        mvc.perform(get("/api/records/days/" + past).header("Authorization", "Bearer " + b))
                .andExpect(jsonPath("$.diaries.length()").value(0));
        updateDiary(b, first, "{\"content\":\"남의 일기\"}").andExpect(status().isNotFound());
        mvc.perform(delete("/api/records/diaries/" + first).header("Authorization", "Bearer " + b))
                .andExpect(status().isNotFound());

        postDiary(a, today.plusDays(1), "{\"content\":\"내일 일기\"}").andExpect(status().isBadRequest());
        postDiary(a, past, "{\"content\":\"" + "가".repeat(501) + "\"}").andExpect(status().isBadRequest());
        postDiary(a, past, "{\"content\":\"  \"}").andExpect(status().isBadRequest());

        mvc.perform(delete("/api/records/diaries/" + first).header("Authorization", "Bearer " + a))
                .andExpect(status().isNoContent());
        mvc.perform(get("/api/records/days/" + past).header("Authorization", "Bearer " + a))
                .andExpect(jsonPath("$.diaries.length()").value(1))
                .andExpect(jsonPath("$.diaries[0].id").value(second));
    }

    @Test
    @DisplayName("일기에 기분 · 챌린지 태그 · 사진을 남길 수 있고, 최근 7일 줄에 기분이 나온다")
    void moodTagsAndPhoto() throws Exception {
        long mine = challenge("DAILY", null);
        long notMine = challenge("DAILY", null);
        join(mine, a);

        long id = createDiary(a, today, "{\"content\":\"물 마시기 성공\",\"mood\":\"GOOD\",\"challengeIds\":["
                + mine + "," + notMine + "]}");
        String photo = "/api/records/diaries/" + id + "/photo";
        mvc.perform(multipart(photo).file(new MockMultipartFile("file", "day.jpg", "image/jpeg", photo()))
                        .header("Authorization", "Bearer " + a))
                .andExpect(status().isNoContent());

        // 참여하지 않은 챌린지는 태그되지 않는다
        mvc.perform(get("/api/records/days/" + today).header("Authorization", "Bearer " + a))
                .andExpect(jsonPath("$.diaries[0].mood").value("GOOD"))
                .andExpect(jsonPath("$.diaries[0].tags.length()").value(1))
                .andExpect(jsonPath("$.diaries[0].tags[0]").value(mine))
                .andExpect(jsonPath("$.diaries[0].photo").value(true))
                .andExpect(jsonPath("$.week.length()").value(7))
                .andExpect(jsonPath("$.week[6].mood").value("GOOD"))
                .andExpect(jsonPath("$.week[6].written").value(true))
                .andExpect(jsonPath("$.week[5].written").value(false));
        mvc.perform(get(photo).header("Authorization", "Bearer " + a)).andExpect(status().isOk());
        mvc.perform(get(photo).header("Authorization", "Bearer " + b)).andExpect(status().isNotFound());

        updateDiary(a, id, "{\"content\":\"\",\"mood\":\"WOW\"}").andExpect(status().isBadRequest());

        // 글과 기분을 비워도 사진이 있으면 일기는 남고, 사진까지 지우면 사라진다
        updateDiary(a, id, "{\"content\":\"\"}").andExpect(status().isNoContent());
        mvc.perform(get("/api/records/days/" + today).header("Authorization", "Bearer " + a))
                .andExpect(jsonPath("$.diaries[0].photo").value(true))
                .andExpect(jsonPath("$.diaries[0].mood").doesNotExist());
        mvc.perform(delete(photo).header("Authorization", "Bearer " + a)).andExpect(status().isNoContent());
        mvc.perform(get("/api/records/days/" + today).header("Authorization", "Bearer " + a))
                .andExpect(jsonPath("$.diaries.length()").value(0))
                .andExpect(jsonPath("$.week[6].written").value(false));

        // 사진만으로 일기를 시작할 수도 있다
        mvc.perform(multipart("/api/records/days/" + today + "/diaries/photo")
                        .file(new MockMultipartFile("file", "day.jpg", "image/jpeg", photo()))
                        .header("Authorization", "Bearer " + a))
                .andExpect(status().isCreated());
        mvc.perform(get("/api/records/days/" + today).header("Authorization", "Bearer " + a))
                .andExpect(jsonPath("$.diaries.length()").value(1))
                .andExpect(jsonPath("$.diaries[0].photo").value(true));
    }

    // ---------- helpers ----------

    /** 캘린더 응답에서 그 날짜 칸 */
    private static String day(LocalDate date) {
        return "$.days[" + (date.getDayOfMonth() - 1) + "]";
    }

    private ResultActions month(String token, YearMonth month, Long challengeId) throws Exception {
        return mvc.perform(get("/api/records").param("month", month.toString())
                        .param("challengeId", challengeId == null ? "" : challengeId.toString())
                        .header("Authorization", "Bearer " + token))
                .andExpect(status().isOk());
    }

    private ResultActions postDiary(String token, LocalDate date, String json) throws Exception {
        return mvc.perform(post("/api/records/days/" + date + "/diaries").header("Authorization", "Bearer " + token)
                .contentType(MediaType.APPLICATION_JSON).content(json));
    }

    private long createDiary(String token, LocalDate date, String json) throws Exception {
        String res = postDiary(token, date, json).andExpect(status().isCreated())
                .andReturn().getResponse().getContentAsString();
        Matcher m = ID.matcher(res);
        assertThat(m.find()).isTrue();
        return Long.parseLong(m.group(1));
    }

    private ResultActions updateDiary(String token, long id, String json) throws Exception {
        return mvc.perform(put("/api/records/diaries/" + id).header("Authorization", "Bearer " + token)
                .contentType(MediaType.APPLICATION_JSON).content(json));
    }

    /** 챌린지가 며칠 전에 시작했고, 참가자도 그때부터 함께한 것으로 */
    private void startedDaysAgo(long challengeId, int days) {
        jdbc.update("UPDATE challenges SET start_date = ? WHERE id = ?", today.minusDays(days), challengeId);
        jdbc.update("UPDATE challenge_participants SET joined_at = ? WHERE challenge_id = ?",
                today.minusDays(days).atStartOfDay(), challengeId);
    }

    private long challenge(String frequency, Integer weeklyCount) throws Exception {
        String body = """
                {"categoryId":1,"title":"기록 테스트","description":"인증","mode":"FREE",
                 "startDate":"%s","endDate":"%s","frequencyType":"%s","weeklyCount":%s,"maxParticipants":10}
                """.formatted(today, today.plusDays(13), frequency, weeklyCount).replace("\n", "");
        String res = mvc.perform(post("/api/challenges").header("Authorization", "Bearer " + host)
                        .contentType(MediaType.APPLICATION_JSON).content(body))
                .andExpect(status().isCreated()).andReturn().getResponse().getContentAsString();
        Matcher m = ID.matcher(res);
        assertThat(m.find()).isTrue();
        return Long.parseLong(m.group(1));
    }

    private void join(long id, String token) throws Exception {
        mvc.perform(post("/api/challenges/" + id + "/participants").header("Authorization", "Bearer " + token))
                .andExpect(status().isOk());
    }

    private void verify(long id, String token) throws Exception {
        mvc.perform(multipart("/api/challenges/" + id + "/verifications")
                        .file(new MockMultipartFile("file", "camera.jpg", "image/jpeg", photo()))
                        .header("Authorization", "Bearer " + token))
                .andExpect(status().isCreated());
    }

    private static byte[] photo() throws IOException {
        BufferedImage image = new BufferedImage(80, 60, BufferedImage.TYPE_INT_RGB);
        Graphics2D g = image.createGraphics();
        g.setColor(new Color(ThreadLocalRandom.current().nextInt(0xFFFFFF)));
        g.fillRect(0, 0, 80, 60);
        g.dispose();
        ByteArrayOutputStream out = new ByteArrayOutputStream();
        ImageIO.write(image, "png", out);
        return out.toByteArray();
    }

    private User newUser() {
        String suffix = UUID.randomUUID().toString().replace("-", "").substring(0, 12);
        return userRepository.saveAndFlush(User.createWithPassword(suffix + "@example.com", "unused", "g" + suffix,
                "g".repeat(52) + suffix));
    }

    private String tokenOf(User user) {
        return jwtProvider.createAccessToken(user.getId(), "USER");
    }
}
