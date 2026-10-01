package com.godlife.backend.user;

import com.godlife.backend.auth.JwtProvider;
import com.godlife.backend.auth.RefreshTokenRepository;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.boot.webmvc.test.autoconfigure.AutoConfigureMockMvc;
import org.springframework.http.MediaType;
import org.springframework.mock.web.MockMultipartFile;
import org.springframework.security.crypto.password.PasswordEncoder;
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
import java.util.UUID;
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

/** 설정: 닉네임 · 자기소개 · 프로필 사진 · 비밀번호 바꾸기 */
@SpringBootTest
@AutoConfigureMockMvc
@ActiveProfiles("test")
@Transactional
class ProfileEditApiTest {

    private static final String PASSWORD = "Passw0rd!";
    private static final Pattern IMAGE = Pattern.compile("\"profileImageUrl\":\"([^\"]+)\"");

    @Autowired MockMvc mvc;
    @Autowired UserRepository userRepository;
    @Autowired RefreshTokenRepository refreshTokenRepository;
    @Autowired PasswordEncoder passwordEncoder;
    @Autowired JwtProvider jwtProvider;

    private User me;
    private User other;
    private String token;

    @BeforeEach
    void setUp() {
        me = newUser();
        other = newUser();
        token = jwtProvider.createAccessToken(me.getId(), "USER");
    }

    @Test
    @DisplayName("닉네임과 자기소개를 고치면 내 정보와 공개 프로필에 반영되고, 남이 쓰는 닉네임·특수문자는 거절한다")
    void updateProfile() throws Exception {
        String nickname = "새이름" + me.getId();
        profile("{\"nickname\":\"" + nickname + "\",\"bio\":\"  매일 조금씩  \"}")
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.nickname").value(nickname))
                .andExpect(jsonPath("$.bio").value("매일 조금씩"));
        mvc.perform(get("/api/users/" + me.getId() + "/profile"))
                .andExpect(jsonPath("$.nickname").value(nickname))
                .andExpect(jsonPath("$.bio").value("매일 조금씩"));

        // 자기소개를 비우면 지워진다. 닉네임을 그대로 두는 것은 중복이 아니다
        profile("{\"nickname\":\"" + nickname + "\",\"bio\":\"\"}")
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.bio").doesNotExist());

        profile("{\"nickname\":\"" + other.getNickname() + "\"}").andExpect(status().isConflict())
                .andExpect(jsonPath("$.code").value("DUPLICATE_NICKNAME"));
        profile("{\"nickname\":\"이름!\"}").andExpect(status().isBadRequest());
        profile("{\"nickname\":\"가\"}").andExpect(status().isBadRequest());
        profile("{\"nickname\":\"" + nickname + "\",\"bio\":\"" + "가".repeat(201) + "\"}")
                .andExpect(status().isBadRequest());
        mvc.perform(put("/api/users/me/profile").contentType(MediaType.APPLICATION_JSON)
                .content("{\"nickname\":\"비로그인\"}")).andExpect(status().isUnauthorized());
    }

    @Test
    @DisplayName("프로필 사진을 올리면 누구나 볼 수 있는 주소가 생기고, 바꾸거나 빼면 예전 사진은 사라진다")
    void profileImage() throws Exception {
        String first = imageUrl(upload().andExpect(status().isOk()));
        assertThat(first).startsWith("/api/profile-images/" + me.getId() + "/");
        mvc.perform(get(first)).andExpect(status().isOk());
        mvc.perform(get("/api/users/" + me.getId() + "/profile"))
                .andExpect(jsonPath("$.profileImageUrl").value(first));

        String second = imageUrl(upload().andExpect(status().isOk()));
        assertThat(second).isNotEqualTo(first);
        mvc.perform(get(first)).andExpect(status().isNotFound());
        mvc.perform(get(second)).andExpect(status().isOk());

        mvc.perform(delete("/api/users/me/profile-image").header("Authorization", "Bearer " + token))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.profileImageUrl").doesNotExist());
        mvc.perform(get(second)).andExpect(status().isNotFound());

        // 사진이 아닌 파일은 거절, 이상한 주소는 404
        mvc.perform(multipart("/api/users/me/profile-image")
                        .file(new MockMultipartFile("file", "a.jpg", "image/jpeg", "not an image".getBytes()))
                        .header("Authorization", "Bearer " + token))
                .andExpect(status().isBadRequest());
        mvc.perform(get("/api/profile-images/" + me.getId() + "/nope.jpg")).andExpect(status().isNotFound());
    }

    @Test
    @DisplayName("비밀번호를 바꾸면 새 비밀번호로만 로그인되고 모든 기기에서 로그아웃된다")
    void changePassword() throws Exception {
        login(PASSWORD).andExpect(status().isOk());
        assertThat(activeRefreshTokens()).isEqualTo(1);
        mvc.perform(get("/api/users/me/account").header("Authorization", "Bearer " + token))
                .andExpect(jsonPath("$.email").value(me.getEmail()))
                .andExpect(jsonPath("$.hasPassword").value(true))
                .andExpect(jsonPath("$.socialProviders.length()").value(0));

        password("Wrong123!", "NewPassw0rd!").andExpect(status().isBadRequest());
        password(PASSWORD, PASSWORD).andExpect(status().isBadRequest());
        password(PASSWORD, "short1").andExpect(status().isBadRequest());
        login(PASSWORD).andExpect(status().isOk());

        password(PASSWORD, "NewPassw0rd!").andExpect(status().isNoContent());
        assertThat(activeRefreshTokens()).isZero();
        login(PASSWORD).andExpect(status().isUnauthorized());
        login("NewPassw0rd!").andExpect(status().isOk());
    }

    @Test
    @DisplayName("소셜로만 가입한 계정은 비밀번호가 없어 바꿀 수 없다")
    void socialAccountHasNoPassword() throws Exception {
        String suffix = UUID.randomUUID().toString().replace("-", "").substring(0, 12);
        User social = userRepository.saveAndFlush(User.createSocial(suffix + "@example.com", "s" + suffix));
        String socialToken = jwtProvider.createAccessToken(social.getId(), "USER");

        mvc.perform(get("/api/users/me/account").header("Authorization", "Bearer " + socialToken))
                .andExpect(jsonPath("$.hasPassword").value(false));
        mvc.perform(put("/api/users/me/password").header("Authorization", "Bearer " + socialToken)
                        .contentType(MediaType.APPLICATION_JSON)
                        .content("{\"currentPassword\":\"anything1\",\"newPassword\":\"NewPassw0rd!\"}"))
                .andExpect(status().isBadRequest());
    }

    // ---------- helpers ----------

    private ResultActions profile(String json) throws Exception {
        return mvc.perform(put("/api/users/me/profile").header("Authorization", "Bearer " + token)
                .contentType(MediaType.APPLICATION_JSON).content(json));
    }

    private ResultActions password(String current, String next) throws Exception {
        return mvc.perform(put("/api/users/me/password").header("Authorization", "Bearer " + token)
                .contentType(MediaType.APPLICATION_JSON)
                .content("{\"currentPassword\":\"" + current + "\",\"newPassword\":\"" + next + "\"}"));
    }

    private ResultActions login(String password) throws Exception {
        return mvc.perform(post("/api/auth/login").contentType(MediaType.APPLICATION_JSON)
                .content("{\"email\":\"" + me.getEmail() + "\",\"password\":\"" + password + "\"}"));
    }

    private ResultActions upload() throws Exception {
        return mvc.perform(multipart("/api/users/me/profile-image")
                .file(new MockMultipartFile("file", "me.png", "image/png", photo()))
                .header("Authorization", "Bearer " + token));
    }

    private static String imageUrl(ResultActions result) throws Exception {
        Matcher m = IMAGE.matcher(result.andReturn().getResponse().getContentAsString());
        assertThat(m.find()).isTrue();
        return m.group(1);
    }

    private long activeRefreshTokens() {
        return refreshTokenRepository.findAll().stream()
                .filter(t -> t.getUserId().equals(me.getId()) && t.getRevokedAt() == null)
                .count();
    }

    private static byte[] photo() throws IOException {
        BufferedImage image = new BufferedImage(900, 600, BufferedImage.TYPE_INT_RGB);
        Graphics2D g = image.createGraphics();
        g.setColor(new Color(0x6FA15A));
        g.fillRect(0, 0, 900, 600);
        g.dispose();
        ByteArrayOutputStream out = new ByteArrayOutputStream();
        ImageIO.write(image, "png", out);
        return out.toByteArray();
    }

    private User newUser() {
        String suffix = UUID.randomUUID().toString().replace("-", "").substring(0, 12);
        return userRepository.saveAndFlush(User.createWithPassword(suffix + "@example.com",
                passwordEncoder.encode(PASSWORD), "p" + suffix, "p".repeat(52) + suffix));
    }
}
