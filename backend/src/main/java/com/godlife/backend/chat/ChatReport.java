package com.godlife.backend.chat;

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

/** 오픈채팅 메시지 신고. (message_id, reporter_id) 는 유니크 — 같은 메시지를 두 번 신고할 수 없다. */
@Getter
@NoArgsConstructor(access = AccessLevel.PROTECTED)
@Entity
@Table(name = "chat_reports")
public class ChatReport {

    @Id
    @GeneratedValue(strategy = GenerationType.IDENTITY)
    private Long id;

    @Column(name = "challenge_id", nullable = false, updatable = false)
    private Long challengeId;

    @Column(name = "message_id", nullable = false, updatable = false)
    private Long messageId;

    @Column(name = "reporter_id", nullable = false, updatable = false)
    private Long reporterId;

    @Column(name = "reported_user_id", nullable = false, updatable = false)
    private Long reportedUserId;

    @Enumerated(EnumType.STRING)
    @Column(nullable = false, updatable = false)
    private ReportReason reason;

    @Column(updatable = false)
    private String detail;

    @Enumerated(EnumType.STRING)
    @Column(nullable = false)
    private ReportStatus status;

    @Generated(event = EventType.INSERT)
    @Column(name = "created_at", insertable = false, updatable = false)
    private LocalDateTime createdAt;

    public static ChatReport of(ChatMessage message, Long reporterId, ReportReason reason, String detail) {
        ChatReport r = new ChatReport();
        r.challengeId = message.getChallengeId();
        r.messageId = message.getId();
        r.reporterId = reporterId;
        r.reportedUserId = message.getSenderId();
        r.reason = reason;
        r.detail = detail == null || detail.isBlank() ? null : detail.strip();
        r.status = ReportStatus.OPEN;
        return r;
    }
}
