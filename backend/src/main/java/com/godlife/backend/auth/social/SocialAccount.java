package com.godlife.backend.auth.social;

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

@Getter
@NoArgsConstructor(access = AccessLevel.PROTECTED)
@Entity
@Table(name = "social_accounts")
public class SocialAccount {

    @Id
    @GeneratedValue(strategy = GenerationType.IDENTITY)
    private Long id;

    @Column(name = "user_id", nullable = false, updatable = false)
    private Long userId;

    @Enumerated(EnumType.STRING)
    @Column(nullable = false, updatable = false)
    private SocialProvider provider;

    @Column(name = "provider_user_id", nullable = false, updatable = false)
    private String providerUserId;

    public static SocialAccount link(Long userId, SocialProvider provider, String providerUserId) {
        SocialAccount account = new SocialAccount();
        account.userId = userId;
        account.provider = provider;
        account.providerUserId = providerUserId;
        return account;
    }
}
