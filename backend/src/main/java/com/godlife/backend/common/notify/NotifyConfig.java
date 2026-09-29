package com.godlife.backend.common.notify;

import lombok.extern.slf4j.Slf4j;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.boot.autoconfigure.condition.ConditionalOnProperty;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;
import org.springframework.mail.javamail.JavaMailSender;

/**
 * 발송기 선택. 문자는 포트폴리오용이라 데모 발송만 둔다: 실제로 보내지 않고(무료) 서비스가 인증번호를 화면으로 돌려준다.
 * 실서비스로 바꿀 때는 SmsSender 구현(예: 국내 문자 업체 API)만 추가하면 되고, 인증 로직은 그대로다.
 * 메일은 무료인 Gmail SMTP 로 실제 발송할 수 있다 (app.mail.mode=smtp).
 */
@Slf4j
@Configuration
public class NotifyConfig {

    @Bean
    public SmsSender demoSmsSender() {
        return new SmsSender() {
            @Override
            public void send(String phone, String message) {
                log.info("[DEMO SMS] to={} | {}", phone, message);
            }

            @Override
            public boolean isDemo() {
                return true;
            }
        };
    }

    @Bean
    @ConditionalOnProperty(name = "app.mail.mode", havingValue = "demo", matchIfMissing = true)
    public EmailSender demoEmailSender() {
        return new EmailSender() {
            @Override
            public void send(String to, String subject, String body) {
                log.info("[DEMO MAIL] to={} | {} | {}", to, subject, body);
            }

            @Override
            public boolean isDemo() {
                return true;
            }
        };
    }

    @Bean
    @ConditionalOnProperty(name = "app.mail.mode", havingValue = "smtp")
    public EmailSender smtpEmailSender(JavaMailSender mailSender, @Value("${spring.mail.username}") String from) {
        return new SmtpEmailSender(mailSender, from);
    }
}
