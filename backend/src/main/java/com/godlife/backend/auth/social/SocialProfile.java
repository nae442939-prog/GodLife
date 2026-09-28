package com.godlife.backend.auth.social;

import java.util.Map;

/**
 * 제공자마다 다른 사용자 정보 응답을 하나의 모양으로 맞춘 것.
 *
 * @param email         제공자가 준 이메일. 없을 수 있다(카카오는 동의항목 설정 필요).
 * @param emailVerified 제공자가 "이 이메일의 소유를 확인했다"고 명시한 경우만 true.
 *                      true 일 때만 같은 이메일의 기존 계정에 연결한다.
 * @param nickname      제공자 닉네임/이름. 우리 닉네임 규칙에 맞게 다듬는 건 서비스가 한다.
 */
public record SocialProfile(
        SocialProvider provider,
        String providerUserId,
        String email,
        boolean emailVerified,
        String nickname) {

    public static SocialProfile of(String registrationId, Map<String, Object> attributes) {
        return switch (SocialProvider.fromRegistrationId(registrationId)) {
            case GOOGLE -> google(attributes);
            case KAKAO -> kakao(attributes);
            case NAVER -> naver(attributes);
        };
    }

    // https://www.googleapis.com/oauth2/v3/userinfo → { sub, name, email, email_verified }
    private static SocialProfile google(Map<String, Object> a) {
        return new SocialProfile(SocialProvider.GOOGLE, required(a, "sub"),
                str(a, "email"), Boolean.TRUE.equals(a.get("email_verified")), str(a, "name"));
    }

    // https://kapi.kakao.com/v2/user/me → { id, kakao_account: { email, is_email_valid, is_email_verified, profile: { nickname } } }
    private static SocialProfile kakao(Map<String, Object> a) {
        Map<String, Object> account = map(a, "kakao_account");
        Map<String, Object> profile = map(account, "profile");
        boolean verified = Boolean.TRUE.equals(account.get("is_email_valid"))
                && Boolean.TRUE.equals(account.get("is_email_verified"));
        return new SocialProfile(SocialProvider.KAKAO, required(a, "id"),
                str(account, "email"), verified, str(profile, "nickname"));
    }

    // https://openapi.naver.com/v1/nid/me → { response: { id, email, nickname, name } }
    // 네이버는 이메일 인증 여부를 알려 주지 않으므로 검증되지 않은 것으로 취급한다.
    private static SocialProfile naver(Map<String, Object> a) {
        Map<String, Object> r = map(a, "response");
        String nickname = str(r, "nickname") != null ? str(r, "nickname") : str(r, "name");
        return new SocialProfile(SocialProvider.NAVER, required(r, "id"), str(r, "email"), false, nickname);
    }

    private static String required(Map<String, Object> m, String key) {
        String v = str(m, key);
        if (v == null || v.isBlank()) {
            throw new IllegalArgumentException("소셜 사용자 정보에 " + key + " 가 없습니다.");
        }
        return v;
    }

    private static String str(Map<String, Object> m, String key) {
        Object v = m.get(key);
        return v == null ? null : String.valueOf(v);
    }

    @SuppressWarnings("unchecked")
    private static Map<String, Object> map(Map<String, Object> m, String key) {
        return m.get(key) instanceof Map<?, ?> v ? (Map<String, Object>) v : Map.of();
    }
}
