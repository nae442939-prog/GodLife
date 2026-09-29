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
    INVALID_RESET_TOKEN(HttpStatus.BAD_REQUEST, "비밀번호 재설정 시간이 지났습니다. 처음부터 다시 진행해 주세요."),
    PHONE_NOT_REGISTERED(HttpStatus.FORBIDDEN, "휴대폰 인증을 마친 회원만 이용할 수 있습니다."),

    CATEGORY_NOT_FOUND(HttpStatus.BAD_REQUEST, "없는 카테고리입니다."),
    CHALLENGE_NOT_FOUND(HttpStatus.NOT_FOUND, "챌린지를 찾을 수 없습니다."),
    INVITE_NOT_FOUND(HttpStatus.NOT_FOUND, "유효하지 않은 초대 링크입니다. 링크가 바뀌었는지 개설자에게 물어보세요."),
    CHALLENGE_NOT_RECRUITING(HttpStatus.CONFLICT, "모집이 끝난 챌린지입니다."),
    CHALLENGE_FULL(HttpStatus.CONFLICT, "참여 인원이 모두 찼습니다."),
    ALREADY_JOINED(HttpStatus.CONFLICT, "이미 참여 중인 챌린지입니다."),
    NOT_JOINED(HttpStatus.CONFLICT, "참여 중인 챌린지가 아닙니다."),
    CHALLENGE_ALREADY_STARTED(HttpStatus.CONFLICT, "이미 시작된 챌린지는 참여를 취소할 수 없습니다."),
    KICKED_FROM_CHALLENGE(HttpStatus.FORBIDDEN, "방장이 내보낸 챌린지에는 다시 참여할 수 없어요."),
    CANNOT_KICK(HttpStatus.BAD_REQUEST, "내보낼 수 없는 참가자예요."),
    MESSAGE_NOT_FOUND(HttpStatus.NOT_FOUND, "메시지를 찾을 수 없어요."),
    CANNOT_REPORT(HttpStatus.BAD_REQUEST, "내 메시지나 안내 메시지는 신고할 수 없어요."),
    ALREADY_REPORTED(HttpStatus.CONFLICT, "이미 신고한 메시지예요."),
    CANNOT_BLOCK(HttpStatus.BAD_REQUEST, "나 자신은 차단할 수 없어요."),
    CHALLENGE_CANNOT_DELETE(HttpStatus.CONFLICT, "시작된 챌린지는 삭제할 수 없어요. 시작일 전날까지만 삭제할 수 있어요."),
    POINT_CHALLENGE_NOT_READY(HttpStatus.CONFLICT, "포인트 챌린지 참여는 준비 중입니다. 포인트 지갑이 열리면 참여할 수 있어요.");

    private final HttpStatus status;
    private final String message;
}
