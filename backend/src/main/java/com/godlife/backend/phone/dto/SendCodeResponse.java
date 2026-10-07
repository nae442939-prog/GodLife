package com.godlife.backend.phone.dto;

/**
 * 인증번호 발송 응답.
 *
 * @param demoCode 데모 모드(실제 문자/메일을 보내지 않음)일 때만 인증번호가 담긴다. 실제 발송이면 null.
 */
public record SendCodeResponse(String demoCode) {
}
