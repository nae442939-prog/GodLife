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

import java.time.Clock;
import java.time.LocalDate;
import java.util.UUID;
import java.util.regex.Matcher;
import java.util.regex.Pattern;

import static org.assertj.core.api.Assertions.assertThat;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.post;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.put;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.jsonPath;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

/** 1:1 메시지: 맞팔로우·같은 챌린지는 바로 · 메시지 요청(3개 제한 · 수락 · 거절) · 읽음 · 대화 목록 · 차단 */
@SpringBootTest
@AutoConfigureMockMvc
@ActiveProfiles("test")
@Transactional
class DirectMessageApiTest {

    private static final Pattern ID = Pattern.compile("\"id\":(\\d+)");

    @Autowired MockMvc mvc;
    @Autowired UserRepository userRepository;
    @Autowired JwtProvider jwtProvider;
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
    @DisplayName("맞팔로우면 메시지 요청 없이 바로 대화한다")
    void mutualIsDirect() throws Exception {
        mutual();
        room(a, bUser).andExpect(jsonPath("$.canSend").value(true))
                .andExpect(jsonPath("$.direct").value(true))
                .andExpect(jsonPath("$.mutual").value(true))
                .andExpect(jsonPath("$.request").doesNotExist());
        send(a, bUser, "우리 같이 러닝 챌린지 하자!").andExpect(status().isCreated())
                .andExpect(jsonPath("$.mine").value(true));
        conversations(b).andExpect(jsonPath("$[0].request").doesNotExist());
    }

    @Test
    @DisplayName("같은 챌린지 참가자는 맞팔로우가 아니어도 바로 대화한다")
    void sharedChallengeIsDirect() throws Exception {
        long id = challenge(a);
        mvc.perform(post("/api/challenges/" + id + "/participants").header("Authorization", "Bearer " + b))
                .andExpect(status().isOk());

        room(b, aUser).andExpect(jsonPath("$.direct").value(true))
                .andExpect(jsonPath("$.mutual").value(false))
                .andExpect(jsonPath("$.sharedChallenge").value(true));
        for (int i = 0; i < 4; i++) {
            send(b, aUser, "같이 해요 " + i).andExpect(status().isCreated());
        }
        conversations(a).andExpect(jsonPath("$[0].request").doesNotExist());
        unread(a).andExpect(jsonPath("$.count").value(4)).andExpect(jsonPath("$.requests").value(0));
    }

    @Test
    @DisplayName("그 밖의 사람에게는 메시지 요청으로 가고, 수락 전에는 3개까지만 보낼 수 있다")
    void requestLimit() throws Exception {
        room(a, bUser).andExpect(jsonPath("$.canSend").value(true))
                .andExpect(jsonPath("$.direct").value(false))
                .andExpect(jsonPath("$.requestLeft").value(3));

        send(a, bUser, "안녕하세요").andExpect(status().isCreated());
        room(a, bUser).andExpect(jsonPath("$.request").value("SENT"))
                .andExpect(jsonPath("$.requestLeft").value(2));
        send(a, bUser, "둘").andExpect(status().isCreated());
        send(a, bUser, "셋").andExpect(status().isCreated());
        room(a, bUser).andExpect(jsonPath("$.canSend").value(false))
                .andExpect(jsonPath("$.reason").value("REQUEST_LIMIT"));
        send(a, bUser, "넷").andExpect(status().isForbidden())
                .andExpect(jsonPath("$.code").value("MESSAGE_NOT_ALLOWED"));

        // 받은 사람: 요청으로 보이고, 안 읽은 메시지 수에는 안 들어간다
        room(b, aUser).andExpect(jsonPath("$.request").value("RECEIVED"));
        conversations(b).andExpect(jsonPath("$[0].request").value("RECEIVED"));
        conversations(a).andExpect(jsonPath("$[0].request").value("SENT"));
        unread(b).andExpect(jsonPath("$.count").value(0)).andExpect(jsonPath("$.requests").value(1));

        // 수락 전에는 열어 봐도 보낸 사람에게 읽음이 안 보인다
        list(b, aUser).andExpect(jsonPath("$.length()").value(3));
        list(a, bUser).andExpect(jsonPath("$[2].read").value(false));
    }

    @Test
    @DisplayName("요청을 수락하면 대화가 열린다")
    void accept() throws Exception {
        send(a, bUser, "안녕하세요");
        // 보낸 사람은 자기 요청을 수락할 수 없다
        respond(a, bUser, "accept").andExpect(status().isForbidden());

        respond(b, aUser, "accept").andExpect(status().isNoContent());
        room(a, bUser).andExpect(jsonPath("$.direct").value(true))
                .andExpect(jsonPath("$.request").doesNotExist());
        for (int i = 0; i < 4; i++) {
            send(a, bUser, "이제 자유롭게 " + i).andExpect(status().isCreated());
        }
        unread(b).andExpect(jsonPath("$.count").value(5)).andExpect(jsonPath("$.requests").value(0));
        list(b, aUser);
        list(a, bUser).andExpect(jsonPath("$[0].read").value(true));
    }

    @Test
    @DisplayName("요청에 답장하면 수락한 것으로 친다")
    void replyAccepts() throws Exception {
        send(a, bUser, "안녕하세요");
        send(b, aUser, "반가워요").andExpect(status().isCreated());

        room(a, bUser).andExpect(jsonPath("$.direct").value(true));
        room(b, aUser).andExpect(jsonPath("$.direct").value(true))
                .andExpect(jsonPath("$.request").doesNotExist());
        conversations(b).andExpect(jsonPath("$[0].request").doesNotExist());
    }

    @Test
    @DisplayName("거절하면 보낸 사람은 더 보낼 수 없고(거절이라고는 안 알림), 거절한 사람 목록에서 사라진다")
    void decline() throws Exception {
        send(a, bUser, "안녕하세요");
        respond(b, aUser, "decline").andExpect(status().isNoContent());

        room(a, bUser).andExpect(jsonPath("$.canSend").value(false))
                .andExpect(jsonPath("$.reason").value("NOT_AVAILABLE"));
        send(a, bUser, "저기요").andExpect(status().isForbidden())
                .andExpect(jsonPath("$.message").value("지금은 메시지를 보낼 수 없어요."));
        conversations(b).andExpect(jsonPath("$.length()").value(0));
        unread(b).andExpect(jsonPath("$.count").value(0)).andExpect(jsonPath("$.requests").value(0));

        // 거절한 사람이 먼저 말을 걸면 다시 열린다
        send(b, aUser, "생각해 보니 반가워요").andExpect(status().isCreated());
        send(a, bUser, "감사해요").andExpect(status().isCreated());
    }

    @Test
    @DisplayName("받은 사람이 열면 읽음이 되고, 대화 목록에 마지막 메시지와 안 읽은 수가 나온다")
    void readAndConversations() throws Exception {
        mutual();
        send(a, bUser, "첫 메시지");
        send(a, bUser, "두 번째");

        unread(b).andExpect(jsonPath("$.count").value(2));
        conversations(b)
                .andExpect(jsonPath("$[0].partner.id").value(aUser.getId()))
                .andExpect(jsonPath("$[0].lastContent").value("두 번째"))
                .andExpect(jsonPath("$[0].lastMine").value(false))
                .andExpect(jsonPath("$[0].unread").value(2));

        list(b, aUser)
                .andExpect(jsonPath("$.length()").value(2))
                .andExpect(jsonPath("$[0].content").value("첫 메시지"))
                .andExpect(jsonPath("$[0].mine").value(false));
        unread(b).andExpect(jsonPath("$.count").value(0));
        list(a, bUser).andExpect(jsonPath("$[1].read").value(true));
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

    private long challenge(String hostToken) throws Exception {
        LocalDate today = LocalDate.now(clock);
        String body = """
                {"categoryId":1,"title":"메시지 테스트","description":"매일 인증","mode":"FREE","visibility":"PUBLIC",
                 "startDate":"%s","endDate":"%s","frequencyType":"DAILY","maxParticipants":10}
                """.formatted(today, today.plusDays(6)).replace("\n", "");
        String res = mvc.perform(post("/api/challenges").header("Authorization", "Bearer " + hostToken)
                        .contentType(MediaType.APPLICATION_JSON).content(body))
                .andExpect(status().isCreated()).andReturn().getResponse().getContentAsString();
        Matcher m = ID.matcher(res);
        assertThat(m.find()).isTrue();
        return Long.parseLong(m.group(1));
    }

    private ResultActions conversations(String token) throws Exception {
        return mvc.perform(get("/api/messages").header("Authorization", "Bearer " + token)).andExpect(status().isOk());
    }

    private ResultActions unread(String token) throws Exception {
        return mvc.perform(get("/api/messages/unread-count").header("Authorization", "Bearer " + token))
                .andExpect(status().isOk());
    }

    private ResultActions list(String token, User partner) throws Exception {
        return mvc.perform(get("/api/messages/" + partner.getId()).header("Authorization", "Bearer " + token))
                .andExpect(status().isOk());
    }

    private ResultActions respond(String token, User partner, String action) throws Exception {
        return mvc.perform(post("/api/messages/" + partner.getId() + "/" + action)
                .header("Authorization", "Bearer " + token));
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
