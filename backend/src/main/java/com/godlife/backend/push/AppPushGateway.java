package com.godlife.backend.push;

/** 안드로이드 앱 푸시(FCM)로 실제 전송하는 부분. 테스트에서는 가짜로 바꿔 끼운다. */
public interface AppPushGateway {

    /** SENT = FCM 이 받음 / GONE = 없어진 토큰(앱 삭제 · 토큰 바뀜)이라 지울 것 / FAILED = 그 밖의 실패(토큰은 남겨 둔다) */
    enum Result {
        SENT, GONE, FAILED
    }

    /** 서비스 계정 키가 설정돼 있어 보낼 수 있는지 */
    boolean enabled();

    /** 기기 토큰 하나에 보낸다. link 는 알림을 누르면 앱이 열 화면 주소 */
    Result send(String token, String title, String body, String link);
}
