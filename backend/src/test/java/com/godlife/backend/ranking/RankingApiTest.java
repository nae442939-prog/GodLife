package com.godlife.backend.ranking;

import com.godlife.backend.auth.JwtProvider;
import com.godlife.backend.challenge.ChallengeLifecycleService;
import com.godlife.backend.user.User;
import com.godlife.backend.user.UserRepository;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.boot.webmvc.test.autoconfigure.AutoConfigureMockMvc;
import org.springframework.http.MediaType;
import org.springframework.mock.web.MockMultipartFile;
import org.springframework.test.context.ActiveProfiles;
import org.springframework.test.web.servlet.MockMvc;
import org.springframework.transaction.annotation.Transactional;

import javax.imageio.ImageIO;
import java.awt.Color;
import java.awt.Graphics2D;
import java.awt.image.BufferedImage;
import java.io.ByteArrayOutputStream;
import java.io.IOException;
import java.time.Clock;
import java.time.LocalDate;
import java.util.UUID;
import java.util.concurrent.ThreadLocalRandom;
import java.util.regex.Matcher;
import java.util.regex.Pattern;

import static org.assertj.core.api.Assertions.assertThat;
import static org.hamcrest.Matchers.hasItem;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.multipart;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.post;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.jsonPath;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

/** 랭킹 메뉴: 전체(개인) 랭킹 기준별 순위·내 순위, 비로그인 조회, 챌린지(팀) 랭킹 */
@SpringBootTest
@AutoConfigureMockMvc
@ActiveProfiles("test")
@Transactional
class RankingApiTest {

    private static final Pattern ID = Pattern.compile("\"id\":(\\d+)");

    @Autowired MockMvc mvc;
    @Autowired UserRepository userRepository;
    @Autowired JwtProvider jwtProvider;
    @Autowired ChallengeLifecycleService lifecycle;
    @Autowired Clock clock;

    private String host;
    private User aUser;
    private String a;
    private String b;
    private LocalDate today;

    @BeforeEach
    void setUp() {
        host = tokenOf(newUser());
        aUser = newUser();
        a = tokenOf(aUser);
        b = tokenOf(newUser());
        today = LocalDate.now(clock);
    }

    @Test
    @DisplayName("이번 달 인증 수 랭킹에 내 순위가 같이 오고, 비로그인도 볼 수 있다")
    void monthVerify() throws Exception {
        long one = challenge(today, today.plusDays(6));
        long two = challenge(today, today.plusDays(6));
        join(one, a);
        join(two, a);
        join(one, b);
        verify(one, a);
        verify(two, a); // A: 2회
        verify(one, b); // B: 1회

        mvc.perform(get("/api/rankings/users").header("Authorization", "Bearer " + a))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.me.value").value(2.0))
                .andExpect(jsonPath("$.me.mine").value(true))
                .andExpect(jsonPath("$.top[?(@.nickname == '" + aUser.getNickname() + "')].mine").value(hasItem(true)));

        mvc.perform(get("/api/rankings/users"))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.me").doesNotExist());

        // 전체 랭킹(누적 성공 횟수)도 같은 인증을 센다
        mvc.perform(get("/api/rankings/users").param("metric", "total_success").header("Authorization", "Bearer " + a))
                .andExpect(jsonPath("$.me.value").value(2.0));
    }

    @Test
    @DisplayName("누적 성공률은 끝난 챌린지 기준: 하루짜리를 인증했으면 100%, 안 했으면 목록에 없다")
    void successRate() throws Exception {
        long id = challenge(today, today);
        join(id, a);
        join(id, b);
        verify(id, a);
        lifecycle.run(today.plusDays(1));

        mvc.perform(get("/api/rankings/users").param("metric", "success_rate").header("Authorization", "Bearer " + a))
                .andExpect(jsonPath("$.me.value").value(100.0));
        mvc.perform(get("/api/rankings/users").param("metric", "success_rate").header("Authorization", "Bearer " + b))
                .andExpect(jsonPath("$.me").doesNotExist());
    }

    @Test
    @DisplayName("최장 연속 기록 랭킹, 잘못된 기준은 기본(이번 달 인증)으로")
    void maxStreakAndFallback() throws Exception {
        long id = challenge(today, today.plusDays(6));
        join(id, a);
        verify(id, a);

        mvc.perform(get("/api/rankings/users").param("metric", "max_streak").header("Authorization", "Bearer " + a))
                .andExpect(jsonPath("$.me.value").value(1.0));
        mvc.perform(get("/api/rankings/users").param("metric", "nope").header("Authorization", "Bearer " + a))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.me.value").value(1.0));
    }

    @Test
    @DisplayName("챌린지(팀) 랭킹은 비로그인도 볼 수 있다 (오늘 시작한 챌린지는 끝난 기간이 없어 아직 없음)")
    void challengeRanking() throws Exception {
        long id = challenge(today, today.plusDays(6));
        join(id, a);
        join(id, b);

        String body = mvc.perform(get("/api/rankings/challenges"))
                .andExpect(status().isOk())
                .andReturn().getResponse().getContentAsString();
        assertThat(body).doesNotContain("\"id\":" + id + ",");
    }

    // ---------- helpers ----------

    private long challenge(LocalDate start, LocalDate end) throws Exception {
        String body = """
                {"categoryId":1,"title":"랭킹 테스트","description":"매일 인증","mode":"FREE",
                 "startDate":"%s","endDate":"%s","frequencyType":"DAILY","maxParticipants":10}
                """.formatted(start, end).replace("\n", "");
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
        return userRepository.saveAndFlush(User.createWithPassword(suffix + "@example.com", "unused", "r" + suffix,
                "r".repeat(52) + suffix));
    }

    private String tokenOf(User user) {
        return jwtProvider.createAccessToken(user.getId(), "USER");
    }
}
