package com.godlife.backend.account.dto;

import java.time.LocalDateTime;
import java.util.List;

/**
 * @param maskedEmail 가려진 로그인 이메일. 소셜 가입(가상 이메일)이면 null.
 * @param hasPassword 이메일+비밀번호로 로그인할 수 있는지
 * @param providers   연결된 소셜 로그인 (KAKAO, GOOGLE, NAVER)
 */
public record FindIdResponse(String maskedEmail, boolean hasPassword, List<String> providers,
                             LocalDateTime createdAt) {
}
