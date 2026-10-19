package com.godlife.backend.push;

import io.jsonwebtoken.Jwts;
import lombok.extern.slf4j.Slf4j;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.http.HttpStatus;
import org.springframework.http.MediaType;
import org.springframework.http.client.SimpleClientHttpRequestFactory;
import org.springframework.stereotype.Component;
import org.springframework.util.LinkedMultiValueMap;
import org.springframework.util.MultiValueMap;
import org.springframework.web.client.RestClient;
import org.springframework.web.client.RestClientException;
import org.springframework.web.client.RestClientResponseException;
import tools.jackson.databind.ObjectMapper;

import java.nio.file.Files;
import java.nio.file.Path;
import java.security.KeyFactory;
import java.security.PrivateKey;
import java.security.spec.PKCS8EncodedKeySpec;
import java.time.Duration;
import java.time.Instant;
import java.util.Base64;
import java.util.Date;
import java.util.Map;

/**
 * FCM HTTP v1 전송. Firebase 서비스 계정 키(JSON 파일, backend/.env 의 FCM_SERVICE_ACCOUNT_FILE)로
 * 구글 OAuth 토큰을 받아 보낸다 — 서명은 이미 쓰는 jjwt 로 직접 한다(Firebase SDK 를 따로 넣지 않는다).
 * 키 파일이 없으면 꺼진 상태로 뜬다 — 알림함은 그대로 쓰고 앱 푸시만 안 간다. 키 파일은 저장소에 올리지 않는다 (규칙 6).
 */
@Slf4j
@Component
public class FcmGateway implements AppPushGateway {

    private static final String SCOPE = "https://www.googleapis.com/auth/firebase.messaging";
    private static final String SEND_URL = "https://fcm.googleapis.com/v1/projects/%s/messages:send";
    /** 기기가 꺼져 있으면 FCM 이 이만큼 보관한다. 하루 지난 인증 알림은 의미가 없다 (웹 푸시와 같게) */
    private static final String TTL = "43200s";

    private record Account(String projectId, String clientEmail, PrivateKey key, String tokenUri) {
    }

    private final Account account;
    private final RestClient client;
    private String accessToken;
    private Instant accessTokenExpiry = Instant.EPOCH;

    public FcmGateway(@Value("${app.push.fcm.service-account-file:}") String file, ObjectMapper objectMapper) {
        this.account = file.isBlank() ? null : load(Path.of(file), objectMapper);
        SimpleClientHttpRequestFactory factory = new SimpleClientHttpRequestFactory();
        factory.setConnectTimeout(Duration.ofSeconds(3));
        factory.setReadTimeout(Duration.ofSeconds(10));
        this.client = RestClient.builder().requestFactory(factory).build();
    }

    private static Account load(Path file, ObjectMapper objectMapper) {
        try {
            Map<?, ?> json = objectMapper.readValue(Files.readString(file), Map.class);
            String pem = ((String) json.get("private_key"))
                    .replaceAll("-----(BEGIN|END) PRIVATE KEY-----", "").replaceAll("\\s", "");
            PrivateKey key = KeyFactory.getInstance("RSA")
                    .generatePrivate(new PKCS8EncodedKeySpec(Base64.getDecoder().decode(pem)));
            Object tokenUri = json.get("token_uri");
            return new Account((String) json.get("project_id"), (String) json.get("client_email"), key,
                    tokenUri instanceof String s ? s : "https://oauth2.googleapis.com/token");
        } catch (Exception e) {
            log.error("FCM 서비스 계정 키({})를 읽지 못해 앱 푸시를 끈 채로 시작합니다: {}", file, e.toString());
            return null;
        }
    }

    @Override
    public boolean enabled() {
        return account != null;
    }

    @Override
    public Result send(String token, String title, String body, String link) {
        if (account == null) {
            return Result.FAILED;
        }
        Map<String, Object> message = Map.of("message", Map.of(
                "token", token,
                "notification", Map.of("title", title, "body", body == null ? "" : body),
                "data", Map.of("link", link == null ? "/" : link),
                "android", Map.of("priority", "HIGH", "ttl", TTL)));
        try {
            client.post().uri(SEND_URL.formatted(account.projectId()))
                    .header("Authorization", "Bearer " + accessToken())
                    .contentType(MediaType.APPLICATION_JSON)
                    .body(message)
                    .retrieve()
                    .toBodilessEntity();
            return Result.SENT;
        } catch (RestClientResponseException e) {
            String error = e.getResponseBodyAsString();
            // 404 UNREGISTERED: 앱이 지워졌거나 토큰이 바뀜 / 400 은 토큰 모양이 틀렸을 때
            if (e.getStatusCode().value() == 404
                    || (e.getStatusCode().value() == 400 && error.contains("registration token"))) {
                return Result.GONE;
            }
            if (e.getStatusCode() == HttpStatus.UNAUTHORIZED) {
                expireAccessToken();
            }
            log.warn("FCM 전송이 받아들여지지 않음: {} {}", e.getStatusCode(), error);
            return Result.FAILED;
        } catch (RestClientException e) {
            log.warn("FCM 연결 실패: {}", e.getMessage());
            return Result.FAILED;
        }
    }

    /** 구글 OAuth 토큰 (한 시간짜리를 만료 5분 전까지 다시 쓴다) */
    private synchronized String accessToken() {
        Instant now = Instant.now();
        if (accessToken != null && now.isBefore(accessTokenExpiry.minusSeconds(300))) {
            return accessToken;
        }
        String assertion = Jwts.builder()
                .issuer(account.clientEmail())
                .audience().add(account.tokenUri()).and()
                .claim("scope", SCOPE)
                .issuedAt(Date.from(now))
                .expiration(Date.from(now.plusSeconds(3600)))
                .signWith(account.key(), Jwts.SIG.RS256)
                .compact();
        MultiValueMap<String, String> form = new LinkedMultiValueMap<>();
        form.add("grant_type", "urn:ietf:params:oauth:grant-type:jwt-bearer");
        form.add("assertion", assertion);
        Map<?, ?> res = client.post().uri(account.tokenUri())
                .contentType(MediaType.APPLICATION_FORM_URLENCODED)
                .body(form)
                .retrieve()
                .body(Map.class);
        if (res == null || !(res.get("access_token") instanceof String token)) {
            throw new RestClientException("구글 OAuth 토큰 응답에 access_token 이 없음");
        }
        long expiresIn = res.get("expires_in") instanceof Number n ? n.longValue() : 3600;
        accessToken = token;
        accessTokenExpiry = now.plusSeconds(expiresIn);
        return token;
    }

    private synchronized void expireAccessToken() {
        accessToken = null;
    }
}
