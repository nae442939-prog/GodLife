package com.godlife.backend.notification;

import jakarta.persistence.Column;
import jakarta.persistence.Entity;
import jakarta.persistence.EnumType;
import jakarta.persistence.Enumerated;
import jakarta.persistence.GeneratedValue;
import jakarta.persistence.GenerationType;
import jakarta.persistence.Id;
import jakarta.persistence.Table;
import lombok.AccessLevel;
import lombok.Getter;
import lombok.NoArgsConstructor;
import org.hibernate.annotations.Generated;
import org.hibernate.generator.EventType;

import java.time.LocalDateTime;

/** 알림. 지금은 신고 누적 알림(REPORT_ALERT)만 쓴다. 알림함 화면은 리텐션(3차) 때 만든다. */
@Getter
@NoArgsConstructor(access = AccessLevel.PROTECTED)
@Entity
@Table(name = "notifications")
public class Notification {

    public enum Type {
        SETTLEMENT, VERIFY_REMINDER, COMMENT, REPORT_RESULT, REPORT_ALERT
    }

    @Id
    @GeneratedValue(strategy = GenerationType.IDENTITY)
    private Long id;

    @Column(name = "user_id", nullable = false, updatable = false)
    private Long userId;

    @Enumerated(EnumType.STRING)
    @Column(nullable = false, updatable = false)
    private Type type;

    @Column(nullable = false)
    private String title;

    @Column(nullable = false)
    private String body;

    @Column(name = "read_at")
    private LocalDateTime readAt;

    @Generated(event = EventType.INSERT)
    @Column(name = "sent_at", insertable = false, updatable = false)
    private LocalDateTime sentAt;

    public static Notification of(Long userId, Type type, String title, String body) {
        Notification n = new Notification();
        n.userId = userId;
        n.type = type;
        n.title = title;
        n.body = body;
        return n;
    }
}
