package com.godlife.backend.common.notify;

/** 메일 발송. 구현은 app.mail.mode 로 고른다 (demo / smtp). */
public interface EmailSender {

    void send(String to, String subject, String body);

    /** true 면 실제로 보내지 않는 데모 발송기다. 이때 서비스는 인증번호를 API 응답으로 돌려준다. */
    default boolean isDemo() {
        return false;
    }
}
