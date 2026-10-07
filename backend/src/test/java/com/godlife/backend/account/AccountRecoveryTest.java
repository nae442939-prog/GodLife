package com.godlife.backend.account;

import com.godlife.backend.auth.social.SocialLoginService;
import com.godlife.backend.auth.social.SocialProfile;
import com.godlife.backend.auth.social.SocialProvider;
import com.godlife.backend.support.TestSenders;
import com.godlife.backend.support.TestSenders.PhoneProofs;
import jakarta.persistence.EntityManager;
import jakarta.servlet.http.Cookie;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.boot.webmvc.test.autoconfigure.AutoConfigureMockMvc;
import org.springframework.context.annotation.Import;
import org.springframework.http.MediaType;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.test.context.ActiveProfiles;
import org.springframework.test.web.servlet.MockMvc;
import org.springframework.test.web.servlet.ResultActions;
import org.springframework.transaction.annotation.Transactional;

import java.util.regex.Matcher;
import java.util.regex.Pattern;

import static org.assertj.core.api.Assertions.assertThat;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.post;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.jsonPath;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

/**
 * 휴대폰 인증 / 아이디 찾기 / 비밀번호 찾기 통합 테스트. 인증번호는 {@link TestSenders} 가 가로챈다.
 * godlife_test DB, 각 테스트는 롤백된다.
 */
@SpringBootTest
@AutoConfigureMockMvc
@ActiveProfiles("test")
@Import(TestSenders.class)
@Transactional
class AccountRecoveryTest {

    private static final String EMAIL = "finder@example.com";
    private static final String PASSWORD = "Passw0rd!";
    private static final Pattern JSON_STR = Pattern.compile("\"%s\":\"([^\"]+)\"");

    @Autowired MockMvc mvc;
    @Autowired TestSenders.Outbox outbox;
    @Autowired PhoneProofs phoneProofs;
    @Autowired SocialLoginService socialLoginService;
    @Autowired JdbcTemplate jdbc;
    @Autowired EntityManager em;

    // ---------- 휴대폰 인증 ----------

    @Test
    @DisplayName("인증번호 발송 → 확인하면 증표를 받는다. 하이픈 있는 번호도 같은 번호로 본다")
    void phoneSendAndConfirm() throws Exception {
        sendSms("010-1234-5678").andExpect(status().isOk()).andExpect(jsonPath("$.demoCode").doesNotExist());
        String code = outbox.lastCode("01012345678");
        assertThat(code).matches("\\d{6}");

        confirmSms("01012345678", code)
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.phoneProof").isNotEmpty());
    }

    @Test
    @DisplayName("60초 안에 다시 보내면 429")
    void resendCooldown() throws Exception {
        sendSms("01011112222").andExpect(status().isOk());
        sendSms("01011112222").andExpect(status().isTooManyRequests())
                .andExpect(jsonPath("$.code").value("TOO_MANY_REQUESTS"));
    }

    @Test
    @DisplayName("5번 틀리면 맞는 번호를 넣어도 막히고, 다시 받아야 한다")
    void attemptLimit() throws Exception {
        sendSms("01033334444");
        String code = outbox.lastCode("01033334444");
        String wrong = code.equals("000000") ? "111111" : "000000";
        for (int i = 0; i < 4; i++) {
            confirmSms("01033334444", wrong).andExpect(jsonPath("$.code").value("INVALID_VERIFICATION_CODE"));
        }
        confirmSms("01033334444", wrong).andExpect(jsonPath("$.code").value("VERIFICATION_ATTEMPTS_EXCEEDED"));
        confirmSms("01033334444", code).andExpect(status().isBadRequest())
                .andExpect(jsonPath("$.code").value("VERIFICATION_ATTEMPTS_EXCEEDED"));
    }

    @Test
    @DisplayName("만료된 인증번호는 받지 않는다")
    void expiredCode() throws Exception {
        sendSms("01055556666");
        String code = outbox.lastCode("01055556666");
        rewindPhoneSends(5);
        confirmSms("01055556666", code).andExpect(status().isBadRequest())
                .andExpect(jsonPath("$.code").value("INVALID_VERIFICATION_CODE"));
    }

    // ---------- 가입 + 번호 1계정 ----------

    @Test
    @DisplayName("증표는 한 번만 쓸 수 있고, 한 번호로는 한 계정만 가입된다")
    void proofSingleUseAndPhoneUnique() throws Exception {
        String phone = PhoneProofs.randomPhone();
        String proof = phoneProofs.proofFor(phone);
        signup(EMAIL, "찾기테스터", proof).andExpect(status().isCreated());
        signup("second@example.com", "두번째", proof).andExpect(status().isBadRequest())
                .andExpect(jsonPath("$.code").value("PHONE_VERIFICATION_REQUIRED"));

        rewindPhoneSends(2);
        signup("second@example.com", "두번째", phoneProofs.proofFor(phone)).andExpect(status().isConflict())
                .andExpect(jsonPath("$.code").value("PHONE_ALREADY_REGISTERED"));
    }

    @Test
    @DisplayName("증표 없이 가입하면 400")
    void signupRequiresPhone() throws Exception {
        signup(EMAIL, "찾기테스터", "").andExpect(status().isBadRequest())
                .andExpect(jsonPath("$.fieldErrors.phoneProof").exists());
    }

    // ---------- 소셜 가입자 번호 등록 ----------

    @Test
    @DisplayName("소셜 가입자는 번호가 없고(phoneVerified=false), 인증하면 등록된다. 남의 번호는 409")
    void socialUserRegistersPhone() throws Exception {
        String access = socialLoginService.login(
                new SocialProfile(SocialProvider.KAKAO, "k-phone", null, false, "카톡유저")).accessToken();
        mvc.perform(get("/api/users/me").header("Authorization", "Bearer " + access))
                .andExpect(jsonPath("$.phoneVerified").value(false));

        registerPhone(access, phoneProofs.proofFor("010-2345-6789"))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.phoneVerified").value(true))
                .andExpect(jsonPath("$.phone").value("010-2345-6789"));
        // 인증한 번호는 본인에게 그대로 다시 보인다
        mvc.perform(get("/api/users/me").header("Authorization", "Bearer " + access))
                .andExpect(jsonPath("$.phone").value("010-2345-6789"));

        String taken = PhoneProofs.randomPhone();
        signup(EMAIL, "먼저가입", phoneProofs.proofFor(taken)).andExpect(status().isCreated());
        rewindPhoneSends(2);
        registerPhone(access, phoneProofs.proofFor(taken)).andExpect(status().isConflict())
                .andExpect(jsonPath("$.code").value("PHONE_ALREADY_REGISTERED"));
    }

    // ---------- 아이디 찾기 ----------

    @Test
    @DisplayName("아이디 찾기: 인증한 번호의 계정 이메일을 가려서 보여준다")
    void findId() throws Exception {
        String phone = PhoneProofs.randomPhone();
        signup(EMAIL, "찾기테스터", phoneProofs.proofFor(phone)).andExpect(status().isCreated());
        rewindPhoneSends(2);

        findId(phoneProofs.proofFor(phone))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.maskedEmail").value("fi****@example.com"))
                .andExpect(jsonPath("$.hasPassword").value(true))
                .andExpect(jsonPath("$.providers").isEmpty());
    }

    @Test
    @DisplayName("아이디 찾기: 가입되지 않은 번호는 404")
    void findIdNotFound() throws Exception {
        findId(phoneProofs.newProof()).andExpect(status().isNotFound())
                .andExpect(jsonPath("$.code").value("ACCOUNT_NOT_FOUND"));
    }

    @Test
    @DisplayName("이메일 가리기 규칙")
    void maskEmail() {
        assertThat(AccountRecoveryService.maskEmail("tester@example.com")).isEqualTo("te****@example.com");
        assertThat(AccountRecoveryService.maskEmail("abc@x.com")).isEqualTo("ab***@x.com");
        assertThat(AccountRecoveryService.maskEmail("a@x.com")).isEqualTo("a***@x.com");
    }

    // ---------- 비밀번호 찾기 ----------

    @Test
    @DisplayName("비밀번호 찾기: 메일 인증번호 → 새 비밀번호. 옛 비밀번호와 기존 로그인은 모두 무효")
    void passwordResetFlow() throws Exception {
        signup(EMAIL, "찾기테스터", phoneProofs.newProof()).andExpect(status().isCreated());
        Cookie oldSession = login(EMAIL, PASSWORD).andExpect(status().isOk())
                .andReturn().getResponse().getCookie("refresh_token");

        requestReset("Finder@Example.com").andExpect(status().isOk());
        String code = outbox.lastCode(EMAIL);
        String resetToken = field(confirmReset(EMAIL, code).andExpect(status().isOk()), "resetToken");

        completeReset(resetToken, "NewPassw0rd").andExpect(status().isNoContent());

        login(EMAIL, PASSWORD).andExpect(status().isUnauthorized());
        login(EMAIL, "NewPassw0rd").andExpect(status().isOk());
        mvc.perform(post("/api/auth/refresh").cookie(oldSession)).andExpect(status().isUnauthorized());

        completeReset(resetToken, "Another1pw").andExpect(status().isBadRequest())
                .andExpect(jsonPath("$.code").value("INVALID_RESET_TOKEN"));
    }

    @Test
    @DisplayName("비밀번호 찾기: 없는 이메일/소셜 전용 계정도 200 으로 같게 응답하고, 메일은 보내지 않는다")
    void passwordResetNoEnumeration() throws Exception {
        requestReset("nobody@example.com").andExpect(status().isOk());
        assertThat(outbox.lastCode("nobody@example.com")).isNull();

        socialLoginService.login(new SocialProfile(SocialProvider.GOOGLE, "g-reset", "soc@gmail.com", true, "구글"));
        requestReset("soc@gmail.com").andExpect(status().isOk());
        assertThat(outbox.lastCode("soc@gmail.com")).isNull();

        confirmReset("nobody@example.com", "123456").andExpect(status().isBadRequest())
                .andExpect(jsonPath("$.code").value("INVALID_VERIFICATION_CODE"));
    }

    @Test
    @DisplayName("비밀번호 찾기: 인증번호 5번 틀리면 막힌다")
    void passwordResetAttemptLimit() throws Exception {
        signup(EMAIL, "찾기테스터", phoneProofs.newProof()).andExpect(status().isCreated());
        requestReset(EMAIL);
        String code = outbox.lastCode(EMAIL);
        String wrong = code.equals("000000") ? "111111" : "000000";
        for (int i = 0; i < 5; i++) {
            confirmReset(EMAIL, wrong).andExpect(status().isBadRequest());
        }
        confirmReset(EMAIL, code).andExpect(jsonPath("$.code").value("VERIFICATION_ATTEMPTS_EXCEEDED"));
    }

    // ---------- helpers ----------

    /** 발송 쿨다운/만료를 기다리는 대신, 이미 보낸 인증번호들의 만료 시각을 과거로 당긴다. */
    private void rewindPhoneSends(int minutes) {
        em.flush();
        jdbc.update("UPDATE phone_verifications SET expires_at = DATE_SUB(expires_at, INTERVAL ? MINUTE)", minutes);
        em.clear();
    }

    private ResultActions sendSms(String phone) throws Exception {
        return postJson("/api/phone-verifications", "{\"phone\":\"%s\"}".formatted(phone));
    }

    private ResultActions confirmSms(String phone, String code) throws Exception {
        return postJson("/api/phone-verifications/confirm", "{\"phone\":\"%s\",\"code\":\"%s\"}".formatted(phone, code));
    }

    private ResultActions signup(String email, String nickname, String proof) throws Exception {
        return postJson("/api/auth/signup", "{\"email\":\"%s\",\"password\":\"%s\",\"nickname\":\"%s\",\"phoneProof\":\"%s\"}"
                .formatted(email, PASSWORD, nickname, proof));
    }

    private ResultActions login(String email, String password) throws Exception {
        return postJson("/api/auth/login", "{\"email\":\"%s\",\"password\":\"%s\"}".formatted(email, password));
    }

    private ResultActions registerPhone(String access, String proof) throws Exception {
        return mvc.perform(post("/api/users/me/phone").header("Authorization", "Bearer " + access)
                .contentType(MediaType.APPLICATION_JSON).content("{\"phoneProof\":\"%s\"}".formatted(proof)));
    }

    private ResultActions findId(String proof) throws Exception {
        return postJson("/api/account/find-id", "{\"phoneProof\":\"%s\"}".formatted(proof));
    }

    private ResultActions requestReset(String email) throws Exception {
        return postJson("/api/account/password-reset", "{\"email\":\"%s\"}".formatted(email));
    }

    private ResultActions confirmReset(String email, String code) throws Exception {
        return postJson("/api/account/password-reset/confirm", "{\"email\":\"%s\",\"code\":\"%s\"}".formatted(email, code));
    }

    private ResultActions completeReset(String token, String newPassword) throws Exception {
        return postJson("/api/account/password-reset/complete",
                "{\"resetToken\":\"%s\",\"newPassword\":\"%s\"}".formatted(token, newPassword));
    }

    private ResultActions postJson(String url, String json) throws Exception {
        return mvc.perform(post(url).contentType(MediaType.APPLICATION_JSON).content(json));
    }

    private static String field(ResultActions result, String name) throws Exception {
        Matcher m = Pattern.compile(JSON_STR.pattern().formatted(name))
                .matcher(result.andReturn().getResponse().getContentAsString());
        assertThat(m.find()).isTrue();
        return m.group(1);
    }
}
