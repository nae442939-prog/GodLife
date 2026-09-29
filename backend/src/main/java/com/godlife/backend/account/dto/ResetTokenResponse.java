package com.godlife.backend.account.dto;

/** 인증번호 확인 후 받는 1회용 재설정 토큰. 새 비밀번호 저장 요청에 한 번 제출한다. */
public record ResetTokenResponse(String resetToken) {
}
