package com.godlife.backend.follow;

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

/** 팔로우 · 맞팔로우 · 차단과 팔로우 · 친구 랭킹 */
@SpringBootTest
@AutoConfigureMockMvc
@ActiveProfiles("test")
@Transactional
class FollowApiTest {

    private static final Pattern ID = Pattern.compile("\"id\":(\\d+)");

    @Autowired MockMvc mvc;
    @Autowired UserRepository userRepository;
    @Autowired JwtProvider jwtProvider;
    @Autowired Clock clock;

    private User aUser;
    private User bUser;
    private User cUser;
    private String a;
    private String b;
    private String c;

    @BeforeEach
    void setUp() {
        aUser = newUser();
        bUser = newUser();
        cUser = newUser();
        a = tokenOf(aUser);
        b = tokenOf(bUser);
        c = tokenOf(cUser);
    }

    @Test
    @DisplayName("팔로우하면 프로필에 팔로잉·팔로워가 반영되고, 서로 팔로우하면 맞팔로우. 두 번 눌러도 한 번")
    void followAndMutual() throws Exception {
        follow(a, bUser).andExpect(status().isNoContent());
        follow(a, bUser).andExpect(status().isNoContent());

        profile(bUser, a).andExpect(jsonPath("$.following").value(true))
                .andExpect(jsonPath("$.followsMe").value(false))
                .andExpect(jsonPath("$.followerCount").value(1));
        profile(aUser, a).andExpect(jsonPath("$.followingCount").value(1));

        follow(b, aUser);
        profile(bUser, a).andExpect(jsonPath("$.following").value(true))
                .andExpect(jsonPath("$.followsMe").value(true));

        mvc.perform(delete("/api/users/" + bUser.getId() + "/follow").header("Authorization", "Bearer " + a))
                .andExpect(status().isNoContent());
        profile(bUser, a).andExpect(jsonPath("$.following").value(false))
                .andExpect(jsonPath("$.followerCount").value(0));
    }

    @Test
    @DisplayName("나 자신·차단한 사이는 팔로우할 수 없고, 차단하면 서로의 팔로우가 끊긴다")
    void selfAndBlock() throws Exception {
        follow(a, aUser).andExpect(status().isForbidden())
                .andExpect(jsonPath("$.code").value("CANNOT_FOLLOW"));

        follow(a, bUser);
        follow(b, aUser);
        mvc.perform(put("/api/users/me/blocks/" + aUser.getId()).header("Authorization", "Bearer " + b))
                .andExpect(status().is2xxSuccessful());

        profile(bUser, a).andExpect(jsonPath("$.following").value(false))
                .andExpect(jsonPath("$.followsMe").value(false));
        follow(a, bUser).andExpect(status().isForbidden());
    }

    @Test
    @DisplayName("친구 랭킹은 나 + 내가 팔로우한 사람만, 비로그인은 401")
    void friendRanking() throws Exception {
        LocalDate today = LocalDate.now(clock);
        long id = challenge(today);
        for (String t : new String[]{a, b, c}) {
            mvc.perform(post("/api/challenges/" + id + "/participants").header("Authorization", "Bearer " + t))
                    .andExpect(status().isOk());
            mvc.perform(multipart("/api/challenges/" + id + "/verifications")
                            .file(new MockMultipartFile("file", "c.png", "image/png", photo()))
                            .header("Authorization", "Bearer " + t))
                    .andExpect(status().isCreated());
        }
        follow(a, bUser); // A 는 B 만 팔로우 (C 는 아님)

        String body = mvc.perform(get("/api/rankings/friends").header("Authorization", "Bearer " + a))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.top.length()").value(2))
                .andExpect(jsonPath("$.me.rank").value(1))
                .andReturn().getResponse().getContentAsString();
        assertThat(body).contains(bUser.getNickname()).doesNotContain(cUser.getNickname());

        mvc.perform(get("/api/rankings/friends")).andExpect(status().isUnauthorized());
    }

    // ---------- helpers ----------

    private ResultActions follow(String token, User target) throws Exception {
        return mvc.perform(post("/api/users/" + target.getId() + "/follow").header("Authorization", "Bearer " + token));
    }

    private ResultActions profile(User target, String token) throws Exception {
        return mvc.perform(get("/api/users/" + target.getId() + "/profile").header("Authorization", "Bearer " + token))
                .andExpect(status().isOk());
    }

    private long challenge(LocalDate today) throws Exception {
        String host = tokenOf(newUser());
        String body = """
                {"categoryId":1,"title":"친구 랭킹","description":"매일 인증","mode":"FREE",
                 "startDate":"%s","endDate":"%s","frequencyType":"DAILY","maxParticipants":10}
                """.formatted(today, today.plusDays(6)).replace("\n", "");
        String res = mvc.perform(post("/api/challenges").header("Authorization", "Bearer " + host)
                        .contentType(MediaType.APPLICATION_JSON).content(body))
                .andExpect(status().isCreated()).andReturn().getResponse().getContentAsString();
        Matcher m = ID.matcher(res);
        assertThat(m.find()).isTrue();
        return Long.parseLong(m.group(1));
    }

    private static byte[] photo() throws Exception {
        BufferedImage image = new BufferedImage(40, 30, BufferedImage.TYPE_INT_RGB);
        Graphics2D g = image.createGraphics();
        g.setColor(new Color(ThreadLocalRandom.current().nextInt(0xFFFFFF)));
        g.fillRect(0, 0, 40, 30);
        g.dispose();
        ByteArrayOutputStream out = new ByteArrayOutputStream();
        ImageIO.write(image, "png", out);
        return out.toByteArray();
    }

    private User newUser() {
        String suffix = UUID.randomUUID().toString().replace("-", "").substring(0, 12);
        return userRepository.saveAndFlush(User.createWithPassword(suffix + "@example.com", "unused", "f" + suffix,
                "f".repeat(52) + suffix));
    }

    private String tokenOf(User user) {
        return jwtProvider.createAccessToken(user.getId(), "USER");
    }
}
