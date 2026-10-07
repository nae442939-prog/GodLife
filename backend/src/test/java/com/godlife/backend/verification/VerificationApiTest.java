package com.godlife.backend.verification;

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
import java.time.LocalTime;
import java.util.UUID;
import java.util.concurrent.ThreadLocalRandom;
import java.util.regex.Matcher;
import java.util.regex.Pattern;

import static org.assertj.core.api.Assertions.assertThat;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.multipart;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.post;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.content;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.jsonPath;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

/** 인증 사진 제출 통합 테스트. 사진 파일은 target/test-uploads 에 저장된다. */
@SpringBootTest
@AutoConfigureMockMvc
@ActiveProfiles("test")
@Transactional
class VerificationApiTest {

    private static final Pattern ID = Pattern.compile("\"id\":(\\d+)");

    @Autowired MockMvc mvc;
    @Autowired UserRepository userRepository;
    @Autowired JwtProvider jwtProvider;
    @Autowired Clock clock;

    private String host;
    private String friend;
    private String friend2;
    private String stranger;
    private LocalDate today;

    @BeforeEach
    void setUp() {
        host = tokenOf(newUser());
        friend = tokenOf(newUser());
        friend2 = tokenOf(newUser());
        stranger = tokenOf(newUser());
        today = LocalDate.now(clock);
    }

    @Test
    @DisplayName("오늘 시작한 챌린지에 인증하면 바로 인정되고, 같은 날 다시 올릴 수 없다")
    void submitOncePerDay() throws Exception {
        long id = challenge(today, today.plusDays(6), "");
        join(id, friend);

        me(id, friend).andExpect(status().isOk())
                .andExpect(jsonPath("$.state").value("OPEN"))
                .andExpect(jsonPath("$.successDays").value(0))
                .andExpect(jsonPath("$.targetCount").value(7));

        submit(id, friend, photo()).andExpect(status().isCreated())
                .andExpect(jsonPath("$.mine").value(true))
                .andExpect(jsonPath("$.receivedAt").exists());

        submit(id, friend, photo()).andExpect(status().isConflict())
                .andExpect(jsonPath("$.code").value("ALREADY_VERIFIED_TODAY"));

        me(id, friend).andExpect(jsonPath("$.state").value("DONE_TODAY"))
                .andExpect(jsonPath("$.successDays").value(1))
                .andExpect(jsonPath("$.currentStreak").value(1))
                .andExpect(jsonPath("$.today").value(today.toString()));
    }

    @Test
    @DisplayName("참가자끼리(개설자 포함) 서로의 인증 사진을 볼 수 있고, 참가자가 아니면 404")
    void membersSeeEachOther() throws Exception {
        long id = challenge(today, today.plusDays(6), "");
        join(id, friend);
        join(id, friend2);

        long verificationId = idOf(submit(id, friend, photo()).andReturn().getResponse().getContentAsString());

        mvc.perform(get("/api/challenges/" + id + "/verifications").header("Authorization", "Bearer " + friend2))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.length()").value(1))
                .andExpect(jsonPath("$[0].mine").value(false));
        image(id, host, verificationId).andExpect(status().isOk())
                .andExpect(content().contentType(MediaType.IMAGE_JPEG));

        mvc.perform(get("/api/challenges/" + id + "/verifications").header("Authorization", "Bearer " + stranger))
                .andExpect(status().isNotFound());
        image(id, stranger, verificationId).andExpect(status().isNotFound());
        submit(id, stranger, photo()).andExpect(status().isNotFound());
    }

    @Test
    @DisplayName("참여하지 않은 개설자는 인증할 수 없다")
    void hostMustJoin() throws Exception {
        long id = challenge(today, today.plusDays(6), "");
        submit(id, host, photo()).andExpect(status().isConflict())
                .andExpect(jsonPath("$.code").value("NOT_JOINED"));
    }

    @Test
    @DisplayName("시작 전인 챌린지는 인증할 수 없다")
    void notStarted() throws Exception {
        long id = challenge(today.plusDays(1), today.plusDays(7), "");
        join(id, friend);
        submit(id, friend, photo()).andExpect(status().isConflict())
                .andExpect(jsonPath("$.code").value("CHALLENGE_NOT_IN_PROGRESS"));
        me(id, friend).andExpect(jsonPath("$.state").value("NOT_STARTED"));
    }

    @Test
    @DisplayName("이미 인증에 쓴 사진 파일은 다른 사람도 다시 쓸 수 없다")
    void duplicatePhoto() throws Exception {
        long id = challenge(today, today.plusDays(6), "");
        join(id, friend);
        join(id, friend2);
        byte[] same = photo();

        submit(id, friend, same).andExpect(status().isCreated());
        submit(id, friend2, same).andExpect(status().isConflict())
                .andExpect(jsonPath("$.code").value("DUPLICATE_PHOTO"));
    }

    @Test
    @DisplayName("인증 가능 시간대 밖에서는 인증할 수 없다")
    void timeWindow() throws Exception {
        // 지금 시각이 들어가지 않는 시간대
        boolean morning = LocalTime.now(clock).getHour() < 12;
        String window = morning ? ",\"verifyFrom\":\"22:00\",\"verifyUntil\":\"23:00\""
                : ",\"verifyFrom\":\"01:00\",\"verifyUntil\":\"02:00\"";
        long id = challenge(today, today.plusDays(6), window);
        join(id, friend);

        submit(id, friend, photo()).andExpect(status().isConflict())
                .andExpect(jsonPath("$.code").value("VERIFY_TIME_CLOSED"));
        me(id, friend).andExpect(jsonPath("$.state").value("TIME_CLOSED"));
    }

    @Test
    @DisplayName("사진이 아닌 파일은 거절한다")
    void rejectsNonImage() throws Exception {
        long id = challenge(today, today.plusDays(6), "");
        join(id, friend);
        submit(id, friend, "not an image".getBytes()).andExpect(status().isBadRequest())
                .andExpect(jsonPath("$.code").value("INVALID_IMAGE"));
        me(id, friend).andExpect(jsonPath("$.successDays").value(0));
    }

    @Test
    @DisplayName("내 챌린지: 참여 중·개설한 챌린지가 진행 중인 것부터 나오고, 오늘 인증 상태가 따라온다")
    void myChallenges() throws Exception {
        long upcoming = challenge(today.plusDays(2), today.plusDays(8), "");
        long ongoing = challenge(today, today.plusDays(6), "");
        join(upcoming, friend);
        join(ongoing, friend);

        mvc.perform(get("/api/me/challenges").header("Authorization", "Bearer " + friend))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.length()").value(2))
                .andExpect(jsonPath("$[0].id").value(ongoing))
                .andExpect(jsonPath("$[0].inProgress").value(true))
                .andExpect(jsonPath("$[0].verifyState").value("OPEN"))
                .andExpect(jsonPath("$[1].id").value(upcoming))
                .andExpect(jsonPath("$[1].verifyState").value("NOT_STARTED"));

        submit(ongoing, friend, photo()).andExpect(status().isCreated());
        mvc.perform(get("/api/me/challenges").header("Authorization", "Bearer " + friend))
                .andExpect(jsonPath("$[0].verifyState").value("DONE_TODAY"))
                .andExpect(jsonPath("$[0].successDays").value(1));

        // 개설만 하고 참여하지 않은 챌린지도 나온다 (인증 상태 없음)
        mvc.perform(get("/api/me/challenges").header("Authorization", "Bearer " + host))
                .andExpect(jsonPath("$.length()").value(2))
                .andExpect(jsonPath("$[0].host").value(true))
                .andExpect(jsonPath("$[0].joined").value(false))
                .andExpect(jsonPath("$[0].verifyState").doesNotExist());

        mvc.perform(get("/api/me/challenges").header("Authorization", "Bearer " + stranger))
                .andExpect(jsonPath("$.length()").value(0));
    }

    // ---------- helpers ----------

    private long challenge(LocalDate start, LocalDate end, String extra) throws Exception {
        String body = """
                {"categoryId":1,"title":"매일 러닝","description":"하루 30분","mode":"FREE",
                 "startDate":"%s","endDate":"%s","frequencyType":"DAILY","maxParticipants":10%s}
                """.formatted(start, end, extra).replace("\n", "");
        return idOf(mvc.perform(post("/api/challenges").header("Authorization", "Bearer " + host)
                        .contentType(MediaType.APPLICATION_JSON).content(body))
                .andExpect(status().isCreated()).andReturn().getResponse().getContentAsString());
    }

    private void join(long id, String token) throws Exception {
        mvc.perform(post("/api/challenges/" + id + "/participants").header("Authorization", "Bearer " + token))
                .andExpect(status().isOk());
    }

    private ResultActions submit(long id, String token, byte[] bytes) throws Exception {
        return mvc.perform(multipart("/api/challenges/" + id + "/verifications")
                .file(new MockMultipartFile("file", "camera.jpg", "image/jpeg", bytes))
                .header("Authorization", "Bearer " + token));
    }

    private ResultActions me(long id, String token) throws Exception {
        return mvc.perform(get("/api/challenges/" + id + "/verifications/me").header("Authorization", "Bearer " + token));
    }

    private ResultActions image(long id, String token, long verificationId) throws Exception {
        return mvc.perform(get("/api/challenges/" + id + "/verifications/" + verificationId + "/image")
                .header("Authorization", "Bearer " + token));
    }

    /** 매번 다른 색이라 파일 내용(해시)이 겹치지 않는 사진 */
    private static byte[] photo() throws IOException {
        BufferedImage image = new BufferedImage(120, 90, BufferedImage.TYPE_INT_RGB);
        Graphics2D g = image.createGraphics();
        g.setColor(new Color(ThreadLocalRandom.current().nextInt(0xFFFFFF)));
        g.fillRect(0, 0, 120, 90);
        g.dispose();
        ByteArrayOutputStream out = new ByteArrayOutputStream();
        ImageIO.write(image, "png", out);
        return out.toByteArray();
    }

    private User newUser() {
        String suffix = UUID.randomUUID().toString().replace("-", "").substring(0, 12);
        return userRepository.saveAndFlush(User.createWithPassword(suffix + "@example.com", "unused", "v" + suffix,
                "v".repeat(52) + suffix));
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
