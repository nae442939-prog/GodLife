package com.godlife.backend.common.error;

import lombok.Getter;

@Getter
public class BusinessException extends RuntimeException {

    private final ErrorCode errorCode;

    public BusinessException(ErrorCode errorCode) {
        this(errorCode, errorCode.getMessage());
    }

    /** 기본 메시지 대신 상황에 맞는 메시지(예: 남은 대기 시간)를 보낸다. */
    public BusinessException(ErrorCode errorCode, String message) {
        super(message);
        this.errorCode = errorCode;
    }

    /** "N초 후" / "N분 후" (올림) */
    public static String after(java.time.Duration wait) {
        long seconds = Math.max(1, (long) Math.ceil(wait.toMillis() / 1000.0));
        return seconds < 60 ? seconds + "초 후" : (seconds + 59) / 60 + "분 후";
    }
}
