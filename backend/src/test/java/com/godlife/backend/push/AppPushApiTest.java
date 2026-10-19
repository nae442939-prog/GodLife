package com.godlife.backend.push;

import com.godlife.backend.auth.JwtProvider;
import com.godlife.backend.notification.NotificationPushEvent;
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
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.delete;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.post;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

/** 앱 푸시(FCM): 기기 토큰 등록 · 해제, 알림 내용 전송, 없어진 토큰 정리 */
@SpringBootTest
@AutoConfigureMockMvc
@ActiveProfiles("test")
@Transactional
class AppPushApiTest {

    @Autowired MockMvc mvc;
    @Autowired UserRepository userRepository;
    @Autowired JwtProvider jwtProvider;
    @Autowired AppPushNotifier appPushNotifier;
    @Autowired JdbcTemplate jdbc;
    @MockitoBean AppPushGateway gateway;

    private User user;
    private String bearer;

    @BeforeEach
    void setUp() {
        user = newUser();
        bearer = jwtProvider.createAccessToken(user.getId(), "USER");
        when(gateway.enabled()).thenReturn(true);
    }

    @Test
    @DisplayName("토큰을 등록하면 알림이 그 휴대폰으로 가고, 로그아웃하며 지우면 더 가지 않는다")
    void registerAndDeliver() throws Exception {
        String token = token();
        register(bearer, token).andExpect(status().isNoContent());
        register(bearer, token).andExpect(status().isNoContent()); // 같은 휴대폰은 한 줄
        assertThat(tokens(user)).isEqualTo(1);

        when(gateway.send(eq(token), any(), any(), any())).thenReturn(AppPushGateway.Result.SENT);
        int sent = appPushNotifier.deliver(new NotificationPushEvent(user.getId(), "인증 마감이 얼마 안 남았어요!",
                "오늘 자정까지", "/challenges/1"));
        assertThat(sent).isEqualTo(1);
        verify(gateway).send(token, "인증 마감이 얼마 안 남았어요!", "오늘 자정까지", "/challenges/1");

        mvc.perform(delete("/api/push/app-tokens").header("Authorization", "Bearer " + bearer)
                        .contentType(MediaType.APPLICATION_JSON).content("{\"token\":\"%s\"}".formatted(token)))
                .andExpect(status().isNoContent());
        assertThat(tokens(user)).isZero();
    }

    @Test
    @DisplayName("같은 휴대폰에 다른 회원이 로그인하면 토큰이 그 회원 것으로 바뀐다")
    void sameDeviceOtherUser() throws Exception {
        String token = token();
        User other = newUser();
        register(bearer, token).andExpect(status().isNoContent());
        register(jwtProvider.createAccessToken(other.getId(), "USER"), token).andExpect(status().isNoContent());

        assertThat(tokens(user)).isZero();
        assertThat(tokens(other)).isEqualTo(1);
    }

    @Test
    @DisplayName("FCM 이 없어진 토큰이라고 하면 지우고, 그 밖의 실패는 남겨 둔다")
    void removesGoneTokens() throws Exception {
        String gone = token();
        String flaky = token();
        register(bearer, gone).andExpect(status().isNoContent());
        register(bearer, flaky).andExpect(status().isNoContent());
        when(gateway.send(eq(gone), any(), any(), any())).thenReturn(AppPushGateway.Result.GONE);
        when(gateway.send(eq(flaky), any(), any(), any())).thenReturn(AppPushGateway.Result.FAILED);

        assertThat(appPushNotifier.deliver(new NotificationPushEvent(user.getId(), "t", "b", "/"))).isZero();

        assertThat(jdbc.queryForList("SELECT token FROM push_tokens WHERE user_id = ?", String.class, user.getId()))
                .containsExactly(flaky);
    }

    @Test
    @DisplayName("토큰 모양이 아니면 받지 않고, 로그인해야 등록할 수 있다")
    void rejectsBadTokens() throws Exception {
        String[] bad = {"short", "has space " + "a".repeat(30), "x".repeat(300), "<b>" + "a".repeat(30)};
        for (String b : bad) {
            register(bearer, b).andExpect(status().isBadRequest());
        }
        assertThat(tokens(user)).isZero();
        mvc.perform(post("/api/push/app-tokens").contentType(MediaType.APPLICATION_JSON)
                        .content("{\"token\":\"%s\"}".formatted(token())))
                .andExpect(status().isUnauthorized());
    }

    // ---------- helpers ----------

    private ResultActions register(String bearerToken, String token) throws Exception {
        return mvc.perform(post("/api/push/app-tokens").header("Authorization", "Bearer " + bearerToken)
                .contentType(MediaType.APPLICATION_JSON).content("{\"token\":\"%s\"}".formatted(token)));
    }

    /** FCM 토큰 모양 (영숫자 · 콜론 · 밑줄 · 하이픈) */
    private static String token() {
        return "f" + UUID.randomUUID().toString().replace("-", "") + ":APA91b"
                + UUID.randomUUID().toString().replace("-", "_");
    }

    private int tokens(User owner) {
        Integer n = jdbc.queryForObject("SELECT COUNT(*) FROM push_tokens WHERE user_id = ?", Integer.class,
                owner.getId());
        return n == null ? 0 : n;
    }

    private User newUser() {
        String suffix = UUID.randomUUID().toString().replace("-", "").substring(0, 12);
        return userRepository.saveAndFlush(User.createWithPassword(suffix + "@example.com", "unused", "a" + suffix,
                "a".repeat(52) + suffix));
    }
}
