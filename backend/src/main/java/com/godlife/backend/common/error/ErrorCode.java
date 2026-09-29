package com.godlife.backend.common.error;

import lombok.Getter;
import lombok.RequiredArgsConstructor;
import org.springframework.http.HttpStatus;

@Getter
@RequiredArgsConstructor
public enum ErrorCode {

    VALIDATION_ERROR(HttpStatus.BAD_REQUEST, "입력값이 올바르지 않습니다."),
    UNAUTHORIZED(HttpStatus.UNAUTHORIZED, "로그인이 필요합니다."),
    FORBIDDEN(HttpStatus.FORBIDDEN, "접근 권한이 없습니다."),
    INTERNAL_ERROR(HttpStatus.INTERNAL_SERVER_ERROR, "서버 오류가 발생했습니다."),

    DUPLICATE_EMAIL(HttpStatus.CONFLICT, "이미 가입된 이메일입니다."),
    DUPLICATE_NICKNAME(HttpStatus.CONFLICT, "이미 사용 중인 닉네임입니다."),
    INVALID_CREDENTIALS(HttpStatus.UNAUTHORIZED, "이메일 또는 비밀번호가 올바르지 않습니다."),
    ACCOUNT_SUSPENDED(HttpStatus.FORBIDDEN, "이용이 정지된 계정입니다."),
    INVALID_REFRESH_TOKEN(HttpStatus.UNAUTHORIZED, "세션이 만료되었습니다. 다시 로그인해 주세요."),
    SOCIAL_LOGIN_FAILED(HttpStatus.UNAUTHORIZED, "소셜 로그인에 실패했습니다. 다시 시도해 주세요."),

    TOO_MANY_REQUESTS(HttpStatus.TOO_MANY_REQUESTS, "요청이 너무 잦습니다. 잠시 후 다시 시도해 주세요."),
    INVALID_VERIFICATION_CODE(HttpStatus.BAD_REQUEST, "인증번호가 올바르지 않거나 만료되었습니다."),
    VERIFICATION_ATTEMPTS_EXCEEDED(HttpStatus.BAD_REQUEST, "인증번호를 너무 많이 틀렸습니다. 인증번호를 다시 받아 주세요."),
    PHONE_VERIFICATION_REQUIRED(HttpStatus.BAD_REQUEST, "휴대폰 인증이 필요합니다. 다시 인증해 주세요."),
    PHONE_ALREADY_REGISTERED(HttpStatus.CONFLICT, "이미 다른 계정에 등록된 휴대폰 번호입니다."),
    ACCOUNT_NOT_FOUND(HttpStatus.NOT_FOUND, "이 번호로 가입된 계정이 없습니다."),
    INVALID_RESET_TOKEN(HttpStatus.BAD_REQUEST, "비밀번호 재설정 시간이 지났습니다. 처음부터 다시 진행해 주세요.");

    private final HttpStatus status;
    private final String message;
}
