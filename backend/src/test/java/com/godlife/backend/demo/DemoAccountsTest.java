package com.godlife.backend.demo;

import com.godlife.backend.auth.JwtProvider;
import com.godlife.backend.user.User;
import com.godlife.backend.user.UserRepository;
import jakarta.persistence.EntityManager;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.boot.webmvc.test.autoconfigure.AutoConfigureMockMvc;
import org.springframework.http.MediaType;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.test.context.ActiveProfiles;
import org.springframework.test.context.TestPropertySource;
import org.springframework.test.web.servlet.MockMvc;
import org.springframework.transaction.annotation.Transactional;

import java.time.LocalDateTime;

import static org.assertj.core.api.Assertions.assertThat;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.post;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.put;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.jsonPath;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

/** 회사에서 둘러볼 테스트 계정: 매일 새로 만들기, 막아 둔 기능, 보기만 되는 관리자 */
@SpringBootTest
@AutoConfigureMockMvc
@ActiveProfiles("test")
@TestPropertySource(properties = {
        "app.demo.user-email=demo-user@godlife.test", "app.demo.user-password=demoPass123",
        "app.demo.admin-email=demo-admin@godlife.test", "app.demo.admin-password=demoPass123"})
@Transactional
class DemoAccountsTest {

    private static final String USER_EMAIL = "demo-user@godlife.test";
    private static final String ADMIN_EMAIL = "demo-admin@godlife.test";

    @Autowired DemoAccountReset reset;
    @Autowired DemoAccounts demoAccounts;
    @Autowired UserRepository userRepository;
    @Autowired JwtProvider jwtProvider;
    @Autowired JdbcTemplate jdbc;
    @Autowired MockMvc mvc;
    @Autowired EntityManager em;

    @Test
    @DisplayName("없으면 만든다: 일반 회원은 보상 포인트 50,000P + 체험 데이터, 관리자는 ADMIN. 같은 날 다시 불러도 그대로")
    void createsOnce() throws Exception {
        reset.resetStale();
        User user = userRepository.findByEmail(USER_EMAIL).orElseThrow();
        User admin = userRepository.findByEmail(ADMIN_EMAIL).orElseThrow();

        assertThat(user.getNickname()).isEqualTo(DemoAccounts.USER_NICKNAME);
        assertThat(user.hasPhone()).isTrue();
        assertThat(admin.getRole().name()).isEqualTo("ADMIN");
        assertThat(jdbc.queryForObject("SELECT reward_balance FROM wallets WHERE user_id = ?", Long.class, user.getId()))
                .isEqualTo(DemoAccountReset.STARTING_REWARD);
        assertThat(jdbc.queryForObject("SELECT charged_balance FROM wallets WHERE user_id = ?", Long.class, user.getId()))
                .isZero();
        assertThat(jdbc.queryForObject("SELECT COUNT(*) FROM challenges WHERE host_id = ? AND title = ?", Long.class,
                user.getId(), DemoStarter.TITLE)).isEqualTo(1);

        assertThat(reset.resetStale()).isZero();
        assertThat(userRepository.findByEmail(USER_EMAIL).orElseThrow().getId()).isEqualTo(user.getId());

        // 공개한 비밀번호로 로그인된다
        mvc.perform(post("/api/auth/login").contentType(MediaType.APPLICATION_JSON)
                        .content("{\"email\":\"" + USER_EMAIL + "\",\"password\":\"demoPass123\"}"))
                .andExpect(status().isOk());
    }

    @Test
    @DisplayName("새벽 4시 전에 만든 계정은 내리고 새로 만든다. 예전 계정의 글은 지워지고 이메일 · 닉네임이 바뀐다")
    void resetsStaleAccount() {
        reset.resetStale();
        User old = userRepository.findByEmail(USER_EMAIL).orElseThrow();
        jdbc.update("INSERT INTO posts (user_id, topic, title, content) VALUES (?, 'FREE', '테스트', '아무 글')", old.getId());
        jdbc.update("UPDATE users SET created_at = ? WHERE id = ?",
                DemoAccountReset.lastResetTime(LocalDateTime.now()).minusMinutes(1), old.getId());
        em.clear(); // 위에서 읽어 둔 회원(가입 시각)을 다시 읽게

        assertThat(reset.resetStale()).isEqualTo(1);

        User fresh = userRepository.findByEmail(USER_EMAIL).orElseThrow();
        assertThat(fresh.getId()).isNotEqualTo(old.getId());
        assertThat(jdbc.queryForObject("SELECT CONCAT(status, '/', email) FROM users WHERE id = ?", String.class,
                old.getId())).isEqualTo("WITHDRAWN/withdrawn-" + old.getId() + "@withdrawn.invalid");
        assertThat(jdbc.queryForObject("SELECT COUNT(*) FROM posts WHERE user_id = ? AND status = 'VISIBLE'",
                Long.class, old.getId())).isZero();
        assertThat(demoAccounts.isDemo(old.getId())).isFalse();
        assertThat(demoAccounts.isDemo(fresh.getId())).isTrue();
    }

    @Test
    @DisplayName("비밀번호 변경 · 찾기, 휴대폰 등록, 탈퇴는 막힌다")
    void lockedFeatures() throws Exception {
        reset.resetStale();
        User user = userRepository.findByEmail(USER_EMAIL).orElseThrow();
        String token = "Bearer " + jwtProvider.createAccessToken(user.getId(), "USER");

        mvc.perform(put("/api/users/me/password").header("Authorization", token).contentType(MediaType.APPLICATION_JSON)
                        .content("{\"currentPassword\":\"demoPass123\",\"newPassword\":\"changed12345\"}"))
                .andExpect(status().isForbidden())
                .andExpect(jsonPath("$.code").value("DEMO_ACCOUNT_LOCKED"));
        mvc.perform(post("/api/account/password-reset").contentType(MediaType.APPLICATION_JSON)
                        .content("{\"email\":\"" + USER_EMAIL + "\"}"))
                .andExpect(status().isForbidden());
        mvc.perform(post("/api/users/me/phone").header("Authorization", token).contentType(MediaType.APPLICATION_JSON)
                        .content("{\"phoneProof\":\"anything\"}"))
                .andExpect(status().isForbidden());
        mvc.perform(get("/api/users/me/withdrawal").header("Authorization", token))
                .andExpect(jsonPath("$.canWithdraw").value(false));
        mvc.perform(post("/api/users/me/withdrawal").header("Authorization", token)
                        .contentType(MediaType.APPLICATION_JSON).content("{\"password\":\"demoPass123\"}"))
                .andExpect(status().isConflict());
        assertThat(userRepository.findById(user.getId()).orElseThrow().getStatus().name()).isEqualTo("ACTIVE");
    }

    @Test
    @DisplayName("테스트용 관리자는 관리자 화면을 볼 수는 있지만 처리 요청은 거절된다")
    void adminIsReadOnly() throws Exception {
        reset.resetStale();
        User admin = userRepository.findByEmail(ADMIN_EMAIL).orElseThrow();
        String token = "Bearer " + jwtProvider.createAccessToken(admin.getId(), "ADMIN");

        mvc.perform(get("/api/admin/shop/orders").header("Authorization", token)).andExpect(status().isOk());
        mvc.perform(get("/api/admin/reviews").header("Authorization", token)).andExpect(status().isOk());
        mvc.perform(post("/api/admin/reviews/1/approve").header("Authorization", token))
                .andExpect(status().isForbidden())
                .andExpect(jsonPath("$.code").value("DEMO_ADMIN_READ_ONLY"));
        mvc.perform(post("/api/admin/shop/orders/1/cancel").header("Authorization", token))
                .andExpect(status().isForbidden());
    }

    @Test
    @DisplayName("관리자 화면의 개인정보 가리기")
    void masks() {
        assertThat(DemoMask.name("김갓생")).isEqualTo("김**");
        assertThat(DemoMask.address("서울 강남구 테헤란로 1")).isEqualTo("서울 강남구 ***");
        assertThat(DemoMask.email("tester@example.com")).isEqualTo("t***@example.com");
        assertThat(DemoMask.ip("211.36.140.7")).isEqualTo("211.36.*.*");
    }

    @Test
    @DisplayName("초기화 기준 시각: 새벽 4시 전이면 전날 4시, 지나면 오늘 4시")
    void lastResetTime() {
        assertThat(DemoAccountReset.lastResetTime(LocalDateTime.of(2026, 10, 27, 3, 59)))
                .isEqualTo(LocalDateTime.of(2026, 10, 26, 4, 0));
        assertThat(DemoAccountReset.lastResetTime(LocalDateTime.of(2026, 10, 27, 4, 0)))
                .isEqualTo(LocalDateTime.of(2026, 10, 27, 4, 0));
    }
}
