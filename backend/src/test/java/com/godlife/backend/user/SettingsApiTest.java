package com.godlife.backend.user;

import com.godlife.backend.auth.JwtProvider;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.boot.webmvc.test.autoconfigure.AutoConfigureMockMvc;
import org.springframework.http.MediaType;
import org.springframework.security.crypto.password.PasswordEncoder;
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
import static org.hamcrest.Matchers.hasItem;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.post;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.jsonPath;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

/** 설정: 고객센터 1:1 문의 · 회원 탈퇴 */
@SpringBootTest
@AutoConfigureMockMvc
@ActiveProfiles("test")
@Transactional
class SettingsApiTest {

    private static final String PASSWORD = "Passw0rd!";
    private static final Pattern ID = Pattern.compile("\"id\":(\\d+)");

    @Autowired MockMvc mvc;
    @Autowired UserRepository userRepository;
    @Autowired PasswordEncoder passwordEncoder;
    @Autowired JwtProvider jwtProvider;
    @Autowired Clock clock;

    private User me;
    private String token;
    private String other;

    @BeforeEach
    void setUp() {
        me = newUser();
        token = jwtProvider.createAccessToken(me.getId(), "USER");
        other = jwtProvider.createAccessToken(newUser().getId(), "USER");
    }

    @Test
    @DisplayName("1:1 문의를 남기면 내 문의 내역에 대기 중으로 보이고, 관리자가 답하면 답변 완료가 된다. 남의 문의는 안 보인다")
    void inquiry() throws Exception {
        String res = inquire(token, "{\"category\":\"POINT\",\"title\":\"환불이 궁금해요\",\"content\":\"충전 포인트 환불은 언제 되나요?\"}")
                .andExpect(status().isCreated()).andReturn().getResponse().getContentAsString();
        Matcher m = ID.matcher(res);
        assertThat(m.find()).isTrue();
        String id = m.group(1);

        mvc.perform(get("/api/inquiries").header("Authorization", "Bearer " + token))
                .andExpect(jsonPath("$.length()").value(1))
                .andExpect(jsonPath("$[0].title").value("환불이 궁금해요"))
                .andExpect(jsonPath("$[0].status").value("WAITING"))
                .andExpect(jsonPath("$[0].answer").doesNotExist());
        mvc.perform(get("/api/inquiries").header("Authorization", "Bearer " + other))
                .andExpect(jsonPath("$.length()").value(0));

        // 답변은 관리자만
        String answer = "{\"answer\":\"결제 취소로 바로 환불돼요.\"}";
        mvc.perform(post("/api/admin/inquiries/" + id + "/answer").header("Authorization", "Bearer " + token)
                .contentType(MediaType.APPLICATION_JSON).content(answer)).andExpect(status().isForbidden());
        String admin = jwtProvider.createAccessToken(me.getId(), "ADMIN");
        mvc.perform(get("/api/admin/inquiries").param("status", "WAITING").header("Authorization", "Bearer " + admin))
                .andExpect(jsonPath("$[?(@.inquiry.id == " + id + ")].nickname").value(hasItem(me.getNickname())));
        mvc.perform(get("/api/admin/inquiries").header("Authorization", "Bearer " + token))
                .andExpect(status().isForbidden());
        mvc.perform(post("/api/admin/inquiries/" + id + "/answer").header("Authorization", "Bearer " + admin)
                .contentType(MediaType.APPLICATION_JSON).content(answer)).andExpect(status().isNoContent());
        mvc.perform(get("/api/inquiries").header("Authorization", "Bearer " + token))
                .andExpect(jsonPath("$[0].status").value("ANSWERED"))
                .andExpect(jsonPath("$[0].answer").value("결제 취소로 바로 환불돼요."));

        inquire(token, "{\"category\":\"NOPE\",\"title\":\"제목\",\"content\":\"내용\"}").andExpect(status().isBadRequest());
        inquire(token, "{\"category\":\"ETC\",\"title\":\"\",\"content\":\"내용\"}").andExpect(status().isBadRequest());
        mvc.perform(get("/api/inquiries")).andExpect(status().isUnauthorized());
    }

    @Test
    @DisplayName("탈퇴: 비밀번호를 확인하고, 탈퇴하면 로그인할 수 없고 프로필도 사라진다")
    void withdraw() throws Exception {
        mvc.perform(get("/api/users/me/withdrawal").header("Authorization", "Bearer " + token))
                .andExpect(jsonPath("$.canWithdraw").value(true))
                .andExpect(jsonPath("$.blockers.length()").value(0))
                .andExpect(jsonPath("$.hasPassword").value(true));

        withdrawWith("{\"password\":\"Wrong123!\"}").andExpect(status().isBadRequest());
        login().andExpect(status().isOk());

        // 본인 확인만 먼저: 맞으면 통과하고(화면이 마지막 경고를 띄운다) 아직 탈퇴되지는 않는다
        verifyWith("{\"password\":\"Wrong123!\"}").andExpect(status().isBadRequest());
        verifyWith("{\"password\":\"" + PASSWORD + "\"}").andExpect(status().isNoContent());
        login().andExpect(status().isOk());

        withdrawWith("{\"password\":\"" + PASSWORD + "\"}").andExpect(status().isNoContent());
        login().andExpect(status().isUnauthorized());
        mvc.perform(get("/api/users/" + me.getId() + "/profile")).andExpect(status().isNotFound());
        mvc.perform(get("/api/users/me").header("Authorization", "Bearer " + token))
                .andExpect(status().isUnauthorized());
    }

    @Test
    @DisplayName("정산이 안 끝난 챌린지에 참여 중이거나 충전 포인트가 남아 있으면 탈퇴할 수 없다")
    void withdrawBlocked() throws Exception {
        LocalDate today = LocalDate.now(clock);
        String body = """
                {"categoryId":1,"title":"탈퇴 테스트","description":"매일 인증","mode":"FREE",
                 "startDate":"%s","endDate":"%s","frequencyType":"DAILY","maxParticipants":10}
                """.formatted(today, today.plusDays(6)).replace("\n", "");
        String res = mvc.perform(post("/api/challenges").header("Authorization", "Bearer " + other)
                        .contentType(MediaType.APPLICATION_JSON).content(body))
                .andExpect(status().isCreated()).andReturn().getResponse().getContentAsString();
        Matcher m = ID.matcher(res);
        assertThat(m.find()).isTrue();
        mvc.perform(post("/api/challenges/" + m.group(1) + "/participants").header("Authorization", "Bearer " + token))
                .andExpect(status().isOk());
        mvc.perform(post("/api/wallet/test-charge").header("Authorization", "Bearer " + token)
                        .contentType(MediaType.APPLICATION_JSON)
                        .content("{\"amount\":1000,\"requestKey\":\"" + UUID.randomUUID() + "\"}"))
                .andExpect(status().is2xxSuccessful());

        mvc.perform(get("/api/users/me/withdrawal").header("Authorization", "Bearer " + token))
                .andExpect(jsonPath("$.canWithdraw").value(false))
                .andExpect(jsonPath("$.blockers.length()").value(2));
        withdrawWith("{\"password\":\"" + PASSWORD + "\"}").andExpect(status().isConflict())
                .andExpect(jsonPath("$.code").value("CANNOT_WITHDRAW"));
        // 챌린지를 연 사람도 탈퇴할 수 없다
        mvc.perform(get("/api/users/me/withdrawal").header("Authorization", "Bearer " + other))
                .andExpect(jsonPath("$.canWithdraw").value(false));
        login().andExpect(status().isOk());
    }

    // ---------- helpers ----------

    private ResultActions inquire(String auth, String json) throws Exception {
        return mvc.perform(post("/api/inquiries").header("Authorization", "Bearer " + auth)
                .contentType(MediaType.APPLICATION_JSON).content(json));
    }

    private ResultActions verifyWith(String json) throws Exception {
        return mvc.perform(post("/api/users/me/withdrawal/verify").header("Authorization", "Bearer " + token)
                .contentType(MediaType.APPLICATION_JSON).content(json));
    }

    private ResultActions withdrawWith(String json) throws Exception {
        return mvc.perform(post("/api/users/me/withdrawal").header("Authorization", "Bearer " + token)
                .contentType(MediaType.APPLICATION_JSON).content(json));
    }

    private ResultActions login() throws Exception {
        return mvc.perform(post("/api/auth/login").contentType(MediaType.APPLICATION_JSON)
                .content("{\"email\":\"" + me.getEmail() + "\",\"password\":\"" + PASSWORD + "\"}"));
    }

    private User newUser() {
        String suffix = UUID.randomUUID().toString().replace("-", "").substring(0, 12);
        return userRepository.saveAndFlush(User.createWithPassword(suffix + "@example.com",
                passwordEncoder.encode(PASSWORD), "w" + suffix, "w".repeat(52) + suffix));
    }
}
