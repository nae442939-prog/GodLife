package com.godlife.backend.common.notify;

/** 문자 발송. 지금은 데모 발송기(실제 발송 안 함)만 있다. 실제 업체를 붙일 때 이 인터페이스를 구현한다. */
public interface SmsSender {

    void send(String phone, String message);

    /** true 면 실제로 보내지 않는 데모 발송기다. 이때 서비스는 인증번호를 API 응답으로 돌려준다. */
    default boolean isDemo() {
        return false;
    }
}
