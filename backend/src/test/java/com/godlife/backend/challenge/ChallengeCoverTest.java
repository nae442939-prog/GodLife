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
import java.nio.charset.StandardCharsets;
import java.time.Clock;
import java.time.LocalDate;
import java.util.UUID;
import java.util.regex.Matcher;
import java.util.regex.Pattern;

import static org.assertj.core.api.Assertions.assertThat;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.multipart;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.post;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.content;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.jsonPath;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

/** 챌린지 대표 사진 통합 테스트. 사진 파일은 target/test-uploads 에 저장된다. */
@SpringBootTest
@AutoConfigureMockMvc
@ActiveProfiles("test")
@Transactional
class ChallengeCoverTest {

    private static final Pattern ID = Pattern.compile("\"id\":(\\d+)");
    private static final Pattern COVER = Pattern.compile("\"coverUrl\":\"([^\"]+)\"");

    @Autowired MockMvc mvc;
    @Autowired UserRepository userRepository;
    @Autowired JwtProvider jwtProvider;
    @Autowired Clock clock;

    private String host;
    private String stranger;
    private long challengeId;

    @BeforeEach
    void setUp() throws Exception {
        host = tokenOf(newUser());
        stranger = tokenOf(newUser());
        LocalDate today = LocalDate.now(clock);
        String body = """
                {"categoryId":1,"title":"대표 사진방","description":"사진이 있는 챌린지","mode":"FREE",
                 "startDate":"%s","endDate":"%s","frequencyType":"DAILY","maxParticipants":10}
                """.formatted(today.plusDays(2), today.plusDays(9)).replace("\n", "");
        String created = mvc.perform(post("/api/challenges").header("Authorization", "Bearer " + host)
                        .contentType(MediaType.APPLICATION_JSON).content(body))
                .andExpect(status().isCreated())
                .andExpect(jsonPath("$.coverUrl").doesNotExist())
                .andReturn().getResponse().getContentAsString(StandardCharsets.UTF_8);
        Matcher m = ID.matcher(created);
        assertThat(m.find()).isTrue();
        challengeId = Long.parseLong(m.group(1));
    }

    @Test
    @DisplayName("개설자가 대표 사진을 올리면 상세 · 목록에 주소가 나오고, 비로그인도 사진을 받을 수 있다")
    void uploadAndShow() throws Exception {
        String url = coverUrlOf(upload(host, png(400, 300)).andExpect(status().isOk()));
        assertThat(url).startsWith("/api/challenge-covers/" + challengeId + "/");

        mvc.perform(get(url)).andExpect(status().isOk()).andExpect(content().contentType(MediaType.IMAGE_JPEG));
        mvc.perform(get("/api/challenges").param("q", "대표 사진방"))
                .andExpect(jsonPath("$.items[0].coverUrl").value(url));
    }

    @Test
    @DisplayName("사진을 바꾸면 주소가 바뀌고 예전 사진은 지워진다")
    void replace() throws Exception {
        String first = coverUrlOf(upload(host, png(400, 300)));
        String second = coverUrlOf(upload(host, png(300, 300)));

        assertThat(second).isNotEqualTo(first);
        mvc.perform(get(first)).andExpect(status().isNotFound());
        mvc.perform(get(second)).andExpect(status().isOk());
    }

    @Test
    @DisplayName("개설자가 아니면 올릴 수 없고, 사진이 아닌 파일 · 이상한 주소는 거절한다")
    void rejects() throws Exception {
        upload(stranger, png(400, 300)).andExpect(status().isForbidden());
        upload(host, "사진 아님".getBytes(StandardCharsets.UTF_8))
                .andExpect(status().isBadRequest()).andExpect(jsonPath("$.code").value("INVALID_IMAGE"));

        mvc.perform(get("/api/challenge-covers/" + challengeId + "/nothing.jpg")).andExpect(status().isNotFound());
        mvc.perform(get("/api/challenge-covers/other/a.jpg")).andExpect(status().isNotFound());
    }

    private ResultActions upload(String token, byte[] bytes) throws Exception {
        return mvc.perform(multipart("/api/challenges/" + challengeId + "/cover")
                .file(new MockMultipartFile("file", "cover.png", "application/octet-stream", bytes))
                .header("Authorization", "Bearer " + token));
    }

    private static String coverUrlOf(ResultActions result) throws Exception {
        Matcher m = COVER.matcher(result.andReturn().getResponse().getContentAsString(StandardCharsets.UTF_8));
        assertThat(m.find()).isTrue();
        return m.group(1);
    }

    private static byte[] png(int width, int height) throws IOException {
        BufferedImage image = new BufferedImage(width, height, BufferedImage.TYPE_INT_ARGB);
        Graphics2D g = image.createGraphics();
        g.setColor(new Color(255, 107, 74, 200));
        g.fillRect(0, 0, width / 2, height);
        g.dispose();
        ByteArrayOutputStream out = new ByteArrayOutputStream();
        assertThat(ImageIO.write(image, "png", out)).isTrue();
        return out.toByteArray();
    }

    private User newUser() {
        String suffix = UUID.randomUUID().toString().replace("-", "").substring(0, 12);
        return userRepository.saveAndFlush(User.createWithPassword(suffix + "@example.com", "unused", "i" + suffix,
                "i".repeat(52) + suffix));
    }

    private String tokenOf(User user) {
        return jwtProvider.createAccessToken(user.getId(), "USER");
    }
}
