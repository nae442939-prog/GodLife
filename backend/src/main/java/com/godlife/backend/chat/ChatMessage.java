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

    @Enumerated(EnumType.STRING)
    @Column(nullable = false, updatable = false)
    private ChatMessageType type;

    /** 글. 사진만 보낸 메시지는 null */
    @Column(updatable = false)
    private String content;

    /** 사진 파일 키 (ImageStore). 글만 보낸 메시지는 null */
    @Column(name = "image_key", updatable = false)
    private String imageKey;

    @Generated(event = EventType.INSERT)
    @Column(name = "created_at", insertable = false, updatable = false)
    private LocalDateTime createdAt;

    public static ChatMessage of(Long challengeId, Long senderId, String content) {
        ChatMessage m = new ChatMessage();
        m.challengeId = challengeId;
        m.senderId = senderId;
        m.content = content;
        m.type = ChatMessageType.USER;
        return m;
    }

    /** 사진 메시지. 글(caption)은 없어도 된다. */
    public static ChatMessage image(Long challengeId, Long senderId, String caption, String imageKey) {
        ChatMessage m = of(challengeId, senderId, caption == null || caption.isBlank() ? null : caption.strip());
        m.imageKey = imageKey;
        return m;
    }

    /** 강퇴·공지 같은 안내. 보낸 사람은 방장으로 둔다. */
    public static ChatMessage system(Long challengeId, Long hostId, String content) {
        ChatMessage m = of(challengeId, hostId, content);
        m.type = ChatMessageType.SYSTEM;
        return m;
    }
}
