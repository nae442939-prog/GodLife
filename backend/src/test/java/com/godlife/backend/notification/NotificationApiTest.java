package com.godlife.backend.notification;

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
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.delete;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.multipart;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.post;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.put;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.jsonPath;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

/** 알림함: 팔로우 알림 · 읽음 · 알림 설정 · 오늘 인증하는 날 알림 */
@SpringBootTest
@AutoConfigureMockMvc
@ActiveProfiles("test")
@Transactional
class NotificationApiTest {

    private static final Pattern ID = Pattern.compile("\"id\":(\\d+)");

    @Autowired MockMvc mvc;
    @Autowired UserRepository userRepository;
    @Autowired JwtProvider jwtProvider;
    @Autowired NotificationService notificationService;
    @Autowired Clock clock;

    private User aUser;
    private User bUser;
    private String a;
    private String b;

    @BeforeEach
    void setUp() {
        aUser = newUser();
        bUser = newUser();
        a = tokenOf(aUser);
        b = tokenOf(bUser);
    }

    @Test
    @DisplayName("팔로우하면 상대 알림함에 쌓이고, 다시 팔로우해도 한 번만. 읽으면 안 읽은 수가 줄고 남의 알림은 못 읽는다")
    void followNotification() throws Exception {
        follow(a, bUser);
        mvc.perform(delete("/api/users/" + bUser.getId() + "/follow").header("Authorization", "Bearer " + a));
        follow(a, bUser);

        unread(b).andExpect(jsonPath("$.count").value(1));
        String body = list(b)
                .andExpect(jsonPath("$.length()").value(1))
                .andExpect(jsonPath("$[0].type").value("FOLLOW"))
                .andExpect(jsonPath("$[0].body").value(aUser.getNickname() + "님이 나를 팔로우했어요."))
                .andExpect(jsonPath("$[0].link").value("/users/" + aUser.getId()))
                .andExpect(jsonPath("$[0].read").value(false))
                .andReturn().getResponse().getContentAsString();
        Matcher m = ID.matcher(body);
        assertThat(m.find()).isTrue();
        String id = m.group(1);

        // 남이 읽음 처리해도 바뀌지 않는다
        mvc.perform(post("/api/notifications/" + id + "/read").header("Authorization", "Bearer " + a))
                .andExpect(status().isNoContent());
        unread(b).andExpect(jsonPath("$.count").value(1));

        mvc.perform(post("/api/notifications/" + id + "/read").header("Authorization", "Bearer " + b))
                .andExpect(status().isNoContent());
        unread(b).andExpect(jsonPath("$.count").value(0));
        list(b).andExpect(jsonPath("$[0].read").value(true));
        list(a).andExpect(jsonPath("$.length()").value(0));
        mvc.perform(get("/api/notifications")).andExpect(status().isUnauthorized());
    }

    @Test
    @DisplayName("설정에서 끈 종류의 알림은 오지 않는다")
    void settings() throws Exception {
        mvc.perform(get("/api/notifications/settings").header("Authorization", "Bearer " + b))
                .andExpect(jsonPath("$.verifyReminder").value(true))
                .andExpect(jsonPath("$.social").value(true));
        mvc.perform(put("/api/notifications/settings").header("Authorization", "Bearer " + b)
                        .contentType(MediaType.APPLICATION_JSON)
                        .content("{\"verifyReminder\":true,\"challengeResult\":true,\"social\":false}"))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.social").value(false));

        follow(a, bUser);
        unread(b).andExpect(jsonPath("$.count").value(0));
        mvc.perform(get("/api/notifications/settings").header("Authorization", "Bearer " + b))
                .andExpect(jsonPath("$.social").value(false));
    }

    @Test
    @DisplayName("오늘 인증하는 날 알림은 아직 인증 안 한 참가자에게 하루 한 번만 가고, 인증하면 읽음이 된다")
    void verifyReminder() throws Exception {
        LocalDate today = LocalDate.now(clock);
        long id = challenge(today);
        join(id, a);
        join(id, b);
        verify(id, b); // b 는 이미 인증함

        notificationService.remindToday(today);
        notificationService.remindToday(today);

        list(a).andExpect(jsonPath("$.length()").value(1))
                .andExpect(jsonPath("$[0].type").value("VERIFY_REMINDER"))
                .andExpect(jsonPath("$[0].title").value("오늘 인증하는 날이에요!"))
                .andExpect(jsonPath("$[0].link").value("/challenges/" + id));
        list(b).andExpect(jsonPath("$.length()").value(0));

        verify(id, a);
        unread(a).andExpect(jsonPath("$.count").value(0));

        mvc.perform(post("/api/notifications/read-all").header("Authorization", "Bearer " + a))
                .andExpect(status().isNoContent());
    }

    // ---------- helpers ----------

    private ResultActions list(String token) throws Exception {
        return mvc.perform(get("/api/notifications").header("Authorization", "Bearer " + token))
                .andExpect(status().isOk());
    }

    private ResultActions unread(String token) throws Exception {
        return mvc.perform(get("/api/notifications/unread-count").header("Authorization", "Bearer " + token))
                .andExpect(status().isOk());
    }

    private void follow(String token, User target) throws Exception {
        mvc.perform(post("/api/users/" + target.getId() + "/follow").header("Authorization", "Bearer " + token))
                .andExpect(status().isNoContent());
    }

    private long challenge(LocalDate today) throws Exception {
        String host = tokenOf(newUser());
        String body = """
                {"categoryId":1,"title":"알림 테스트","description":"매일 인증","mode":"FREE",
                 "startDate":"%s","endDate":"%s","frequencyType":"DAILY","maxParticipants":10}
                """.formatted(today, today.plusDays(6)).replace("\n", "");
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
        return userRepository.saveAndFlush(User.createWithPassword(suffix + "@example.com", "unused", "n" + suffix,
                "n".repeat(52) + suffix));
    }

    private String tokenOf(User user) {
        return jwtProvider.createAccessToken(user.getId(), "USER");
    }
}
