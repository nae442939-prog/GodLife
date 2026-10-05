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
import org.springframework.transaction.annotation.Transactional;
import org.springframework.web.socket.CloseStatus;
import org.springframework.web.socket.TextMessage;
import org.springframework.web.socket.WebSocketSession;

import java.time.Clock;
import java.time.LocalDate;
import java.util.HashMap;
import java.util.UUID;
import java.util.regex.Matcher;
import java.util.regex.Pattern;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.times;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.post;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

/** 오픈채팅 실시간 알림 소켓: 참가자만 들어오고, 그 방에 메시지가 생기면 알림만 받는다 */
@SpringBootTest
@AutoConfigureMockMvc
@ActiveProfiles("test")
@Transactional
class ChatSocketTest {

    private static final Pattern ID = Pattern.compile("\"id\":(\\d+)");

    @Autowired MockMvc mvc;
    @Autowired UserRepository userRepository;
    @Autowired JwtProvider jwtProvider;
    @Autowired ChatSocketHandler handler;
    @Autowired Clock clock;

    private User host;
    private long challengeId;

    @BeforeEach
    void setUp() throws Exception {
        host = newUser();
        LocalDate today = LocalDate.now(clock);
        String body = """
                {"categoryId":1,"title":"소켓 테스트","description":"매일 인증","mode":"FREE",
                 "startDate":"%s","endDate":"%s","frequencyType":"DAILY","maxParticipants":20}
                """.formatted(today.plusDays(1), today.plusDays(30));
        String res = mvc.perform(post("/api/challenges").header("Authorization", "Bearer " + tokenOf(host))
                        .contentType(MediaType.APPLICATION_JSON).content(body.replace("\n", "")))
                .andExpect(status().isCreated()).andReturn().getResponse().getContentAsString();
        Matcher m = ID.matcher(res);
        assertThat(m.find()).isTrue();
        challengeId = Long.parseLong(m.group(1));
    }

    @Test
    @DisplayName("참가자가 토큰과 방 번호를 보내면 들어오고, 그 방에 메시지가 생기면 알림을 받는다. 나가면 더 받지 않는다")
    void memberGetsNotified() throws Exception {
        WebSocketSession session = session("s1");
        handler.handleMessage(session, join(tokenOf(host), challengeId));

        verify(session).sendMessage(new TextMessage("{\"type\":\"ready\"}"));
        verify(session, never()).close(any());
        assertThat(handler.watching(challengeId)).isEqualTo(1);

        handler.onChanged(new ChatChangedEvent(challengeId));
        verify(session).sendMessage(new TextMessage("{\"type\":\"changed\"}"));

        // 다른 방의 메시지는 알리지 않는다
        handler.onChanged(new ChatChangedEvent(challengeId + 1));
        verify(session, times(2)).sendMessage(any());

        handler.afterConnectionClosed(session, CloseStatus.NORMAL);
        assertThat(handler.watching(challengeId)).isZero();
        handler.onChanged(new ChatChangedEvent(challengeId));
        verify(session, times(2)).sendMessage(any());
    }

    @Test
    @DisplayName("참가자가 아니거나 토큰이 틀리거나 형식이 이상하면 바로 끊는다")
    void rejectsOthers() throws Exception {
        WebSocketSession stranger = session("s2");
        handler.handleMessage(stranger, join(tokenOf(newUser()), challengeId));
        verify(stranger).close(CloseStatus.POLICY_VIOLATION);

        WebSocketSession badToken = session("s3");
        handler.handleMessage(badToken, join("not-a-token", challengeId));
        verify(badToken).close(CloseStatus.POLICY_VIOLATION);

        WebSocketSession garbage = session("s4");
        handler.handleMessage(garbage, new TextMessage("hello"));
        verify(garbage).close(CloseStatus.POLICY_VIOLATION);

        assertThat(handler.watching(challengeId)).isZero();
        verify(stranger, never()).sendMessage(any());
    }

    // ---------- helpers ----------

    private static WebSocketSession session(String id) {
        WebSocketSession session = mock(WebSocketSession.class);
        when(session.getId()).thenReturn(id);
        when(session.isOpen()).thenReturn(true);
        when(session.getAttributes()).thenReturn(new HashMap<>());
        return session;
    }

    private static TextMessage join(String token, long challengeId) {
        return new TextMessage("{\"token\":\"%s\",\"challengeId\":%d}".formatted(token, challengeId));
    }

    private User newUser() {
        String suffix = UUID.randomUUID().toString().replace("-", "").substring(0, 12);
        return userRepository.saveAndFlush(User.createWithPassword(suffix + "@example.com", "unused", "k" + suffix,
                "k".repeat(52) + suffix));
    }

    private String tokenOf(User user) {
        return jwtProvider.createAccessToken(user.getId(), "USER");
    }
}
