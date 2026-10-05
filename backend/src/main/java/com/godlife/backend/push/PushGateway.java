package com.godlife.backend.push;

/** 브라우저 푸시 서비스(FCM · Mozilla · Apple · Windows)로 실제 전송하는 부분. 테스트에서는 가짜로 바꿔 끼운다. */
public interface PushGateway {

    /** VAPID 키가 설정돼 있어 보낼 수 있는지 */
    boolean enabled();

    /** 브라우저가 구독할 때 쓰는 공개 키 (없으면 빈 문자열) */
    String publicKey();

    /**
     * 구독 하나에 암호화해서 보낸다.
     * @return 푸시 서비스의 HTTP 상태 코드 (보내지 못했으면 -1). 404 · 410 은 사라진 구독이다
     */
    int send(String endpoint, String p256dh, String auth, String payloadJson);
}
