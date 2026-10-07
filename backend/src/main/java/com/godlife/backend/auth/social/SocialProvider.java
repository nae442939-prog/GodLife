package com.godlife.backend.auth.social;

import java.util.Locale;

/** social_accounts.provider. 값은 OAuth2 registrationId(google/kakao/naver)의 대문자다. */
public enum SocialProvider {
    KAKAO, GOOGLE, NAVER;

    public static SocialProvider fromRegistrationId(String registrationId) {
        return valueOf(registrationId.toUpperCase(Locale.ROOT));
    }
}
