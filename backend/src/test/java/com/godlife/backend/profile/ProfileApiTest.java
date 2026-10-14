package com.godlife.backend.profile;

import com.godlife.backend.auth.JwtProvider;
import com.godlife.backend.user.User;
import com.godlife.backend.user.UserRepository;
import jakarta.persistence.EntityManager;
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
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.put;
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
    @Autowired JdbcTemplate jdbc;
    @Autowired EntityManager entityManager;

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
                .andExpect(jsonPath("$.tier").value("BRONZE"))
                .andExpect(jsonPath("$.badges.length()").value(15))
                .andExpect(jsonPath("$.badges[0].code").value("FIRST_VERIFY"))
                .andExpect(jsonPath("$.badges[0].earned").value(true))
                .andExpect(jsonPath("$.badges[1].code").value("VERIFY_10"))
                .andExpect(jsonPath("$.badges[1].earned").value(false))
                .andExpect(jsonPath("$.badges[1].current").value(1))
                .andExpect(jsonPath("$.badges[1].target").value(10))
                .andExpect(jsonPath("$.maxStreak").value(1))
                .andExpect(jsonPath("$.successRate").doesNotExist())
                .andExpect(jsonPath("$.challenges.length()").value(1))
                .andExpect(jsonPath("$.challenges[0].id").value(open))
                .andExpect(jsonPath("$.challenges[0].inProgress").value(true));
    }

    @Test
    @DisplayName("뱃지 착용: 딴 뱃지만 착용할 수 있고, 프로필에 보이며, 비우면 해제된다")
    void equipBadge() throws Exception {
        // 아직 딴 뱃지가 없으면 착용할 수 없다
        mvc.perform(put("/api/users/me/badge").header("Authorization", "Bearer " + me)
                        .contentType(MediaType.APPLICATION_JSON).content("{\"code\":\"FIRST_VERIFY\"}"))
                .andExpect(status().isBadRequest())
                .andExpect(jsonPath("$.code").value("BADGE_NOT_EARNED"));

        long open = challenge("PUBLIC");
        join(open);
        mvc.perform(multipart("/api/challenges/" + open + "/verifications")
                        .file(new MockMultipartFile("file", "c.png", "image/png", png()))
                        .header("Authorization", "Bearer " + me))
                .andExpect(status().isCreated());

        mvc.perform(put("/api/users/me/badge").header("Authorization", "Bearer " + me)
                        .contentType(MediaType.APPLICATION_JSON).content("{\"code\":\"FIRST_VERIFY\"}"))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.equippedBadge").value("FIRST_VERIFY"));
        // 남이 봐도 보인다
        mvc.perform(get("/api/users/" + meUser.getId() + "/profile"))
                .andExpect(jsonPath("$.equippedBadge").value("FIRST_VERIFY"));
        // 없는 코드 · 아직 못 딴 뱃지는 안 된다 (착용 중인 것은 그대로)
        mvc.perform(put("/api/users/me/badge").header("Authorization", "Bearer " + me)
                        .contentType(MediaType.APPLICATION_JSON).content("{\"code\":\"VERIFY_10\"}"))
                .andExpect(status().isBadRequest());
        mvc.perform(get("/api/users/" + meUser.getId() + "/profile"))
                .andExpect(jsonPath("$.equippedBadge").value("FIRST_VERIFY"));

        mvc.perform(put("/api/users/me/badge").header("Authorization", "Bearer " + me)
                        .contentType(MediaType.APPLICATION_JSON).content("{\"code\":null}"))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.equippedBadge").doesNotExist());
        mvc.perform(put("/api/users/me/badge").contentType(MediaType.APPLICATION_JSON).content("{\"code\":null}"))
                .andExpect(status().isUnauthorized());
    }

    @Test
    @DisplayName("관리자 뱃지: 관리자에게만 있고, 관리자는 기록과 상관없이 뱃지를 모두 딴 상태다")
    void adminBadge() throws Exception {
        // 보통 회원에게는 없고 착용할 수도 없다
        mvc.perform(get("/api/users/" + meUser.getId() + "/profile"))
                .andExpect(jsonPath("$.badges.length()").value(15));
        mvc.perform(put("/api/users/me/badge").header("Authorization", "Bearer " + me)
                        .contentType(MediaType.APPLICATION_JSON).content("{\"code\":\"ADMIN\"}"))
                .andExpect(status().isBadRequest());

        jdbc.update("UPDATE users SET role = 'ADMIN' WHERE id = ?", meUser.getId());
        entityManager.clear();
        String admin = jwtProvider.createAccessToken(meUser.getId(), "ADMIN");

        mvc.perform(get("/api/users/" + meUser.getId() + "/profile"))
                .andExpect(jsonPath("$.badges.length()").value(16))
                .andExpect(jsonPath("$.badges[15].code").value("ADMIN"))
                .andExpect(jsonPath("$.badges[15].group").value("SPECIAL"))
                .andExpect(jsonPath("$.badges[15].earned").value(true))
                .andExpect(jsonPath("$.badges[?(@.earned == false)]").isEmpty())
                .andExpect(jsonPath("$.badges[3].code").value("VERIFY_100"))
                .andExpect(jsonPath("$.badges[3].current").value(100));
        // 인증한 적이 없어도 가장 어려운 뱃지를 착용할 수 있다
        mvc.perform(put("/api/users/me/badge").header("Authorization", "Bearer " + admin)
                        .contentType(MediaType.APPLICATION_JSON).content("{\"code\":\"STREAK_100\"}"))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.equippedBadge").value("STREAK_100"));
        mvc.perform(put("/api/users/me/badge").header("Authorization", "Bearer " + admin)
                        .contentType(MediaType.APPLICATION_JSON).content("{\"code\":\"ADMIN\"}"))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.equippedBadge").value("ADMIN"));
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
