package com.godlife.backend.auth.social;

import com.godlife.backend.auth.RefreshCookies;
import com.godlife.backend.auth.dto.IssuedTokens;
import com.godlife.backend.common.error.BusinessException;
import com.godlife.backend.common.error.ErrorCode;
import jakarta.servlet.http.HttpServletRequest;
import jakarta.servlet.http.HttpServletResponse;
import jakarta.servlet.http.HttpSession;
import lombok.extern.slf4j.Slf4j;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.http.HttpHeaders;
import org.springframework.security.core.Authentication;
import org.springframework.security.core.AuthenticationException;
import org.springframework.security.oauth2.client.authentication.OAuth2AuthenticationToken;
import org.springframework.security.web.authentication.AuthenticationFailureHandler;
import org.springframework.security.web.authentication.AuthenticationSuccessHandler;
import org.springframework.stereotype.Component;

import java.io.IOException;

/**
 * 제공자 인증이 끝난 뒤 처리. 성공하면 이메일 로그인과 같은 리프레시 쿠키를 심고 프론트 홈으로 보낸다.
 * 토큰을 URL 에 싣지 않는다: 프론트는 홈에서 평소처럼 /api/auth/refresh 로 액세스 토큰을 받는다.
 * 안드로이드 앱에서 시작한 로그인(?app=1)은 쿠키 대신 1회용 코드를 앱 주소로 넘긴다 (AppLoginCodes).
 */
@Slf4j
@Component
public class OAuth2LoginHandlers implements AuthenticationSuccessHandler, AuthenticationFailureHandler {

    private final SocialLoginService socialLoginService;
    private final RefreshCookies refreshCookies;
    private final AppLoginCodes appLoginCodes;
    private final String frontendUrl;

    /** 앱이 받는 주소 (AndroidManifest 의 intent-filter 와 같아야 한다) */
    static final String APP_REDIRECT = "godlife://login";

    public OAuth2LoginHandlers(SocialLoginService socialLoginService, RefreshCookies refreshCookies,
                               AppLoginCodes appLoginCodes, @Value("${app.frontend-url}") String frontendUrl) {
        this.socialLoginService = socialLoginService;
        this.refreshCookies = refreshCookies;
        this.appLoginCodes = appLoginCodes;
        this.frontendUrl = frontendUrl;
    }

    @Override
    public void onAuthenticationSuccess(HttpServletRequest request, HttpServletResponse response,
                                        Authentication authentication) throws IOException {
        boolean app = endHandshakeSession(request);
        OAuth2AuthenticationToken token = (OAuth2AuthenticationToken) authentication;
        try {
            SocialProfile profile = SocialProfile.of(
                    token.getAuthorizedClientRegistrationId(), token.getPrincipal().getAttributes());
            IssuedTokens tokens = socialLoginService.login(profile);
            if (app) {
                response.sendRedirect(APP_REDIRECT + "?code=" + appLoginCodes.issue(tokens));
                return;
            }
            response.addHeader(HttpHeaders.SET_COOKIE, refreshCookies.issue(tokens).toString());
            response.sendRedirect(frontendUrl + "/");
        } catch (BusinessException e) {
            redirectToLogin(response, e.getErrorCode(), app);
        } catch (RuntimeException e) {
            log.warn("소셜 로그인 처리 실패: provider={}", token.getAuthorizedClientRegistrationId(), e);
            redirectToLogin(response, ErrorCode.SOCIAL_LOGIN_FAILED, app);
        }
    }

    /** 사용자가 동의 화면에서 취소했거나, 코드 교환/사용자 정보 조회가 실패한 경우. */
    @Override
    public void onAuthenticationFailure(HttpServletRequest request, HttpServletResponse response,
                                        AuthenticationException exception) throws IOException {
        boolean app = endHandshakeSession(request);
        log.info("소셜 로그인 실패: {}", exception.getMessage());
        redirectToLogin(response, ErrorCode.SOCIAL_LOGIN_FAILED, app);
    }

    private void redirectToLogin(HttpServletResponse response, ErrorCode code, boolean app) throws IOException {
        response.sendRedirect((app ? APP_REDIRECT : frontendUrl + "/login") + "?error=" + code.name());
    }

    /**
     * 인가 요청(state) 보관용으로만 잠깐 쓴 세션을 정리한다. 로그인 상태는 세션이 아니라 토큰으로 유지한다.
     * @return 앱에서 시작한 로그인이었는지 (AppLoginMarkerFilter 가 남긴 표시)
     */
    private static boolean endHandshakeSession(HttpServletRequest request) {
        HttpSession session = request.getSession(false);
        if (session == null) {
            return false;
        }
        boolean app = Boolean.TRUE.equals(session.getAttribute(AppLoginMarkerFilter.SESSION_KEY));
        session.invalidate();
        return app;
    }
}
