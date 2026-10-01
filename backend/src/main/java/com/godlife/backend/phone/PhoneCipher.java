package com.godlife.backend.phone;

import lombok.extern.slf4j.Slf4j;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.stereotype.Component;

import javax.crypto.Cipher;
import javax.crypto.spec.GCMParameterSpec;
import javax.crypto.spec.SecretKeySpec;
import java.nio.ByteBuffer;
import java.nio.charset.StandardCharsets;
import java.security.GeneralSecurityException;
import java.security.MessageDigest;
import java.security.SecureRandom;
import java.util.Base64;

/**
 * 휴대폰 번호를 본인에게 다시 보여 주기 위한 암호화 (AES-256-GCM).
 * 번호의 중복·본인 확인은 여전히 HMAC 해시({@link PhoneHasher})로 하고, 이 값은 설정 화면에서 본인에게만 보여 줄 때 쓴다.
 * 키는 휴대폰 해시 키(PHONE_HMAC_SECRET)에서 용도 이름을 섞어 따로 뽑는다 → DB 만 새어 나가서는 번호를 알 수 없다.
 */
@Slf4j
@Component
public class PhoneCipher {

    private static final int IV_BYTES = 12;
    private static final int TAG_BITS = 128;
    private static final SecureRandom RANDOM = new SecureRandom();

    private final SecretKeySpec key;

    public PhoneCipher(@Value("${app.phone-hmac-secret}") String secret) {
        try {
            byte[] derived = MessageDigest.getInstance("SHA-256")
                    .digest(("godlife-phone-display:" + secret).getBytes(StandardCharsets.UTF_8));
            this.key = new SecretKeySpec(derived, "AES");
        } catch (GeneralSecurityException e) {
            throw new IllegalStateException(e);
        }
    }

    /** 숫자만 남긴 번호를 암호화한다 (같은 번호도 매번 다른 값이 나온다) */
    public String encrypt(String phone) {
        try {
            byte[] iv = new byte[IV_BYTES];
            RANDOM.nextBytes(iv);
            Cipher cipher = Cipher.getInstance("AES/GCM/NoPadding");
            cipher.init(Cipher.ENCRYPT_MODE, key, new GCMParameterSpec(TAG_BITS, iv));
            byte[] encrypted = cipher.doFinal(PhoneHasher.normalize(phone).getBytes(StandardCharsets.UTF_8));
            return Base64.getEncoder().encodeToString(
                    ByteBuffer.allocate(iv.length + encrypted.length).put(iv).put(encrypted).array());
        } catch (GeneralSecurityException e) {
            throw new IllegalStateException(e);
        }
    }

    /** 010-1234-5678 모양으로 되돌린다. 값이 없거나 풀 수 없으면 null (키가 바뀐 경우 등) */
    public String decryptFormatted(String encrypted) {
        if (encrypted == null) {
            return null;
        }
        try {
            byte[] raw = Base64.getDecoder().decode(encrypted);
            Cipher cipher = Cipher.getInstance("AES/GCM/NoPadding");
            cipher.init(Cipher.DECRYPT_MODE, key, new GCMParameterSpec(TAG_BITS, raw, 0, IV_BYTES));
            String digits = new String(cipher.doFinal(raw, IV_BYTES, raw.length - IV_BYTES), StandardCharsets.UTF_8);
            return format(digits);
        } catch (GeneralSecurityException | IllegalArgumentException e) {
            log.warn("휴대폰 번호를 복호화하지 못했습니다.");
            return null;
        }
    }

    private static String format(String digits) {
        if (digits.length() < 10) {
            return digits;
        }
        int mid = digits.length() - 4;
        return digits.substring(0, 3) + "-" + digits.substring(3, mid) + "-" + digits.substring(mid);
    }
}
