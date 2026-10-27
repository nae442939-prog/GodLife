package com.godlife.backend.demo;

import com.godlife.backend.auth.AuthUser;
import com.godlife.backend.common.error.BusinessException;
import com.godlife.backend.common.error.ErrorCode;
import jakarta.servlet.http.HttpServletRequest;
import jakarta.servlet.http.HttpServletResponse;
import lombok.RequiredArgsConstructor;
import org.springframework.context.annotation.Configuration;
import org.springframework.http.HttpMethod;
import org.springframework.security.core.Authentication;
import org.springframework.security.core.context.SecurityContextHolder;
import org.springframework.web.servlet.HandlerInterceptor;
import org.springframework.web.servlet.config.annotation.InterceptorRegistry;
import org.springframework.web.servlet.config.annotation.WebMvcConfigurer;

/**
 * 테스트용 관리자 계정은 관리자 화면을 보기만 한다: /api/admin/** 에 조회(GET) 말고 다른 요청이 오면 거절한다.
 * (승인 · 거절 · 배송 처리 같은 버튼을 눌러도 실제 회원의 인증 · 주문이 바뀌지 않게)
 * 관리자 화면에 나오는 실제 회원의 개인정보는 각 컨트롤러에서 가린다 (DemoAccounts.isDemoAdmin).
 */
@Configuration
@RequiredArgsConstructor
public class DemoAdminReadOnly implements WebMvcConfigurer, HandlerInterceptor {

    private final DemoAccounts demoAccounts;

    @Override
    public void addInterceptors(InterceptorRegistry registry) {
        registry.addInterceptor(this).addPathPatterns("/api/admin/**");
    }

    @Override
    public boolean preHandle(HttpServletRequest request, HttpServletResponse response, Object handler) {
        if (HttpMethod.GET.matches(request.getMethod()) || HttpMethod.HEAD.matches(request.getMethod())) {
            return true;
        }
        Authentication auth = SecurityContextHolder.getContext().getAuthentication();
        if (auth != null && auth.getPrincipal() instanceof AuthUser user && demoAccounts.isDemoAdmin(user.id())) {
            throw new BusinessException(ErrorCode.DEMO_ADMIN_READ_ONLY);
        }
        return true;
    }
}
