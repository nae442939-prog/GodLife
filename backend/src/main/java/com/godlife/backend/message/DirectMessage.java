package com.godlife.backend.message;

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

/** 1:1 메시지 한 건. 두 사람 대화는 (작은 id, 큰 id) 쌍으로 묶는다. */
@Getter
@NoArgsConstructor(access = AccessLevel.PROTECTED)
@Entity
@Table(name = "direct_messages")
public class DirectMessage {

    @Id
    @GeneratedValue(strategy = GenerationType.IDENTITY)
    private Long id;

    @Column(name = "sender_id", nullable = false, updatable = false)
    private Long senderId;

    @Column(name = "receiver_id", nullable = false, updatable = false)
    private Long receiverId;

    @Column(name = "low_id", nullable = false, updatable = false)
    private Long lowId;

    @Column(name = "high_id", nullable = false, updatable = false)
    private Long highId;

    @Column(nullable = false, updatable = false)
    private String content;

    /** 챌린지 초대 카드면 그 챌린지. 챌린지가 삭제되면 DB 가 비운다 */
    @Column(name = "challenge_id", updatable = false)
    private Long challengeId;

    @Generated(event = EventType.INSERT)
    @Column(name = "created_at", insertable = false, updatable = false)
    private LocalDateTime createdAt;

    @Column(name = "read_at")
    private LocalDateTime readAt;

    static DirectMessage of(Long senderId, Long receiverId, String content) {
        DirectMessage m = new DirectMessage();
        m.senderId = senderId;
        m.receiverId = receiverId;
        m.lowId = Math.min(senderId, receiverId);
        m.highId = Math.max(senderId, receiverId);
        m.content = content;
        return m;
    }

    static DirectMessage invite(Long senderId, Long receiverId, String content, Long challengeId) {
        DirectMessage m = of(senderId, receiverId, content);
        m.challengeId = challengeId;
        return m;
    }
}
