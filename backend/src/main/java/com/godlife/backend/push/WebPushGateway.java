package com.godlife.backend.push;

import lombok.extern.slf4j.Slf4j;
import nl.martijndwars.webpush.Encoding;
import nl.martijndwars.webpush.Notification;
import nl.martijndwars.webpush.PushService;
import org.bouncycastle.jce.provider.BouncyCastleProvider;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.stereotype.Component;

import java.nio.charset.StandardCharsets;
import java.security.Security;

/**
 * 웹 푸시 전송 (RFC 8291 본문 암호화 + VAPID 서명은 web-push 라이브러리가 한다).
 * VAPID 키(backend/.env 의 VAPID_PUBLIC_KEY · VAPID_PRIVATE_KEY)가 없으면 꺼진 상태로 뜬다 — 알림함은 그대로 쓰고 푸시만 안 간다.
 */
@Slf4j
@Component
public class WebPushGateway implements PushGateway {

    /** 받는 기기가 꺼져 있으면 푸시 서비스가 이만큼(초) 보관한다. 하루 지난 인증 알림은 의미가 없다 */
    private static final int TTL_SECONDS = 12 * 60 * 60;

    private final String publicKey;
    private final PushService pushService;

    public WebPushGateway(@Value("${app.push.vapid-public-key:}") String publicKey,
                          @Value("${app.push.vapid-private-key:}") String privateKey,
                          @Value("${app.push.subject:mailto:admin@godlife.local}") String subject) {
        PushService service = null;
        if (!publicKey.isBlank() && !privateKey.isBlank()) {
            try {
                if (Security.getProvider(BouncyCastleProvider.PROVIDER_NAME) == null) {
                    Security.addProvider(new BouncyCastleProvider());
                }
                service = new PushService(publicKey, privateKey, subject);
            } catch (Exception e) {
                log.error("VAPID 키를 읽지 못해 푸시 알림을 끈 채로 시작합니다", e);
            }
        }
        this.publicKey = service == null ? "" : publicKey;
        this.pushService = service;
    }

    @Override
    public boolean enabled() {
        return pushService != null;
    }

    @Override
    public String publicKey() {
        return publicKey;
    }

    @Override
    public int send(String endpoint, String p256dh, String auth, String payloadJson) {
        if (pushService == null) {
            return -1;
        }
        try {
            Notification notification = new Notification(endpoint, p256dh, auth,
                    payloadJson.getBytes(StandardCharsets.UTF_8), TTL_SECONDS);
            return pushService.send(notification, Encoding.AES128GCM).getStatusLine().getStatusCode();
        } catch (InterruptedException e) {
            Thread.currentThread().interrupt();
            return -1;
        } catch (Exception e) {
            log.warn("푸시 전송 실패: {}", e.toString());
            return -1;
        }
    }
}
