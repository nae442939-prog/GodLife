package com.godlife.backend.support;

import com.godlife.backend.common.notify.EmailSender;
import com.godlife.backend.common.notify.SmsSender;
import com.godlife.backend.phone.PhoneVerificationService;
import org.springframework.boot.test.context.TestConfiguration;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Primary;

import java.util.Map;
import java.util.concurrent.ConcurrentHashMap;
import java.util.concurrent.ThreadLocalRandom;
import java.util.regex.Matcher;
import java.util.regex.Pattern;

/**
 * 테스트용 발송기: 보낸 인증번호를 받는 사람별로 기억해 두고 테스트가 꺼내 쓴다.
 * 사용: 테스트 클래스에 {@code @Import(TestSenders.class)}.
 */
@TestConfiguration
public class TestSenders {

    private static final Pattern CODE = Pattern.compile("\\b(\\d{6})\\b");

    @Bean
    public Outbox outbox() {
        return new Outbox();
    }

    @Bean
    @Primary
    public SmsSender capturingSmsSender(Outbox outbox) {
        return (phone, message) -> outbox.last.put(phone, message);
    }

    @Bean
    @Primary
    public EmailSender capturingEmailSender(Outbox outbox) {
        return (to, subject, body) -> outbox.last.put(to, body);
    }

    @Bean
    public PhoneProofs phoneProofs(PhoneVerificationService service, Outbox outbox) {
        return new PhoneProofs(service, outbox);
    }

    public static class Outbox {
        final Map<String, String> last = new ConcurrentHashMap<>();

        /** 받는 사람(숫자만 남긴 번호 또는 이메일)에게 마지막으로 보낸 6자리 인증번호. 없으면 null. */
        public String lastCode(String to) {
            String message = last.get(to);
            if (message == null) {
                return null;
            }
            Matcher m = CODE.matcher(message);
            return m.find() ? m.group(1) : null;
        }
    }

    /** 휴대폰 인증을 끝까지 진행해 가입 등에 쓸 증표를 만든다. */
    public record PhoneProofs(PhoneVerificationService service, Outbox outbox) {

        public static String randomPhone() {
            return "010" + String.format("%08d", ThreadLocalRandom.current().nextInt(100_000_000));
        }

        public String proofFor(String phone) {
            service.send(phone, "127.0.0.1");
            return service.confirm(phone, outbox.lastCode(phone.replaceAll("[^0-9]", "")));
        }

        public String newProof() {
            return proofFor(randomPhone());
        }
    }
}
