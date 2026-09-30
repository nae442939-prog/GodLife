package com.godlife.backend.profile;

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
import org.springframework.transaction.annotation.Transactional;

import javax.imageio.ImageIO;
import java.awt.image.BufferedImage;
import java.io.ByteArrayOutputStream;
import java.time.Clock;
import java.time.LocalDate;
import java.util.UUID;
import java.util.regex.Matcher;
import java.util.regex.Pattern;

import static org.assertj.core.api.Assertions.assertThat;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.multipart;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.post;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.jsonPath;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

/** 회원 프로필: 비로그인 조회, 개인정보 없음, 기록, 공개 챌린지만 */
@SpringBootTest
@AutoConfigureMockMvc
@ActiveProfiles("test")
@Transactional
class ProfileApiTest {

    private static final Pattern ID = Pattern.compile("\"id\":(\\d+)");

    @Autowired MockMvc mvc;
    @Autowired UserRepository userRepository;
    @Autowired JwtProvider jwtProvider;
    @Autowired Clock clock;

    private String host;
    private User meUser;
    private String me;
    private LocalDate today;

    @BeforeEach
    void setUp() {
        host = tokenOf(newUser());
        meUser = newUser();
        me = tokenOf(meUser);
        today = LocalDate.now(clock);
    }

    @Test
    @DisplayName("비로그인도 볼 수 있고, 이메일·휴대폰 같은 개인정보는 없다. 내 프로필이면 mine")
    void publicWithoutPrivateInfo() throws Exception {
        String body = mvc.perform(get("/api/users/" + meUser.getId() + "/profile"))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.nickname").value(meUser.getNickname()))
                .andExpect(jsonPath("$.mine").value(false))
                .andReturn().getResponse().getContentAsString();
        assertThat(body).doesNotContain(meUser.getEmail()).doesNotContain("phone").doesNotContain("email");

        mvc.perform(get("/api/users/" + meUser.getId() + "/profile").header("Authorization", "Bearer " + me))
                .andExpect(jsonPath("$.mine").value(true));
        mvc.perform(get("/api/users/999999999/profile")).andExpect(status().isNotFound());
    }

    @Test
    @DisplayName("기록과 참여 중인 챌린지 — 비공개 챌린지는 보이지 않는다")
    void statsAndPublicChallengesOnly() throws Exception {
        long open = challenge("PUBLIC");
        long secret = challenge("PRIVATE");
        join(open);
        String code = mvc.perform(get("/api/challenges/" + secret).header("Authorization", "Bearer " + host))
                .andReturn().getResponse().getContentAsString().replaceAll(".*\"inviteCode\":\"([A-Z0-9]+)\".*", "$1");
        mvc.perform(post("/api/challenges/invite/" + code + "/participants").header("Authorization", "Bearer " + me))
                .andExpect(status().isOk());
        mvc.perform(multipart("/api/challenges/" + open + "/verifications")
                        .file(new MockMultipartFile("file", "c.png", "image/png", png()))
                        .header("Authorization", "Bearer " + me))
                .andExpect(status().isCreated());

        mvc.perform(get("/api/users/" + meUser.getId() + "/profile"))
                .andExpect(jsonPath("$.monthVerify").value(1))
                .andExpect(jsonPath("$.maxStreak").value(1))
                .andExpect(jsonPath("$.successRate").doesNotExist())
                .andExpect(jsonPath("$.challenges.length()").value(1))
                .andExpect(jsonPath("$.challenges[0].id").value(open))
                .andExpect(jsonPath("$.challenges[0].inProgress").value(true));
    }

    // ---------- helpers ----------

    private long challenge(String visibility) throws Exception {
        String body = """
                {"categoryId":1,"title":"프로필 테스트","description":"매일 인증","mode":"FREE","visibility":"%s",
                 "startDate":"%s","endDate":"%s","frequencyType":"DAILY","maxParticipants":10}
                """.formatted(visibility, today, today.plusDays(6)).replace("\n", "");
        String res = mvc.perform(post("/api/challenges").header("Authorization", "Bearer " + host)
                        .contentType(MediaType.APPLICATION_JSON).content(body))
                .andExpect(status().isCreated()).andReturn().getResponse().getContentAsString();
        Matcher m = ID.matcher(res);
        assertThat(m.find()).isTrue();
        return Long.parseLong(m.group(1));
    }

    private void join(long id) throws Exception {
        mvc.perform(post("/api/challenges/" + id + "/participants").header("Authorization", "Bearer " + me))
                .andExpect(status().isOk());
    }

    private static byte[] png() throws Exception {
        ByteArrayOutputStream out = new ByteArrayOutputStream();
        ImageIO.write(new BufferedImage(40, 30, BufferedImage.TYPE_INT_RGB), "png", out);
        return out.toByteArray();
    }

    private User newUser() {
        String suffix = UUID.randomUUID().toString().replace("-", "").substring(0, 12);
        return userRepository.saveAndFlush(User.createWithPassword(suffix + "@example.com", "unused", "p" + suffix,
                "p".repeat(52) + suffix));
    }

    private String tokenOf(User user) {
        return jwtProvider.createAccessToken(user.getId(), "USER");
    }
}
