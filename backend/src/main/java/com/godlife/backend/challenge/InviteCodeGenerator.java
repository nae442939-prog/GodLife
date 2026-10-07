package com.godlife.backend.challenge;

import org.springframework.stereotype.Component;

import java.security.SecureRandom;

/**
 * 초대 링크 코드. 소리 내어 읽거나 옮겨 적을 때 헷갈리는 0/O, 1/I/L 을 뺀 31글자로 8자리를 만든다.
 * (31^8 ≈ 8,500억 가지. 무작위 대입은 초대 API 의 IP 요청 제한이 막는다)
 */
@Component
public class InviteCodeGenerator {

    static final String ALPHABET = "ABCDEFGHJKMNPQRSTUVWXYZ23456789";
    public static final int LENGTH = 8;

    private final SecureRandom random = new SecureRandom();

    public String next() {
        StringBuilder sb = new StringBuilder(LENGTH);
        for (int i = 0; i < LENGTH; i++) {
            sb.append(ALPHABET.charAt(random.nextInt(ALPHABET.length())));
        }
        return sb.toString();
    }
}
