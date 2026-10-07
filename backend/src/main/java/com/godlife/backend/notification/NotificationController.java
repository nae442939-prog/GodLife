package com.godlife.backend.notification;

import com.godlife.backend.auth.AuthUser;
import com.godlife.backend.notification.NotificationService.NotificationItem;
import com.godlife.backend.notification.NotificationService.Settings;
import lombok.RequiredArgsConstructor;
import org.springframework.http.ResponseEntity;
import org.springframework.security.core.annotation.AuthenticationPrincipal;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.PutMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RestController;

import java.util.List;
import java.util.Map;

/** 알림함 (로그인 회원, 본인 알림만) */
@RestController
@RequiredArgsConstructor
public class NotificationController {

    private final NotificationService notificationService;

    /** 내 알림 (최근 것부터 50개) */
    @GetMapping("/api/notifications")
    public List<NotificationItem> list(@AuthenticationPrincipal AuthUser authUser) {
        return notificationService.list(authUser.id());
    }

    /** 안 읽은 알림 수 (헤더의 종) */
    @GetMapping("/api/notifications/unread-count")
    public Map<String, Long> unreadCount(@AuthenticationPrincipal AuthUser authUser) {
        return Map.of("count", notificationService.unreadCount(authUser.id()));
    }

    @PostMapping("/api/notifications/{id}/read")
    public ResponseEntity<Void> read(@PathVariable Long id, @AuthenticationPrincipal AuthUser authUser) {
        notificationService.read(authUser.id(), id);
        return ResponseEntity.noContent().build();
    }

    @PostMapping("/api/notifications/read-all")
    public ResponseEntity<Void> readAll(@AuthenticationPrincipal AuthUser authUser) {
        notificationService.readAll(authUser.id());
        return ResponseEntity.noContent().build();
    }

    /** 알림 설정: 종류별 켜기/끄기 */
    @GetMapping("/api/notifications/settings")
    public Settings settings(@AuthenticationPrincipal AuthUser authUser) {
        return notificationService.settings(authUser.id());
    }

    @PutMapping("/api/notifications/settings")
    public Settings updateSettings(@AuthenticationPrincipal AuthUser authUser, @RequestBody Settings settings) {
        return notificationService.updateSettings(authUser.id(), settings);
    }
}
