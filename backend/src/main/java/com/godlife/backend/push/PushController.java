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

/** 푸시 알림 구독 (로그인 회원, 이 브라우저의 구독만 다룬다) */
@RestController
@RequiredArgsConstructor
public class PushController {

    private final PushNotifier pushNotifier;

    /** 브라우저의 PushSubscription.toJSON() 모양 그대로 */
    public record Keys(String p256dh, String auth) {
    }

    public record SubscriptionRequest(String endpoint, Keys keys) {
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
}
