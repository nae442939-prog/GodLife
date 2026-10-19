package com.godlife.backend.push;

import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.io.TempDir;
import tools.jackson.databind.ObjectMapper;
import tools.jackson.databind.json.JsonMapper;

import java.nio.charset.StandardCharsets;
import java.nio.file.Files;
import java.nio.file.Path;
import java.security.KeyPairGenerator;
import java.util.Base64;
import java.util.Map;

import static org.assertj.core.api.Assertions.assertThat;

/** FCM 서비스 계정 키 파일 읽기: 있으면 켜지고, 없거나 망가졌으면 꺼진 채로 뜬다 (서버는 그대로 켜진다) */
class FcmGatewayTest {

    private final ObjectMapper objectMapper = JsonMapper.builder().build();

    @Test
    @DisplayName("서비스 계정 키 파일이 있으면 켜진다")
    void enabledWithKeyFile(@TempDir Path dir) throws Exception {
        KeyPairGenerator gen = KeyPairGenerator.getInstance("RSA");
        gen.initialize(2048);
        byte[] der = gen.generateKeyPair().getPrivate().getEncoded();
        String pem = "-----BEGIN PRIVATE KEY-----\n"
                + Base64.getMimeEncoder(64, "\n".getBytes(StandardCharsets.US_ASCII)).encodeToString(der)
                + "\n-----END PRIVATE KEY-----\n";
        Path file = dir.resolve("service-account.json");
        Files.writeString(file, objectMapper.writeValueAsString(Map.of(
                "type", "service_account", "project_id", "godlife-test",
                "client_email", "push@godlife-test.iam.gserviceaccount.com", "private_key", pem,
                "token_uri", "https://oauth2.googleapis.com/token")));

        assertThat(new FcmGateway(file.toString(), objectMapper).enabled()).isTrue();
    }

    @Test
    @DisplayName("키 파일 경로가 비었거나 파일이 없거나 망가졌으면 꺼진다")
    void disabledWithoutKey(@TempDir Path dir) throws Exception {
        Path broken = dir.resolve("broken.json");
        Files.writeString(broken, "{\"private_key\":\"not a key\"}");

        assertThat(new FcmGateway("", objectMapper).enabled()).isFalse();
        assertThat(new FcmGateway(broken.toString(), objectMapper).enabled()).isFalse();
        assertThat(new FcmGateway(dir.resolve("missing.json").toString(), objectMapper).enabled()).isFalse();
        assertThat(new FcmGateway("", objectMapper).send("t", "a", "b", "/")).isEqualTo(AppPushGateway.Result.FAILED);
    }
}
