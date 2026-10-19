package com.godlife.backend.push;

import com.godlife.backend.auth.AuthUser;
import lombok.RequiredArgsConstructor;
import org.springframework.http.ResponseEntity;
import org.springframework.security.core.annotation.AuthenticationPrincipal;
import org.springframework.web.bind.annotation.DeleteMapping;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RestController;

/** 푸시 알림 구독 (로그인 회원, 이 브라우저 · 이 휴대폰의 구독만 다룬다) */
@RestController
@RequiredArgsConstructor
public class PushController {

    private final PushNotifier pushNotifier;
    private final AppPushNotifier appPushNotifier;

    /** 브라우저의 PushSubscription.toJSON() 모양 그대로 */
    public record Keys(String p256dh, String auth) {
    }

    public record SubscriptionRequest(String endpoint, Keys keys) {
    }

    /** 안드로이드 앱의 FCM 기기 토큰 */
    public record AppTokenRequest(String token) {
    }

    /** 푸시를 쓸 수 있는지와 구독할 때 쓸 공개 키 */
    @GetMapping("/api/push/config")
    public PushNotifier.Config config() {
        return pushNotifier.config();
    }

    @PostMapping("/api/push/subscriptions")
    public ResponseEntity<Void> subscribe(@RequestBody SubscriptionRequest request,
                                          @AuthenticationPrincipal AuthUser authUser) {
        Keys keys = request.keys() == null ? new Keys(null, null) : request.keys();
        pushNotifier.subscribe(authUser.id(), request.endpoint(), keys.p256dh(), keys.auth());
        return ResponseEntity.noContent().build();
    }

    @DeleteMapping("/api/push/subscriptions")
    public ResponseEntity<Void> unsubscribe(@RequestBody SubscriptionRequest request,
                                            @AuthenticationPrincipal AuthUser authUser) {
        pushNotifier.unsubscribe(authUser.id(), request.endpoint());
        return ResponseEntity.noContent().build();
    }

    /** 앱(안드로이드)이 로그인하면 이 휴대폰의 FCM 토큰을 남긴다 */
    @PostMapping("/api/push/app-tokens")
    public ResponseEntity<Void> registerApp(@RequestBody AppTokenRequest request,
                                            @AuthenticationPrincipal AuthUser authUser) {
        appPushNotifier.register(authUser.id(), request.token());
        return ResponseEntity.noContent().build();
    }

    /** 앱에서 로그아웃하기 전에 이 휴대폰의 토큰을 지운다 */
    @DeleteMapping("/api/push/app-tokens")
    public ResponseEntity<Void> unregisterApp(@RequestBody AppTokenRequest request,
                                              @AuthenticationPrincipal AuthUser authUser) {
        appPushNotifier.unregister(authUser.id(), request.token());
        return ResponseEntity.noContent().build();
    }
}
