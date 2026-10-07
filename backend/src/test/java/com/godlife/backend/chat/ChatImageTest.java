package com.godlife.backend.chat;

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
import java.io.ByteArrayInputStream;
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

/** 오픈채팅 사진 전송 통합 테스트. 사진 파일은 target/test-uploads 에 저장된다. */
@SpringBootTest
@AutoConfigureMockMvc
@ActiveProfiles("test")
@Transactional
class ChatImageTest {

    private static final Pattern ID = Pattern.compile("\"id\":(\\d+)");

    @Autowired MockMvc mvc;
    @Autowired UserRepository userRepository;
    @Autowired JwtProvider jwtProvider;
    @Autowired Clock clock;

    private String host;
    private User friendUser;
    private String friend;
    private String stranger;
    private long challengeId;

    @BeforeEach
    void setUp() throws Exception {
        host = tokenOf(newUser());
        friendUser = newUser();
        friend = tokenOf(friendUser);
        stranger = tokenOf(newUser());
        LocalDate today = LocalDate.now(clock);
        String body = """
                {"categoryId":1,"title":"사진방","description":"인증 사진 공유","mode":"FREE",
                 "startDate":"%s","endDate":"%s","frequencyType":"DAILY","maxParticipants":10}
                """.formatted(today.plusDays(2), today.plusDays(9)).replace("\n", "");
        challengeId = idOf(mvc.perform(post("/api/challenges").header("Authorization", "Bearer " + host)
                        .contentType(MediaType.APPLICATION_JSON).content(body))
                .andExpect(status().isCreated()).andReturn().getResponse().getContentAsString());
        mvc.perform(post("/api/challenges/" + challengeId + "/participants").header("Authorization", "Bearer " + friend))
                .andExpect(status().isOk());
    }

    @Test
    @DisplayName("사진(+글)을 보내면 참가자는 받을 수 있고, 큰 사진은 긴 쪽 1600px 로 줄어든 JPEG 로 저장된다")
    void sendAndDownload() throws Exception {
        String sent = sendImage(friend, png(2400, 1200), "a.png", "오늘 러닝 인증!")
                .andExpect(status().isCreated())
                .andExpect(jsonPath("$.hasImage").value(true))
                .andExpect(jsonPath("$.content").value("오늘 러닝 인증!"))
                .andExpect(jsonPath("$.mine").value(true))
                .andReturn().getResponse().getContentAsString();
        long messageId = idOf(sent);

        byte[] stored = image(host, messageId).andExpect(status().isOk())
                .andExpect(content().contentType(MediaType.IMAGE_JPEG))
                .andReturn().getResponse().getContentAsByteArray();
        BufferedImage read = ImageIO.read(new ByteArrayInputStream(stored));
        assertThat(read.getWidth()).isEqualTo(1600);
        assertThat(read.getHeight()).isEqualTo(800);

        // 글 없이 사진만
        sendImage(host, png(300, 300), "b.png", null).andExpect(status().isCreated())
                .andExpect(jsonPath("$.content").doesNotExist())
                .andExpect(jsonPath("$.hasImage").value(true));
    }

    @Test
    @DisplayName("사진 속 촬영 정보(EXIF)는 지워서 저장한다")
    void stripsExif() throws Exception {
        byte[] withExif = jpegWithExif();
        assertThat(new String(withExif, StandardCharsets.ISO_8859_1)).contains("Exif").contains("GPS-TEST");

        long messageId = idOf(sendImage(friend, withExif, "gps.jpg", null).andExpect(status().isCreated())
                .andReturn().getResponse().getContentAsString());
        byte[] stored = image(friend, messageId).andReturn().getResponse().getContentAsByteArray();
        String raw = new String(stored, StandardCharsets.ISO_8859_1);
        assertThat(raw).doesNotContain("Exif").doesNotContain("GPS-TEST");
    }

    @Test
    @DisplayName("사진이 아니거나(가짜 확장자 · GIF), 가로·세로가 너무 크면 거절한다")
    void rejectsBadFiles() throws Exception {
        sendImage(friend, "not an image".getBytes(StandardCharsets.UTF_8), "fake.png", null)
                .andExpect(status().isBadRequest()).andExpect(jsonPath("$.code").value("INVALID_IMAGE"));
        sendImage(friend, encode(new BufferedImage(10, 10, BufferedImage.TYPE_INT_RGB), "gif"), "a.gif", null)
                .andExpect(status().isBadRequest()).andExpect(jsonPath("$.code").value("INVALID_IMAGE"));
        sendImage(friend, png(8001, 2), "wide.png", null)
                .andExpect(status().isBadRequest()).andExpect(jsonPath("$.code").value("IMAGE_TOO_BIG"));
    }

    @Test
    @DisplayName("참가자가 아니면 보내지도 받지도 못하고, 강퇴된 사람의 사진은 가려진다")
    void onlyMembers() throws Exception {
        long messageId = idOf(sendImage(friend, png(100, 100), "a.png", null)
                .andReturn().getResponse().getContentAsString());

        sendImage(stranger, png(100, 100), "a.png", null).andExpect(status().isNotFound());
        image(stranger, messageId).andExpect(status().isNotFound());

        mvc.perform(post("/api/challenges/" + challengeId + "/participants/" + friendUser.getId() + "/kick")
                .header("Authorization", "Bearer " + host)).andExpect(status().isOk());
        image(host, messageId).andExpect(status().isNotFound());
        mvc.perform(get("/api/challenges/" + challengeId + "/messages").header("Authorization", "Bearer " + host))
                .andExpect(jsonPath("$[0].hidden").value(true))
                .andExpect(jsonPath("$[0].hasImage").value(false));
    }

    // ---------- helpers ----------

    private ResultActions sendImage(String token, byte[] bytes, String filename, String caption) throws Exception {
        var request = multipart("/api/challenges/" + challengeId + "/messages/image")
                .file(new MockMultipartFile("file", filename, "application/octet-stream", bytes));
        if (caption != null) {
            request.param("content", caption);
        }
        return mvc.perform(request.header("Authorization", "Bearer " + token));
    }

    private ResultActions image(String token, long messageId) throws Exception {
        return mvc.perform(get("/api/challenges/" + challengeId + "/messages/" + messageId + "/image")
                .header("Authorization", "Bearer " + token));
    }

    private static byte[] png(int width, int height) throws IOException {
        BufferedImage image = new BufferedImage(width, height, BufferedImage.TYPE_INT_ARGB);
        Graphics2D g = image.createGraphics();
        g.setColor(new Color(255, 107, 74, 200));
        g.fillRect(0, 0, width / 2, height);
        g.dispose();
        return encode(image, "png");
    }

    /** JPEG 맨 앞(SOI 뒤)에 GPS 흉내 문자열이 든 EXIF(APP1) 구역을 끼워 넣는다. */
    private static byte[] jpegWithExif() throws IOException {
        byte[] jpeg = encode(new BufferedImage(200, 100, BufferedImage.TYPE_INT_RGB), "jpg");
        byte[] payload = "Exif\0\0GPS-TEST 37.5665N 126.9780E".getBytes(StandardCharsets.ISO_8859_1);
        int length = payload.length + 2;
        ByteArrayOutputStream out = new ByteArrayOutputStream();
        out.write(jpeg, 0, 2); // SOI (FF D8)
        out.write(0xFF);
        out.write(0xE1); // APP1
        out.write((length >> 8) & 0xFF);
        out.write(length & 0xFF);
        out.write(payload);
        out.write(jpeg, 2, jpeg.length - 2);
        return out.toByteArray();
    }

    private static byte[] encode(BufferedImage image, String format) throws IOException {
        ByteArrayOutputStream out = new ByteArrayOutputStream();
        assertThat(ImageIO.write(image, format, out)).isTrue();
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

    private static long idOf(String body) {
        Matcher m = ID.matcher(body);
        assertThat(m.find()).isTrue();
        return Long.parseLong(m.group(1));
    }
}
