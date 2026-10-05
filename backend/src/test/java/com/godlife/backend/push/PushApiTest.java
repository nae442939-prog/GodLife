package com.godlife.backend.push;

import com.godlife.backend.auth.JwtProvider;
import com.godlife.backend.notification.Notification;
import com.godlife.backend.notification.NotificationPushEvent;
import com.godlife.backend.notification.NotificationService;
import com.godlife.backend.user.User;
import com.godlife.backend.user.UserRepository;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.boot.webmvc.test.autoconfigure.AutoConfigureMockMvc;
import org.springframework.http.MediaType;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.test.context.ActiveProfiles;
import org.springframework.test.context.bean.override.mockito.MockitoBean;
import org.springframework.test.web.servlet.MockMvc;
import org.springframework.test.web.servlet.ResultActions;
import org.springframework.transaction.annotation.Transactional;

import java.util.UUID;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.anyString;
import static org.mockito.ArgumentMatchers.contains;
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.delete;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.post;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.jsonPath;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

/** 푸시 알림: 구독 등록 · 해제, 푸시 서비스 주소 검사, 알림 내용 전송, 사라진 구독 정리 */
@SpringBootTest
@AutoConfigureMockMvc
@ActiveProfiles("test")
@Transactional
class PushApiTest {

    private static final String P256DH =
            "BNcRdreALRFXTkOOUHK1EtK2wtaz5Ry4YfYCA_0QTpQtUbVlUls0VJXg7A8u-Ts1XbjhazAkj7I99e8QcYP7DkM";
    private static final String AUTH = "tBHItJI5svbpez7KI4CCXg";

    @Autowired MockMvc mvc;
    @Autowired UserRepository userRepository;
    @Autowired JwtProvider jwtProvider;
    @Autowired PushNotifier pushNotifier;
    @Autowired NotificationService notificationService;
    @Autowired JdbcTemplate jdbc;
    @MockitoBean PushGateway gateway;

    private User user;
    private String token;

    @BeforeEach
    void setUp() {
        user = newUser();
        token = jwtProvider.createAccessToken(user.getId(), "USER");
        when(gateway.enabled()).thenReturn(true);
        when(gateway.publicKey()).thenReturn("test-public-key");
    }

    @Test
    @DisplayName("구독을 등록하면 알림 내용이 그 브라우저로 가고, 해제하면 더 가지 않는다")
    void subscribeAndDeliver() throws Exception {
        String endpoint = endpoint();
        mvc.perform(get("/api/push/config").header("Authorization", "Bearer " + token))
                .andExpect(jsonPath("$.enabled").value(true))
                .andExpect(jsonPath("$.publicKey").value("test-public-key"));

        subscribe(token, endpoint).andExpect(status().isNoContent());
        subscribe(token, endpoint).andExpect(status().isNoContent()); // 같은 브라우저는 한 줄
        assertThat(subscriptions(user)).isEqualTo(1);

        when(gateway.send(eq(endpoint), eq(P256DH), eq(AUTH), anyString())).thenReturn(201);
        int sent = pushNotifier.deliver(new NotificationPushEvent(user.getId(), "오늘 인증하는 날이에요!", "잊지 마세요",
                "/challenges/1"));
        assertThat(sent).isEqualTo(1);
        verify(gateway).send(eq(endpoint), eq(P256DH), eq(AUTH), contains("\"link\":\"/challenges/1\""));

        mvc.perform(delete("/api/push/subscriptions").header("Authorization", "Bearer " + token)
                        .contentType(MediaType.APPLICATION_JSON).content("{\"endpoint\":\"%s\"}".formatted(endpoint)))
                .andExpect(status().isNoContent());
        assertThat(subscriptions(user)).isZero();
        assertThat(pushNotifier.deliver(new NotificationPushEvent(user.getId(), "t", "b", null))).isZero();
    }

    @Test
    @DisplayName("알려진 브라우저 푸시 서비스 주소가 아니면 받지 않는다 (서버가 아무 주소로나 요청을 보내지 않게)")
    void rejectsUnknownEndpoints() throws Exception {
        for (String bad : new String[] {
                "https://evil.example.com/push/abc",
                "http://fcm.googleapis.com/fcm/send/abc",
                "https://fcm.googleapis.com.evil.com/fcm/send/abc",
                "https://localhost:8080/api/admin/devices",
                "https://user@fcm.googleapis.com/fcm/send/abc",
                "not a url"}) {
            subscribe(token, bad).andExpect(status().isBadRequest());
        }
        assertThat(subscriptions(user)).isZero();
        mvc.perform(post("/api/push/subscriptions").contentType(MediaType.APPLICATION_JSON).content("{}"))
                .andExpect(status().isUnauthorized());
    }

    @Test
    @DisplayName("푸시 서비스가 사라진 구독(410)이라고 하면 지우고, 그 밖의 실패는 남겨 둔다")
    void removesGoneSubscriptions() throws Exception {
        String gone = endpoint();
        String flaky = endpoint();
        subscribe(token, gone).andExpect(status().isNoContent());
        subscribe(token, flaky).andExpect(status().isNoContent());
        when(gateway.send(eq(gone), any(), any(), anyString())).thenReturn(410);
        when(gateway.send(eq(flaky), any(), any(), anyString())).thenReturn(-1);

        assertThat(pushNotifier.deliver(new NotificationPushEvent(user.getId(), "t", "b", "/"))).isZero();

        assertThat(jdbc.queryForList("SELECT endpoint FROM push_subscriptions WHERE user_id = ?", String.class,
                user.getId())).containsExactly(flaky);
    }

    @Test
    @DisplayName("VAPID 키가 없어 푸시가 꺼져 있으면 구독을 받지 않고, 알림함 알림은 그대로 만들어진다")
    void disabled() throws Exception {
        when(gateway.enabled()).thenReturn(false);
        subscribe(token, endpoint()).andExpect(status().isBadRequest());

        notificationService.notify(user.getId(), Notification.Type.TIER, "제목", "내용", "/me/tier",
                "push-test:" + user.getId());
        Integer made = jdbc.queryForObject("SELECT COUNT(*) FROM notifications WHERE user_id = ?", Integer.class,
                user.getId());
        assertThat(made).isEqualTo(1);
        verify(gateway, never()).send(any(), any(), any(), any());
    }

    // ---------- helpers ----------

    private ResultActions subscribe(String bearer, String endpoint) throws Exception {
        return mvc.perform(post("/api/push/subscriptions").header("Authorization", "Bearer " + bearer)
                .contentType(MediaType.APPLICATION_JSON)
                .content("{\"endpoint\":\"%s\",\"keys\":{\"p256dh\":\"%s\",\"auth\":\"%s\"}}"
                        .formatted(endpoint, P256DH, AUTH)));
    }

    private static String endpoint() {
        return "https://fcm.googleapis.com/fcm/send/" + UUID.randomUUID();
    }

    private int subscriptions(User owner) {
        Integer n = jdbc.queryForObject("SELECT COUNT(*) FROM push_subscriptions WHERE user_id = ?", Integer.class,
                owner.getId());
        return n == null ? 0 : n;
    }

    private User newUser() {
        String suffix = UUID.randomUUID().toString().replace("-", "").substring(0, 12);
        return userRepository.saveAndFlush(User.createWithPassword(suffix + "@example.com", "unused", "p" + suffix,
                "p".repeat(52) + suffix));
    }
}
