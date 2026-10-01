package com.godlife.backend.config;

import com.godlife.backend.auth.JwtAuthenticationFilter;
import com.godlife.backend.auth.social.OAuth2LoginHandlers;
import jakarta.servlet.http.HttpServletResponse;
import lombok.RequiredArgsConstructor;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;
import org.springframework.http.HttpMethod;
import org.springframework.http.MediaType;
import org.springframework.security.config.annotation.web.builders.HttpSecurity;
import org.springframework.security.config.annotation.web.configurers.AbstractHttpConfigurer;
import org.springframework.security.config.http.SessionCreationPolicy;
import org.springframework.security.crypto.bcrypt.BCryptPasswordEncoder;
import org.springframework.security.crypto.password.PasswordEncoder;
import org.springframework.security.web.SecurityFilterChain;
import org.springframework.security.web.authentication.UsernamePasswordAuthenticationFilter;

import java.io.IOException;
import java.nio.charset.StandardCharsets;

@Configuration
@RequiredArgsConstructor
public class SecurityConfig {

    private static final String UNAUTHORIZED_BODY =
            "{\"code\":\"UNAUTHORIZED\",\"message\":\"로그인이 필요합니다.\"}";
    private static final String FORBIDDEN_BODY =
            "{\"code\":\"FORBIDDEN\",\"message\":\"접근 권한이 없습니다.\"}";

    private final JwtAuthenticationFilter jwtAuthenticationFilter;

    @Bean
    public PasswordEncoder passwordEncoder() {
        return new BCryptPasswordEncoder();
    }

    @Bean
    public SecurityFilterChain securityFilterChain(HttpSecurity http, OAuth2LoginHandlers oauth2Handlers)
            throws Exception {
        http
                // Bearer 토큰 기반 API 라 세션/CSRF 를 쓰지 않는다. (refresh 쿠키는 SameSite=Strict + 경로 제한으로 보호)
                .csrf(AbstractHttpConfigurer::disable)
                .formLogin(AbstractHttpConfigurer::disable)
                .httpBasic(AbstractHttpConfigurer::disable)
                .logout(AbstractHttpConfigurer::disable)
                .sessionManagement(s -> s.sessionCreationPolicy(SessionCreationPolicy.STATELESS))
                // 소셜 로그인: /oauth2/authorization/{provider} 로 시작, /login/oauth2/code/{provider} 로 돌아온다.
                // 인가 요청(state)만 잠깐 세션에 보관하고, 로그인 상태는 세션이 아니라 JWT 로 유지한다.
                // loginPage 를 지정해 Spring 기본 로그인 페이지가 만들어지지 않게 한다. (로그인 화면은 프론트)
                .oauth2Login(o -> o
                        .loginPage("/login")
                        .successHandler(oauth2Handlers)
                        .failureHandler(oauth2Handlers))
                .authorizeHttpRequests(auth -> auth
                        .requestMatchers("/api/auth/**", "/api/phone-verifications/**", "/api/account/**").permitAll()
                        // 랭킹은 비로그인도 볼 수 있다 (로그인하면 내 순위가 같이 온다)
                        .requestMatchers(HttpMethod.GET, "/api/rankings/**").permitAll()
                        // 회원 프로필도 비로그인이 볼 수 있다 (개인정보는 싣지 않음)
                        .requestMatchers(HttpMethod.GET, "/api/users/*/profile").permitAll()
                        // 프로필 사진은 랭킹·프로필처럼 비로그인 화면에도 나온다
                        .requestMatchers(HttpMethod.GET, "/api/profile-images/**").permitAll()
                        // 챌린지 둘러보기는 비로그인도 가능. 개설/참여는 아래 authenticated 에 걸린다.
                        .requestMatchers(HttpMethod.GET, "/api/categories", "/api/challenges", "/api/challenges/*",
                                "/api/challenges/invite/*")
                        .permitAll()
                        .requestMatchers("/api/admin/**").hasRole("ADMIN")
                        .anyRequest().authenticated())
                .exceptionHandling(e -> e
                        .authenticationEntryPoint((req, res, ex) -> writeJson(res, 401, UNAUTHORIZED_BODY))
                        .accessDeniedHandler((req, res, ex) -> writeJson(res, 403, FORBIDDEN_BODY)))
                .addFilterBefore(jwtAuthenticationFilter, UsernamePasswordAuthenticationFilter.class);
        return http.build();
    }

    private static void writeJson(HttpServletResponse res, int status, String body) throws IOException {
        res.setStatus(status);
        res.setContentType(MediaType.APPLICATION_JSON_VALUE);
        res.setCharacterEncoding(StandardCharsets.UTF_8.name());
        res.getWriter().write(body);
    }
}
