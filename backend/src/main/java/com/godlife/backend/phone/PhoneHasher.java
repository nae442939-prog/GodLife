package com.godlife.backend.phone;

import org.springframework.beans.factory.annotation.Value;
import org.springframework.stereotype.Component;

import javax.crypto.Mac;
import javax.crypto.spec.SecretKeySpec;
import java.nio.charset.StandardCharsets;
import java.security.GeneralSecurityException;
import java.util.HexFormat;

/**
 * 휴대폰 번호는 원문을 저장하지 않고 HMAC-SHA256 으로만 저장한다.
 * 번호는 경우의 수가 적어 단순 SHA-256 이면 전부 계산해 되돌릴 수 있으므로, 서버만 아는 키를 섞는다.
 */
@Component
public class PhoneHasher {

    private static final int MIN_SECRET_BYTES = 32;

    private final SecretKeySpec key;

    public PhoneHasher(@Value("${app.phone-hmac-secret}") String secret) {
        if (secret == null || secret.getBytes(StandardCharsets.UTF_8).length < MIN_SECRET_BYTES) {
            throw new IllegalStateException("app.phone-hmac-secret(PHONE_HMAC_SECRET)은 32바이트 이상이어야 합니다.");
        }
        this.key = new SecretKeySpec(secret.getBytes(StandardCharsets.UTF_8), "HmacSHA256");
    }

    /** 하이픈/공백을 뺀 숫자만 남긴다. 형식 검사는 요청 DTO 가 한다. */
    public static String normalize(String phone) {
        return phone.replaceAll("[^0-9]", "");
    }

    public String hash(String phone) {
        try {
            Mac mac = Mac.getInstance("HmacSHA256");
            mac.init(key);
            return HexFormat.of().formatHex(mac.doFinal(normalize(phone).getBytes(StandardCharsets.UTF_8)));
        } catch (GeneralSecurityException e) {
            throw new IllegalStateException(e);
        }
    }
}
