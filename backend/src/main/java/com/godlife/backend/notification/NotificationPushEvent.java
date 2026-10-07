package com.godlife.backend.notification;

/** 알림함에 새 알림이 생겼다 — 이 회원이 푸시를 켜 둔 기기가 있으면 푸시로도 보낸다 (push 패키지가 듣는다) */
public record NotificationPushEvent(Long userId, String title, String body, String link) {
}
