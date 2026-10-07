package com.godlife.backend.auth.social;

import com.godlife.backend.auth.AuthService;
import com.godlife.backend.auth.dto.SignupRequest;
import com.godlife.backend.common.error.BusinessException;
import com.godlife.backend.common.error.ErrorCode;
import com.godlife.backend.support.TestSenders;
import com.godlife.backend.user.User;
import com.godlife.backend.user.UserRepository;
import com.godlife.backend.user.UserStatus;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.boot.webmvc.test.autoconfigure.AutoConfigureMockMvc;
import org.springframework.context.annotation.Import;
import org.springframework.mock.web.MockHttpServletRequest;
import org.springframework.mock.web.MockHttpServletResponse;
import org.springframework.security.core.authority.AuthorityUtils;
import org.springframework.security.oauth2.client.authentication.OAuth2AuthenticationToken;
import org.springframework.security.oauth2.core.user.DefaultOAuth2User;
import org.springframework.test.context.ActiveProfiles;
import org.springframework.test.util.ReflectionTestUtils;
import org.springframework.test.web.servlet.MockMvc;
import org.springframework.transaction.annotation.Transactional;

import java.util.Map;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

/**
 * 소셜 로그인 연결/가입 정책 통합 테스트. 실제 제공자에는 접속하지 않고,
 * 제공자가 돌려주는 사용자 정보(attributes)를 직접 만들어 넣는다. godlife_test DB, 각 테스트는 롤백된다.
 */
@SpringBootTest
@AutoConfigureMockMvc
@ActiveProfiles("test")
@Import(TestSenders.class)
@Transactional
class SocialLoginTest {

    @Autowired MockMvc mvc;
    @Autowired SocialLoginService socialLoginService;
    @Autowired OAuth2LoginHandlers handlers;
    @Autowired AuthService authService;
    @Autowired UserRepository userRepository;
    @Autowired SocialAccountRepository socialAccountRepository;
    @Autowired TestSenders.PhoneProofs phoneProofs;

    // ---------- 제공자 응답 파싱 ----------

    @Test
    @DisplayName("구글/카카오/네이버 응답을 같은 모양으로 읽는다")
    void parseProfiles() {
        var google = SocialProfile.of("google",
                Map.of("sub", "g-1", "email", "a@gmail.com", "email_verified", true, "name", "구글유저"));
        assertThat(google).isEqualTo(
                new SocialProfile(SocialProvider.GOOGLE, "g-1", "a@gmail.com", true, "구글유저"));

        var kakao = SocialProfile.of("kakao", Map.of("id", 12345L,
                "kakao_account", Map.of("profile", Map.of("nickname", "카카오유저"))));
        assertThat(kakao).isEqualTo(new SocialProfile(SocialProvider.KAKAO, "12345", null, false, "카카오유저"));

        var naver = SocialProfile.of("naver",
                Map.of("resultcode", "00", "response", Map.of("id", "n-1", "email", "b@naver.com", "nickname", "네이버")));
        assertThat(naver.emailVerified()).as("네이버는 인증 여부를 주지 않으므로 미검증").isFalse();
        assertThat(naver.providerUserId()).isEqualTo("n-1");
    }

    // ---------- 연결 / 가입 ----------

    @Test
    @DisplayName("처음 로그인하면 가입되고, 두 번째에는 같은 회원으로 로그인된다")
    void firstLoginSignsUpThenReuses() {
        var profile = new SocialProfile(SocialProvider.GOOGLE, "g-100", "New@Gmail.com", true, "새유저");

        socialLoginService.login(profile);
        User user = userRepository.findByEmail("new@gmail.com").orElseThrow();
        assertThat(user.getPasswordHash()).isNull();
        assertThat(user.getNickname()).isEqualTo("새유저");

        long users = userRepository.count();
        socialLoginService.login(profile);
        assertThat(userRepository.count()).isEqualTo(users);
        assertThat(socialAccountRepository.findByProviderAndProviderUserId(SocialProvider.GOOGLE, "g-100"))
                .get().extracting(SocialAccount::getUserId).isEqualTo(user.getId());
    }

    @Test
    @DisplayName("검증된 이메일이 기존 회원과 같으면 그 회원에 연결한다")
    void verifiedEmailLinksExistingUser() {
        User existing = authService.signup(new SignupRequest("me@example.com", "Passw0rd!", "기존회원", phoneProofs.newProof()));

        socialLoginService.login(new SocialProfile(SocialProvider.KAKAO, "k-1", "me@example.com", true, "카톡닉"));

        var link = socialAccountRepository.findByProviderAndProviderUserId(SocialProvider.KAKAO, "k-1").orElseThrow();
        assertThat(link.getUserId()).isEqualTo(existing.getId());
    }

    @Test
    @DisplayName("검증되지 않은 이메일은 기존 회원에 연결하지 않고 가상 이메일로 따로 가입시킨다")
    void unverifiedEmailDoesNotLink() {
        User existing = authService.signup(new SignupRequest("me@example.com", "Passw0rd!", "기존회원", phoneProofs.newProof()));

        socialLoginService.login(new SocialProfile(SocialProvider.NAVER, "n-1", "me@example.com", false, "네이버닉"));

        var link = socialAccountRepository.findByProviderAndProviderUserId(SocialProvider.NAVER, "n-1").orElseThrow();
        assertThat(link.getUserId()).isNotEqualTo(existing.getId());
        assertThat(userRepository.findById(link.getUserId()).orElseThrow().getEmail())
                .isEqualTo("naver_n-1@social.godlife.local");
    }

    @Test
    @DisplayName("이메일이 없으면 가상 이메일, 닉네임이 겹치면 뒤에 숫자를 붙이고, 특수문자/공백은 지운다")
    void nicknameAndVirtualEmail() {
        authService.signup(new SignupRequest("other@example.com", "Passw0rd!", "홍길동", phoneProofs.newProof()));

        socialLoginService.login(new SocialProfile(SocialProvider.KAKAO, "777", null, false, "홍길동"));
        User user = userRepository.findByEmail("kakao_777@social.godlife.local").orElseThrow();
        assertThat(user.getNickname()).matches("홍길동\\d{4}");

        assertThat(SocialLoginService.sanitizeNickname("🌟 별 빛 🌟")).isEqualTo("별빛");
        assertThat(SocialLoginService.sanitizeNickname("!")).isEqualTo(SocialLoginService.DEFAULT_NICKNAME);
        assertThat(SocialLoginService.sanitizeNickname("a".repeat(40))).hasSize(15);
    }

    @Test
    @DisplayName("정지된 회원은 소셜 로그인도 막는다")
    void suspendedUserBlocked() {
        var profile = new SocialProfile(SocialProvider.GOOGLE, "g-9", "s@gmail.com", true, "정지대상");
        socialLoginService.login(profile);
        User user = userRepository.findByEmail("s@gmail.com").orElseThrow();
        ReflectionTestUtils.setField(user, "status", UserStatus.SUSPENDED);
        userRepository.flush();

        assertThatThrownBy(() -> socialLoginService.login(profile))
                .isInstanceOf(BusinessException.class)
                .extracting("errorCode").isEqualTo(ErrorCode.ACCOUNT_SUSPENDED);
    }

    // ---------- 웹 흐름 ----------

    @Test
    @DisplayName("시작 URL 은 제공자 인가 화면으로 보내고, redirect_uri 는 프론트(5173) 콜백이다")
    void authorizationRedirect() throws Exception {
        var res = mvc.perform(get("/oauth2/authorization/kakao"))
                .andExpect(status().is3xxRedirection())
                .andReturn().getResponse();
        assertThat(res.getRedirectedUrl())
                .startsWith("https://kauth.kakao.com/oauth/authorize")
                .contains("redirect_uri=http://localhost:5173/login/oauth2/code/kakao");
    }

    @Test
    @DisplayName("성공 핸들러: 리프레시 쿠키를 심고 토큰 없이 프론트 홈으로 보낸다")
    void successHandlerSetsCookieAndRedirects() throws Exception {
        var principal = new DefaultOAuth2User(AuthorityUtils.createAuthorityList("OAUTH2_USER"),
                Map.of("sub", "g-200", "email", "h@gmail.com", "email_verified", true, "name", "핸들러"), "sub");
        var auth = new OAuth2AuthenticationToken(principal, principal.getAuthorities(), "google");
        var response = new MockHttpServletResponse();

        handlers.onAuthenticationSuccess(new MockHttpServletRequest(), response, auth);

        assertThat(response.getRedirectedUrl()).isEqualTo("http://localhost:5173/");
        assertThat(response.getHeader("Set-Cookie"))
                .startsWith("refresh_token=").contains("HttpOnly").contains("SameSite=Strict");
    }
}
