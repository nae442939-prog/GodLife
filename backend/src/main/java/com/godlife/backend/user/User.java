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

    /** 휴대폰 번호 HMAC 해시(원문 미저장). 계정당 1개, 번호당 1계정. 소셜 가입 직후에는 null. */
    @Column(name = "phone_hash")
    private String phoneHash;

    /** 휴대폰 번호 암호화 값 (본인에게만 다시 보여 준다). 이 칸이 생기기 전에 인증한 회원은 null */
    @Column(name = "phone_enc")
    private String phoneEnc;

    @Column(name = "tier_id", nullable = false)
    private Integer tierId;

    @Enumerated(EnumType.STRING)
    @Column(nullable = false)
    private Role role;

    @Enumerated(EnumType.STRING)
    @Column(nullable = false)
    private UserStatus status;

    /** 자동 로그인 (설정 → 보안). 켜면 브라우저를 닫아도 로그인이 유지되고, 끄면 브라우저를 닫을 때 로그아웃된다. */
    @Column(name = "auto_login", nullable = false)
    private boolean autoLogin = true;

    /** DB DEFAULT CURRENT_TIMESTAMP 가 채우는 값. INSERT 직후 Hibernate 가 다시 읽어 온다. */
    @Generated(event = EventType.INSERT)
    @Column(name = "created_at", insertable = false, updatable = false)
    private LocalDateTime createdAt;

    public static User createWithPassword(String email, String passwordHash, String nickname, String phoneHash) {
        User user = create(email, nickname);
        user.passwordHash = passwordHash;
        user.phoneHash = phoneHash;
        return user;
    }

    /** 소셜 전용 가입자. 비밀번호가 없어 이메일 로그인은 할 수 없다. */
    public static User createSocial(String email, String nickname) {
        return create(email, nickname);
    }

    public boolean hasPhone() {
        return phoneHash != null;
    }

    public void registerPhone(String phoneHash) {
        this.phoneHash = phoneHash;
        this.phoneEnc = null;
    }

    /** 인증한 번호의 암호화 값을 기억해 둔다 (설정 화면에서 본인에게 보여 주려고) */
    public void rememberPhone(String phoneEnc) {
        this.phoneEnc = phoneEnc;
    }

    public void changePassword(String passwordHash) {
        this.passwordHash = passwordHash;
    }

    public void changeAutoLogin(boolean autoLogin) {
        this.autoLogin = autoLogin;
    }

    /** 닉네임 · 자기소개 고치기 (bio 는 비우면 null) */
    public void updateProfile(String nickname, String bio) {
        this.nickname = nickname;
        this.bio = bio;
    }

    /** 프로필 사진 주소 바꾸기 (null 이면 기본 아이콘) */
    public void changeProfileImage(String profileImageUrl) {
        this.profileImageUrl = profileImageUrl;
    }

    /** 탈퇴: 개인정보를 지우고 계정을 닫는다 (이메일은 바꿀 수 없는 칸이라 서비스에서 따로 바꾼다) */
    public void withdraw(String anonymousNickname) {
        this.status = UserStatus.WITHDRAWN;
        this.nickname = anonymousNickname;
        this.passwordHash = null;
        this.phoneHash = null;
        this.phoneEnc = null;
        this.profileImageUrl = null;
        this.bio = null;
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
