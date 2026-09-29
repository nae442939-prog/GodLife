package com.godlife.backend.chat;

import jakarta.persistence.Column;
import jakarta.persistence.Entity;
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

/** 챌린지 오픈채팅 메시지. 챌린지 1개가 채팅방 1개라 방 테이블 없이 challengeId 로 묶는다. */
@Getter
@NoArgsConstructor(access = AccessLevel.PROTECTED)
@Entity
@Table(name = "chat_messages")
public class ChatMessage {

    @Id
    @GeneratedValue(strategy = GenerationType.IDENTITY)
    private Long id;

    @Column(name = "challenge_id", nullable = false, updatable = false)
    private Long challengeId;

    @Column(name = "sender_id", nullable = false, updatable = false)
    private Long senderId;

    @Column(nullable = false, updatable = false)
    private String content;

    @Generated(event = EventType.INSERT)
    @Column(name = "created_at", insertable = false, updatable = false)
    private LocalDateTime createdAt;

    public static ChatMessage of(Long challengeId, Long senderId, String content) {
        ChatMessage m = new ChatMessage();
        m.challengeId = challengeId;
        m.senderId = senderId;
        m.content = content;
        return m;
    }
}
