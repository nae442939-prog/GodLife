package com.godlife.backend.block;

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

/** blocker 가 blocked 를 차단했다. blocker 의 화면에서만 blocked 의 채팅이 안 보인다. */
@Getter
@NoArgsConstructor(access = AccessLevel.PROTECTED)
@Entity
@Table(name = "user_blocks")
public class UserBlock {

    @Id
    @GeneratedValue(strategy = GenerationType.IDENTITY)
    private Long id;

    @Column(name = "blocker_id", nullable = false, updatable = false)
    private Long blockerId;

    @Column(name = "blocked_id", nullable = false, updatable = false)
    private Long blockedId;

    @Generated(event = EventType.INSERT)
    @Column(name = "created_at", insertable = false, updatable = false)
    private LocalDateTime createdAt;

    public static UserBlock of(Long blockerId, Long blockedId) {
        UserBlock b = new UserBlock();
        b.blockerId = blockerId;
        b.blockedId = blockedId;
        return b;
    }
}
