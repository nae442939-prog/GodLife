package com.godlife.backend.auth.social;

import jakarta.servlet.FilterChain;
import jakarta.servlet.ServletException;
import jakarta.servlet.http.HttpServletRequest;
import jakarta.servlet.http.HttpServletResponse;
import org.springframework.web.filter.OncePerRequestFilter;

import java.io.IOException;

/**
 * 앱이 소셜 로그인을 시작할 때(/oauth2/authorization/{제공자}?app=1) 그 사실을 인가 요청용 세션에 적어 둔다.
 * 로그인이 끝나면 OAuth2LoginHandlers 가 이 표시를 보고 웹 홈 대신 앱 주소로 돌려보낸다.
 * (빈으로 등록하지 않는다: 서블릿 필터로 한 번 더 걸리지 않게 SecurityConfig 에서 직접 만든다)
 */
public class AppLoginMarkerFilter extends OncePerRequestFilter {

    static final String SESSION_KEY = "godlife.appLogin";

    @Override
    protected boolean shouldNotFilter(HttpServletRequest request) {
        return !request.getRequestURI().startsWith("/oauth2/authorization/");
    }

    @Override
    protected void doFilterInternal(HttpServletRequest request, HttpServletResponse response, FilterChain chain)
            throws ServletException, IOException {
        if ("1".equals(request.getParameter("app"))) {
            request.getSession(true).setAttribute(SESSION_KEY, Boolean.TRUE);
        } else if (request.getSession(false) != null) {
            request.getSession(false).removeAttribute(SESSION_KEY);
        }
        chain.doFilter(request, response);
    }
}
