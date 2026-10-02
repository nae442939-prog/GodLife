package com.godlife.backend.payment;

import com.godlife.backend.common.error.BusinessException;
import com.godlife.backend.common.error.ErrorCode;

/** PG 가 요청을 거절했거나 응답하지 않았다. 메시지는 PG 가 준 안내(없으면 기본 문구)다. */
public class PgException extends BusinessException {

    public PgException(ErrorCode errorCode, String message) {
        super(errorCode, message == null || message.isBlank() ? errorCode.getMessage() : message);
    }
}
