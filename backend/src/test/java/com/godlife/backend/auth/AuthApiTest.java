package com.godlife.backend.auth;

import com.godlife.backend.user.UserRepository;
import jakarta.servlet.http.Cookie;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.boot.webmvc.test.autoconfigure.AutoConfigureMockMvc;
import org.springframework.http.MediaType;
import org.springframework.test.context.ActiveProfiles;
import org.springframework.test.web.servlet.MockMvc;
import org.springframework.test.web.servlet.MvcResult;
import org.springframework.transaction.annotation.Transactional;

import java.util.regex.Matcher;
import java.util.regex.Pattern;

import static org.assertj.core.api.Assertions.assertThat;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.post;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.jsonPath;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

/**
 * 회원가입/로그인/토큰 회전/로그아웃 통합 테스트. godlife_test DB 를 쓰고, 각 테스트는 롤백된다.
 */
@SpringBootTest
@AutoConfigureMockMvc
@ActiveProfiles("test")
@Transactional
class AuthApiTest {

    private static final String EMAIL = "tester@example.com";
    private static final String PASSWORD = "Passw0rd!";
    private static final Pattern ACCESS = Pattern.compile("\"accessToken\":\"([^\"]+)\"");

    @Autowired MockMvc mvc;
    @Autowired UserRepository userRepository;
    @Autowired RefreshTokenRepository refreshTokenRepository;

    // ---------- 회원가입 ----------

    @Test
    @DisplayName("회원가입 성공: 201, 비밀번호는 응답/DB 어디에도 평문으로 남지 않는다")
    void signupSuccess() throws Exception {
        signup(EMAIL, PASSWORD, "테스터")
                .andExpect(status().isCreated())
                .andExpect(jsonPath("$.email").value(EMAIL))
                .andExpect(jsonPath("$.role").value("USER"))
                .andExpect(jsonPath("$.createdAt").isNotEmpty())
                .andExpect(jsonPath("$.password").doesNotExist())
                .andExpect(jsonPath("$.passwordHash").doesNotExist());

        var saved = userRepository.findByEmail(EMAIL).orElseThrow();
        assertThat(saved.getPasswordHash()).startsWith("$2").isNotEqualTo(PASSWORD);
        assertThat(saved.getTierId()).isEqualTo(1);
    }

    @Test
    @DisplayName("이메일은 대소문자를 무시하고 중복을 막는다")
    void duplicateEmail() throws Exception {
        signup(EMAIL, PASSWORD, "테스터").andExpect(status().isCreated());
        signup("TESTER@Example.COM", PASSWORD, "다른닉").andExpect(status().isConflict())
                .andExpect(jsonPath("$.code").value("DUPLICATE_EMAIL"));
    }

    @Test
    @DisplayName("닉네임 중복은 409")
    void duplicateNickname() throws Exception {
        signup(EMAIL, PASSWORD, "테스터").andExpect(status().isCreated());
        signup("other@example.com", PASSWORD, "테스터").andExpect(status().isConflict())
                .andExpect(jsonPath("$.code").value("DUPLICATE_NICKNAME"));
    }

    @Test
    @DisplayName("약한 비밀번호/잘못된 이메일/닉네임은 400 과 필드별 메시지")
    void signupValidation() throws Exception {
        signup("not-an-email", "short", "a")
                .andExpect(status().isBadRequest())
                .andExpect(jsonPath("$.code").value("VALIDATION_ERROR"))
                .andExpect(jsonPath("$.fieldErrors.email").exists())
                .andExpect(jsonPath("$.fieldErrors.password").exists())
                .andExpect(jsonPath("$.fieldErrors.nickname").exists());
        signup(EMAIL, "onlyletters", "테스터").andExpect(status().isBadRequest())
                .andExpect(jsonPath("$.fieldErrors.password").exists());
        signup(EMAIL, "한글비밀번호1234", "테스터").andExpect(status().isBadRequest());
    }

    // ---------- 로그인 ----------

    @Test
    @DisplayName("로그인 성공: 본문에는 액세스 토큰, 리프레시 토큰은 HttpOnly/Strict 쿠키로만")
    void loginSuccess() throws Exception {
        signup(EMAIL, PASSWORD, "테스터").andExpect(status().isCreated());

        MvcResult result = login(EMAIL, PASSWORD)
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.tokenType").value("Bearer"))
                .andExpect(jsonPath("$.accessToken").isNotEmpty())
                .andExpect(jsonPath("$.refreshToken").doesNotExist())
                .andReturn();

        String setCookie = result.getResponse().getHeader("Set-Cookie");
        assertThat(setCookie).contains("refresh_token=", "HttpOnly", "SameSite=Strict", "Path=/api/auth");

        // DB 에는 원문이 아니라 SHA-256 해시(64자 hex)만 저장된다.
        String raw = result.getResponse().getCookie(AuthController.REFRESH_COOKIE).getValue();
        assertThat(refreshTokenRepository.findAll()).hasSize(1)
                .allSatisfy(t -> assertThat(t.getTokenHash()).hasSize(64).isNotEqualTo(raw));
    }

    @Test
    @DisplayName("없는 이메일과 틀린 비밀번호는 같은 401 응답 (가입 여부 노출 방지)")
    void loginFailureIsUniform() throws Exception {
        signup(EMAIL, PASSWORD, "테스터").andExpect(status().isCreated());

        String wrongPw = login(EMAIL, "Wrong-pass1").andExpect(status().isUnauthorized())
                .andReturn().getResponse().getContentAsString();
        String noUser = login("nobody@example.com", PASSWORD).andExpect(status().isUnauthorized())
                .andReturn().getResponse().getContentAsString();
        assertThat(wrongPw).isEqualTo(noUser).contains("INVALID_CREDENTIALS");
    }

    // ---------- 액세스 토큰 ----------

    @Test
    @DisplayName("보호된 API: 토큰 없으면 401, 유효한 토큰이면 본인 정보, 위조 토큰은 401")
    void meRequiresValidToken() throws Exception {
        mvc.perform(get("/api/users/me")).andExpect(status().isUnauthorized())
                .andExpect(jsonPath("$.code").value("UNAUTHORIZED"));

        signup(EMAIL, PASSWORD, "테스터").andExpect(status().isCreated());
        String access = accessTokenOf(login(EMAIL, PASSWORD).andReturn());

        mvc.perform(get("/api/users/me").header("Authorization", "Bearer " + access))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.email").value(EMAIL))
                .andExpect(jsonPath("$.nickname").value("테스터"));

        mvc.perform(get("/api/users/me").header("Authorization", "Bearer " + access + "x"))
                .andExpect(status().isUnauthorized());
        mvc.perform(get("/api/users/me").header("Authorization", "Bearer garbage"))
                .andExpect(status().isUnauthorized());
    }

    @Test
    @DisplayName("일반 회원은 관리자 경로에 접근할 수 없다 (403)")
    void adminPathForbiddenForUser() throws Exception {
        signup(EMAIL, PASSWORD, "테스터").andExpect(status().isCreated());
        String access = accessTokenOf(login(EMAIL, PASSWORD).andReturn());
        mvc.perform(get("/api/admin/anything").header("Authorization", "Bearer " + access))
                .andExpect(status().isForbidden());
    }

    // ---------- 리프레시 회전 / 로그아웃 ----------

    @Test
    @DisplayName("refresh: 새 토큰이 발급되고 옛 토큰은 폐기된다")
    void refreshRotates() throws Exception {
        signup(EMAIL, PASSWORD, "테스터").andExpect(status().isCreated());
        Cookie first = refreshCookieOf(login(EMAIL, PASSWORD).andReturn());

        MvcResult refreshed = mvc.perform(post("/api/auth/refresh").cookie(first))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.accessToken").isNotEmpty())
                .andReturn();
        Cookie second = refreshCookieOf(refreshed);
        assertThat(second.getValue()).isNotEqualTo(first.getValue());

        long active = refreshTokenRepository.findAll().stream().filter(t -> !t.isRevoked()).count();
        assertThat(active).isEqualTo(1);
    }

    @Test
    @DisplayName("refresh: 쿠키가 없거나 가짜면 401")
    void refreshWithoutOrFakeCookie() throws Exception {
        mvc.perform(post("/api/auth/refresh")).andExpect(status().isUnauthorized())
                .andExpect(jsonPath("$.code").value("INVALID_REFRESH_TOKEN"));
        mvc.perform(post("/api/auth/refresh").cookie(new Cookie(AuthController.REFRESH_COOKIE, "fake")))
                .andExpect(status().isUnauthorized());
    }

    @Test
    @DisplayName("폐기된 토큰을 나중에 재사용하면 탈취로 보고 그 사용자의 모든 세션을 폐기한다")
    void reuseDetectionRevokesAllSessions() throws Exception {
        signup(EMAIL, PASSWORD, "테스터").andExpect(status().isCreated());
        Cookie deviceA = refreshCookieOf(login(EMAIL, PASSWORD).andReturn());
        Cookie deviceB = refreshCookieOf(login(EMAIL, PASSWORD).andReturn());

        // A 기기가 회전한 뒤, 옛 토큰이 유예 시간(10초)을 넘겨 다시 쓰인 상황을 만든다.
        refreshCookieOf(mvc.perform(post("/api/auth/refresh").cookie(deviceA)).andReturn());
        var used = refreshTokenRepository.findAll().stream().filter(RefreshToken::isRevoked).findFirst().orElseThrow();
        java.lang.reflect.Field f = RefreshToken.class.getDeclaredField("revokedAt");
        f.setAccessible(true);
        f.set(used, used.getRevokedAt().minusMinutes(5));
        refreshTokenRepository.saveAndFlush(used);

        mvc.perform(post("/api/auth/refresh").cookie(deviceA)).andExpect(status().isUnauthorized());

        // 아무 잘못 없는 B 기기의 토큰까지 전부 폐기되어야 한다.
        assertThat(refreshTokenRepository.findAll()).allSatisfy(t -> assertThat(t.isRevoked()).isTrue());
        mvc.perform(post("/api/auth/refresh").cookie(deviceB)).andExpect(status().isUnauthorized());
    }

    @Test
    @DisplayName("회전 직후(유예 시간 이내) 옛 토큰이 한 번 더 오면 401 이지만 다른 세션은 유지된다")
    void graceWindowKeepsOtherSessions() throws Exception {
        signup(EMAIL, PASSWORD, "테스터").andExpect(status().isCreated());
        Cookie deviceA = refreshCookieOf(login(EMAIL, PASSWORD).andReturn());
        Cookie deviceB = refreshCookieOf(login(EMAIL, PASSWORD).andReturn());

        mvc.perform(post("/api/auth/refresh").cookie(deviceA)).andExpect(status().isOk());
        mvc.perform(post("/api/auth/refresh").cookie(deviceA)).andExpect(status().isUnauthorized());

        mvc.perform(post("/api/auth/refresh").cookie(deviceB)).andExpect(status().isOk());
    }

    @Test
    @DisplayName("로그아웃: 이 기기 토큰만 폐기되고 쿠키가 지워진다. 다른 기기는 유지, 두 번 호출해도 204")
    void logoutRevokesOnlyThisDevice() throws Exception {
        signup(EMAIL, PASSWORD, "테스터").andExpect(status().isCreated());
        Cookie deviceA = refreshCookieOf(login(EMAIL, PASSWORD).andReturn());
        Cookie deviceB = refreshCookieOf(login(EMAIL, PASSWORD).andReturn());

        MvcResult out = mvc.perform(post("/api/auth/logout").cookie(deviceA)).andExpect(status().isNoContent()).andReturn();
        assertThat(out.getResponse().getHeader("Set-Cookie")).contains("refresh_token=", "Max-Age=0");
        mvc.perform(post("/api/auth/logout").cookie(deviceA)).andExpect(status().isNoContent());
        mvc.perform(post("/api/auth/logout")).andExpect(status().isNoContent());

        mvc.perform(post("/api/auth/refresh").cookie(deviceA)).andExpect(status().isUnauthorized());
        mvc.perform(post("/api/auth/refresh").cookie(deviceB)).andExpect(status().isOk());
    }

    // ---------- helpers ----------

    private org.springframework.test.web.servlet.ResultActions signup(String email, String pw, String nick) throws Exception {
        String json = "{\"email\":\"%s\",\"password\":\"%s\",\"nickname\":\"%s\"}".formatted(email, pw, nick);
        return mvc.perform(post("/api/auth/signup").contentType(MediaType.APPLICATION_JSON).content(json));
    }

    private org.springframework.test.web.servlet.ResultActions login(String email, String pw) throws Exception {
        String json = "{\"email\":\"%s\",\"password\":\"%s\"}".formatted(email, pw);
        return mvc.perform(post("/api/auth/login").contentType(MediaType.APPLICATION_JSON).content(json));
    }

    private static String accessTokenOf(MvcResult result) throws Exception {
        Matcher m = ACCESS.matcher(result.getResponse().getContentAsString());
        assertThat(m.find()).isTrue();
        return m.group(1);
    }

    private static Cookie refreshCookieOf(MvcResult result) {
        Cookie c = result.getResponse().getCookie(AuthController.REFRESH_COOKIE);
        assertThat(c).isNotNull();
        return c;
    }
}
