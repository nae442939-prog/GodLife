package com.godlife.backend.message;

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

import java.util.UUID;

import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.post;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.put;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.jsonPath;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

/** 1:1 메시지: 맞팔로우만 · 보낼 수 없는 이유 · 읽음 · 대화 목록 · 차단 */
@SpringBootTest
@AutoConfigureMockMvc
@ActiveProfiles("test")
@Transactional
class DirectMessageApiTest {

    @Autowired MockMvc mvc;
    @Autowired UserRepository userRepository;
    @Autowired JwtProvider jwtProvider;

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
    @DisplayName("맞팔로우가 아니면 보낼 수 없고, 이유가 단계별로 온다")
    void onlyMutual() throws Exception {
        room(a, bUser).andExpect(jsonPath("$.canSend").value(false))
                .andExpect(jsonPath("$.reason").value("NOT_FOLLOWING"));
        send(a, bUser, "안녕").andExpect(status().isForbidden())
                .andExpect(jsonPath("$.code").value("MESSAGE_NOT_ALLOWED"));

        follow(a, bUser);
        room(a, bUser).andExpect(jsonPath("$.reason").value("NOT_FOLLOWED_BACK"));
        send(a, bUser, "안녕").andExpect(status().isForbidden());

        follow(b, aUser);
        room(a, bUser).andExpect(jsonPath("$.canSend").value(true))
                .andExpect(jsonPath("$.reason").doesNotExist());
        send(a, bUser, "우리 같이 러닝 챌린지 하자!").andExpect(status().isCreated())
                .andExpect(jsonPath("$.mine").value(true));
    }

    @Test
    @DisplayName("받은 사람이 열면 읽음이 되고, 대화 목록에 마지막 메시지와 안 읽은 수가 나온다")
    void readAndConversations() throws Exception {
        mutual();
        send(a, bUser, "첫 메시지");
        send(a, bUser, "두 번째");

        mvc.perform(get("/api/messages/unread-count").header("Authorization", "Bearer " + b))
                .andExpect(jsonPath("$.count").value(2));
        mvc.perform(get("/api/messages").header("Authorization", "Bearer " + b))
                .andExpect(jsonPath("$[0].partner.id").value(aUser.getId()))
                .andExpect(jsonPath("$[0].lastContent").value("두 번째"))
                .andExpect(jsonPath("$[0].lastMine").value(false))
                .andExpect(jsonPath("$[0].unread").value(2));

        mvc.perform(get("/api/messages/" + aUser.getId()).header("Authorization", "Bearer " + b))
                .andExpect(jsonPath("$.length()").value(2))
                .andExpect(jsonPath("$[0].content").value("첫 메시지"))
                .andExpect(jsonPath("$[0].mine").value(false));
        mvc.perform(get("/api/messages/unread-count").header("Authorization", "Bearer " + b))
                .andExpect(jsonPath("$.count").value(0));
        mvc.perform(get("/api/messages/" + bUser.getId()).header("Authorization", "Bearer " + a))
                .andExpect(jsonPath("$[1].read").value(true));
    }

    @Test
    @DisplayName("차단하면 보낼 수 없고, 나에게는 보낼 수 없다")
    void blockedAndSelf() throws Exception {
        mutual();
        mvc.perform(put("/api/users/me/blocks/" + aUser.getId()).header("Authorization", "Bearer " + b))
                .andExpect(status().is2xxSuccessful());

        room(a, bUser).andExpect(jsonPath("$.reason").value("BLOCKED"));
        send(a, bUser, "안녕").andExpect(status().isForbidden());
        send(a, aUser, "나야").andExpect(status().isForbidden());
    }

    // ---------- helpers ----------

    private void mutual() throws Exception {
        follow(a, bUser);
        follow(b, aUser);
    }

    private void follow(String token, User target) throws Exception {
        mvc.perform(post("/api/users/" + target.getId() + "/follow").header("Authorization", "Bearer " + token))
                .andExpect(status().isNoContent());
    }

    private ResultActions room(String token, User partner) throws Exception {
        return mvc.perform(get("/api/messages/" + partner.getId() + "/room").header("Authorization", "Bearer " + token))
                .andExpect(status().isOk());
    }

    private ResultActions send(String token, User partner, String content) throws Exception {
        return mvc.perform(post("/api/messages/" + partner.getId()).header("Authorization", "Bearer " + token)
                .contentType(MediaType.APPLICATION_JSON)
                .content("{\"content\":\"" + content + "\"}"));
    }

    private User newUser() {
        String suffix = UUID.randomUUID().toString().replace("-", "").substring(0, 12);
        return userRepository.saveAndFlush(User.createWithPassword(suffix + "@example.com", "unused", "m" + suffix,
                "m".repeat(52) + suffix));
    }

    private String tokenOf(User user) {
        return jwtProvider.createAccessToken(user.getId(), "USER");
    }
}
