package com.godlife.backend.user;

import jakarta.persistence.Column;
import jakarta.persistence.Entity;
import jakarta.persistence.EnumType;
import jakarta.persistence.Enumerated;
import jakarta.persistence.GeneratedValue;
import jakarta.persistence.GenerationType;
import jakarta.persistence.Id;
import jakarta.persistence.Table;
import org.hibernate.annotations.Generated;
import org.hibernate.generator.EventType;
import lombok.AccessLevel;
import lombok.Getter;
import lombok.NoArgsConstructor;

import java.time.LocalDateTime;

@Getter
@NoArgsConstructor(access = AccessLevel.PROTECTED)
@Entity
@Table(name = "users")
public class User {

    /** tiers.id = 1 (BRONZE). 신규 가입자는 가장 낮은 베팅 상한에서 시작한다. */
    public static final int DEFAULT_TIER_ID = 1;

    @Id
    @GeneratedValue(strategy = GenerationType.IDENTITY)
    private Long id;

    @Column(nullable = false, updatable = false)
    private String email;

    /** BCrypt 해시. 소셜 전용 가입자는 null. */
    @Column(name = "password_hash")
    private String passwordHash;

    @Column(nullable = false)
    private String nickname;

    @Column(name = "profile_image_url")
    private String profileImageUrl;

    private String bio;

    @Column(name = "tier_id", nullable = false)
    private Integer tierId;

    @Enumerated(EnumType.STRING)
    @Column(nullable = false)
    private Role role;

    @Enumerated(EnumType.STRING)
    @Column(nullable = false)
    private UserStatus status;

    /** DB DEFAULT CURRENT_TIMESTAMP 가 채우는 값. INSERT 직후 Hibernate 가 다시 읽어 온다. */
    @Generated(event = EventType.INSERT)
    @Column(name = "created_at", insertable = false, updatable = false)
    private LocalDateTime createdAt;

    public static User createWithPassword(String email, String passwordHash, String nickname) {
        User user = create(email, nickname);
        user.passwordHash = passwordHash;
        return user;
    }

    /** 소셜 전용 가입자. 비밀번호가 없어 이메일 로그인은 할 수 없다. */
    public static User createSocial(String email, String nickname) {
        return create(email, nickname);
    }

    private static User create(String email, String nickname) {
        User user = new User();
        user.email = email;
        user.nickname = nickname;
        user.tierId = DEFAULT_TIER_ID;
        user.role = Role.USER;
        user.status = UserStatus.ACTIVE;
        return user;
    }
}
