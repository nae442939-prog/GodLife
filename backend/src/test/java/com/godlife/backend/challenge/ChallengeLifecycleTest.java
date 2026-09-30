package com.godlife.backend.challenge;

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
import java.util.UUID;
import java.util.concurrent.ThreadLocalRandom;
import java.util.regex.Matcher;
import java.util.regex.Pattern;

import static org.assertj.core.api.Assertions.assertThat;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.multipart;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.post;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.jsonPath;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

/** 진행 중 포기 + 자정 진행 관리(시작·종료 판정·연속 기록 초기화) */
@SpringBootTest
@AutoConfigureMockMvc
@ActiveProfiles("test")
@Transactional
class ChallengeLifecycleTest {

    private static final Pattern ID = Pattern.compile("\"id\":(\\d+)");

    @Autowired MockMvc mvc;
    @Autowired UserRepository userRepository;
    @Autowired JwtProvider jwtProvider;
    @Autowired ChallengeLifecycleService lifecycle;
    @Autowired Clock clock;

    private String host;
    private String friend;
    private String friend2;
    private LocalDate today;

    @BeforeEach
    void setUp() {
        host = tokenOf(newUser());
        friend = tokenOf(newUser());
        friend2 = tokenOf(newUser());
        today = LocalDate.now(clock);
    }

    @Test
    @DisplayName("포기하면 챌린지에서 나간다: 인원수·채팅·인증 사진·내 챌린지에서 빠지고 다시 참여할 수 없다")
    void giveUpLeavesChallenge() throws Exception {
        long id = challenge(today, today.plusDays(6), "PUBLIC");
        join(id, friend);
        join(id, friend2);

        giveUp(id, friend).andExpect(status().isNoContent());

        detail(id, friend).andExpect(jsonPath("$.participantCount").value(1)) // 참가자 2명 중 1명이 나감
                .andExpect(jsonPath("$.joined").value(false))
                .andExpect(jsonPath("$.member").value(false))
                .andExpect(jsonPath("$.myStatus").value("GAVE_UP"))
                .andExpect(jsonPath("$.participants.length()").value(1));
        mvc.perform(get("/api/challenges/" + id + "/messages").header("Authorization", "Bearer " + friend))
                .andExpect(status().isNotFound());
        mvc.perform(get("/api/challenges/" + id + "/verifications").header("Authorization", "Bearer " + friend))
                .andExpect(status().isNotFound());
        mvc.perform(get("/api/me/challenges").header("Authorization", "Bearer " + friend))
                .andExpect(jsonPath("$.length()").value(0));

        // 채팅방에 안내가 남는다
        mvc.perform(get("/api/challenges/" + id + "/messages").header("Authorization", "Bearer " + friend2))
                .andExpect(jsonPath("$[-1:].content").value(org.hamcrest.Matchers.hasItem(
                        org.hamcrest.Matchers.endsWith("님이 챌린지를 포기하고 나갔어요."))));

        // 시작일 당일이라 모집 기간이지만, 포기한 챌린지에는 다시 참여할 수 없다
        mvc.perform(post("/api/challenges/" + id + "/participants").header("Authorization", "Bearer " + friend))
                .andExpect(status().isForbidden())
                .andExpect(jsonPath("$.code").value("GAVE_UP_CHALLENGE"));
    }

    @Test
    @DisplayName("시작 전에는 포기가 아니라 참여 취소를 쓴다")
    void cannotGiveUpBeforeStart() throws Exception {
        long id = challenge(today.plusDays(1), today.plusDays(7), "PUBLIC");
        join(id, friend);
        giveUp(id, friend).andExpect(status().isConflict())
                .andExpect(jsonPath("$.code").value("CHALLENGE_NOT_IN_PROGRESS"));
    }

    @Test
    @DisplayName("시작일이 되면 진행 중으로 바뀌고, 그날은 여전히 참여할 수 있다")
    void startsOnStartDate() throws Exception {
        long id = challenge(today, today.plusDays(6), "PUBLIC");
        lifecycle.run(today);

        detail(id, host).andExpect(jsonPath("$.status").value("ONGOING"))
                .andExpect(jsonPath("$.recruiting").value(true));
        join(id, friend);
        // 모집 목록에도 그날까지는 나온다
        mvc.perform(get("/api/challenges").param("q", "진행관리"))
                .andExpect(jsonPath("$.items[?(@.id == " + id + ")]").exists());
    }

    @Test
    @DisplayName("종료일이 지나면 종료되고, 필요한 인증을 채운 사람은 성공·못 채운 사람은 실패")
    void endsAndJudges() throws Exception {
        long id = challenge(today, today, "PUBLIC"); // 하루짜리 → 1회 인증이면 성공
        join(id, friend);
        join(id, friend2);
        verify(id, friend).andExpect(status().isCreated());

        lifecycle.run(today.plusDays(1));
        lifecycle.run(today.plusDays(1)); // 두 번 돌아도 결과가 같다

        detail(id, friend).andExpect(jsonPath("$.status").value("SETTLED")) // 무료 챌린지는 종료와 함께 정산 완료
                .andExpect(jsonPath("$.myStatus").value("COMPLETED"));
        detail(id, friend2).andExpect(jsonPath("$.myStatus").value("FAILED"));
        mvc.perform(get("/api/challenges/" + id + "/verifications/me").header("Authorization", "Bearer " + friend))
                .andExpect(jsonPath("$.status").value("COMPLETED"))
                .andExpect(jsonPath("$.state").value("ENDED"));
    }

    @Test
    @DisplayName("매일 챌린지에서 어제 인증을 빼먹으면 연속 기록이 0이 된다 (어제 했으면 그대로)")
    void resetsMissedStreak() throws Exception {
        long id = challenge(today, today.plusDays(6), "PUBLIC");
        join(id, friend);
        verify(id, friend).andExpect(status().isCreated());

        lifecycle.run(today.plusDays(1)); // 어제(=오늘) 인증함 → 유지
        streak(id, friend).andExpect(jsonPath("$.currentStreak").value(1));

        lifecycle.run(today.plusDays(2)); // 어제(=내일) 안 함 → 0
        streak(id, friend).andExpect(jsonPath("$.currentStreak").value(0))
                .andExpect(jsonPath("$.maxStreak").value(1));
    }

    // ---------- helpers ----------

    private long challenge(LocalDate start, LocalDate end, String visibility) throws Exception {
        String body = """
                {"categoryId":1,"title":"진행관리 테스트","description":"매일 인증","mode":"FREE","visibility":"%s",
                 "startDate":"%s","endDate":"%s","frequencyType":"DAILY","maxParticipants":10}
                """.formatted(visibility, start, end).replace("\n", "");
        return idOf(mvc.perform(post("/api/challenges").header("Authorization", "Bearer " + host)
                        .contentType(MediaType.APPLICATION_JSON).content(body))
                .andExpect(status().isCreated()).andReturn().getResponse().getContentAsString());
    }

    private void join(long id, String token) throws Exception {
        mvc.perform(post("/api/challenges/" + id + "/participants").header("Authorization", "Bearer " + token))
                .andExpect(status().isOk());
    }

    private ResultActions giveUp(long id, String token) throws Exception {
        return mvc.perform(post("/api/challenges/" + id + "/participants/me/give-up")
                .header("Authorization", "Bearer " + token));
    }

    private ResultActions detail(long id, String token) throws Exception {
        return mvc.perform(get("/api/challenges/" + id).header("Authorization", "Bearer " + token))
                .andExpect(status().isOk());
    }

    private ResultActions verify(long id, String token) throws Exception {
        return mvc.perform(multipart("/api/challenges/" + id + "/verifications")
                .file(new MockMultipartFile("file", "camera.jpg", "image/jpeg", photo()))
                .header("Authorization", "Bearer " + token));
    }

    private ResultActions streak(long id, String token) throws Exception {
        return mvc.perform(get("/api/challenges/" + id + "/verifications/me").header("Authorization", "Bearer " + token));
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
        return userRepository.saveAndFlush(User.createWithPassword(suffix + "@example.com", "unused", "l" + suffix,
                "l".repeat(52) + suffix));
    }

    private String tokenOf(User user) {
        return jwtProvider.createAccessToken(user.getId(), "USER");
    }

    private static long idOf(String body) {
        Matcher m = ID.matcher(body);
        assertThat(m.find()).isTrue();
        return Long.parseLong(m.group(1));
    }
}
