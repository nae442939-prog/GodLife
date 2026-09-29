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
import org.springframework.test.context.ActiveProfiles;
import org.springframework.test.web.servlet.MockMvc;
import org.springframework.test.web.servlet.ResultActions;
import org.springframework.transaction.annotation.Transactional;

import java.time.Clock;
import java.time.LocalDate;
import java.util.UUID;
import java.util.regex.Matcher;
import java.util.regex.Pattern;

import static org.assertj.core.api.Assertions.assertThat;
import static org.hamcrest.Matchers.hasSize;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.delete;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.post;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.jsonPath;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

/** 챌린지 오픈채팅 통합 테스트. godlife_test DB, 각 테스트는 롤백된다. */
@SpringBootTest
@AutoConfigureMockMvc
@ActiveProfiles("test")
@Transactional
class ChatApiTest {

    private static final Pattern ID = Pattern.compile("\"id\":(\\d+)");

    @Autowired MockMvc mvc;
    @Autowired UserRepository userRepository;
    @Autowired JwtProvider jwtProvider;
    @Autowired Clock clock;

    private String host;
    private String friend;
    private String stranger;
    private long challengeId;

    @BeforeEach
    void setUp() throws Exception {
        host = tokenOf(newUser());
        friend = tokenOf(newUser());
        stranger = tokenOf(newUser());
        LocalDate today = LocalDate.now(clock);
        String body = """
                {"categoryId":1,"title":"채팅방","description":"같이 해요","mode":"FREE",
                 "startDate":"%s","endDate":"%s","frequencyType":"DAILY","maxParticipants":10}
                """.formatted(today.plusDays(2), today.plusDays(9)).replace("\n", "");
        String created = mvc.perform(post("/api/challenges").header("Authorization", "Bearer " + host)
                        .contentType(MediaType.APPLICATION_JSON).content(body))
                .andExpect(status().isCreated()).andReturn().getResponse().getContentAsString();
        challengeId = idOf(created);
        mvc.perform(post("/api/challenges/" + challengeId + "/participants").header("Authorization", "Bearer " + friend))
                .andExpect(status().isOk()).andExpect(jsonPath("$.member").value(true));
    }

    @Test
    @DisplayName("개설자와 참가자는 메시지를 보내고 읽는다. 내 메시지는 mine=true")
    void sendAndRead() throws Exception {
        send(host, "다들 화이팅!").andExpect(status().isCreated())
                .andExpect(jsonPath("$.mine").value(true))
                .andExpect(jsonPath("$.content").value("다들 화이팅!"))
                .andExpect(jsonPath("$.createdAt").exists());
        send(friend, "  아침 러닝 인증은 앱 캡처로 해요  ").andExpect(jsonPath("$.content").value("아침 러닝 인증은 앱 캡처로 해요"));

        list(friend, "").andExpect(status().isOk())
                .andExpect(jsonPath("$", hasSize(2)))
                .andExpect(jsonPath("$[0].content").value("다들 화이팅!"))
                .andExpect(jsonPath("$[0].mine").value(false))
                .andExpect(jsonPath("$[1].mine").value(true));
    }

    @Test
    @DisplayName("폴링은 after 이후 새 메시지만, 이전 메시지는 before 로")
    void cursors() throws Exception {
        long first = idOf(send(host, "첫 번째").andReturn().getResponse().getContentAsString());
        long second = idOf(send(friend, "두 번째").andReturn().getResponse().getContentAsString());

        list(host, "?after=" + first).andExpect(jsonPath("$", hasSize(1)))
                .andExpect(jsonPath("$[0].content").value("두 번째"));
        list(host, "?after=" + second).andExpect(jsonPath("$", hasSize(0)));
        list(host, "?before=" + second).andExpect(jsonPath("$", hasSize(1)))
                .andExpect(jsonPath("$[0].content").value("첫 번째"));
    }

    @Test
    @DisplayName("참가자가 아니면(참여 취소 포함) 읽기·쓰기 모두 404, 비로그인은 401")
    void onlyMembers() throws Exception {
        list(stranger, "").andExpect(status().isNotFound());
        send(stranger, "안녕").andExpect(status().isNotFound());
        mvc.perform(get("/api/challenges/" + challengeId + "/messages")).andExpect(status().isUnauthorized());

        mvc.perform(delete("/api/challenges/" + challengeId + "/participants/me")
                .header("Authorization", "Bearer " + friend)).andExpect(status().isOk());
        list(friend, "").andExpect(status().isNotFound());
    }

    @Test
    @DisplayName("빈 메시지와 500자 넘는 메시지는 거절, 10초에 5개 넘게 보내면 429")
    void validationAndThrottle() throws Exception {
        send(host, "   ").andExpect(status().isBadRequest());
        send(host, "가".repeat(501)).andExpect(status().isBadRequest());
        for (int i = 0; i < 5; i++) {
            send(host, "도배 " + i).andExpect(status().isCreated());
        }
        send(host, "여섯 번째").andExpect(status().isTooManyRequests());
    }

    // ---------- helpers ----------

    private User newUser() {
        String suffix = UUID.randomUUID().toString().replace("-", "").substring(0, 12);
        return userRepository.saveAndFlush(User.createWithPassword(suffix + "@example.com", "unused", "m" + suffix,
                "m".repeat(52) + suffix));
    }

    private String tokenOf(User user) {
        return jwtProvider.createAccessToken(user.getId(), "USER");
    }

    private ResultActions send(String token, String content) throws Exception {
        String json = "{\"content\":\"" + content + "\"}";
        return mvc.perform(post("/api/challenges/" + challengeId + "/messages").header("Authorization", "Bearer " + token)
                .contentType(MediaType.APPLICATION_JSON).content(json));
    }

    private ResultActions list(String token, String query) throws Exception {
        return mvc.perform(get("/api/challenges/" + challengeId + "/messages" + query)
                .header("Authorization", "Bearer " + token));
    }

    private static long idOf(String body) {
        Matcher m = ID.matcher(body);
        assertThat(m.find()).isTrue();
        return Long.parseLong(m.group(1));
    }
}
