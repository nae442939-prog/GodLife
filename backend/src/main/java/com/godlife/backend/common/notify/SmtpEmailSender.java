package com.godlife.backend.common.notify;

import jakarta.mail.MessagingException;
import jakarta.mail.internet.MimeMessage;
import org.springframework.mail.MailPreparationException;
import org.springframework.mail.javamail.JavaMailSender;
import org.springframework.mail.javamail.MimeMessageHelper;

import java.io.UnsupportedEncodingException;
import java.nio.charset.StandardCharsets;

/** Gmail SMTP 등으로 실제 메일을 보낸다. 실패하면 MailException 을 그대로 던진다. */
public class SmtpEmailSender implements EmailSender {

    /** 받는 사람 메일함에 보이는 보낸사람 이름. 한글이라 헤더 인코딩(UTF-8)이 필요하다. */
    private static final String SENDER_NAME = "갓생살기";

    private final JavaMailSender mailSender;
    private final String from;

    public SmtpEmailSender(JavaMailSender mailSender, String from) {
        if (from == null || from.isBlank()) {
            throw new IllegalStateException("MAIL_MODE=smtp 이면 MAIL_USERNAME, MAIL_PASSWORD 를 설정해야 합니다.");
        }
        this.mailSender = mailSender;
        this.from = from;
    }

    @Override
    public void send(String to, String subject, String body) {
        MimeMessage message = mailSender.createMimeMessage();
        try {
            MimeMessageHelper helper = new MimeMessageHelper(message, StandardCharsets.UTF_8.name());
            helper.setFrom(from, SENDER_NAME);
            helper.setTo(to);
            helper.setSubject(subject);
            helper.setText(body);
        } catch (MessagingException | UnsupportedEncodingException e) {
            throw new MailPreparationException("메일 작성 실패", e);
        }
        mailSender.send(message);
    }
}
