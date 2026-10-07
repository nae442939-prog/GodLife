package com.godlife.backend.verification.ai;

import lombok.extern.slf4j.Slf4j;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.core.io.FileSystemResource;
import org.springframework.http.MediaType;
import org.springframework.http.client.SimpleClientHttpRequestFactory;
import org.springframework.stereotype.Component;
import org.springframework.util.LinkedMultiValueMap;
import org.springframework.util.MultiValueMap;
import org.springframework.web.client.RestClient;
import org.springframework.web.client.RestClientException;

import java.nio.file.Path;
import java.time.Duration;
import java.util.Base64;
import java.util.Map;
import java.util.Optional;

/**
 * ai-server(FastAPI)의 POST /predict 를 부른다. 같은 컴퓨터의 내부 서버라 사진 파일을 그대로 보낸다.
 * 연결 실패 · 시간 초과 · 모델 없음(503)은 모두 '판정할 수 없음'으로 본다 → 인증이 AI 때문에 막히지 않는다.
 */
@Slf4j
@Component
public class HttpAiClassifier implements AiClassifier {

    private static final Duration CONNECT_TIMEOUT = Duration.ofSeconds(1);
    private static final Duration READ_TIMEOUT = Duration.ofSeconds(8);

    private final RestClient client;
    private final boolean enabled;

    public HttpAiClassifier(@Value("${ai-server.base-url}") String baseUrl,
                            @Value("${ai-server.enabled:true}") boolean enabled) {
        SimpleClientHttpRequestFactory factory = new SimpleClientHttpRequestFactory();
        factory.setConnectTimeout(CONNECT_TIMEOUT);
        factory.setReadTimeout(READ_TIMEOUT);
        this.client = RestClient.builder().baseUrl(baseUrl).requestFactory(factory).build();
        this.enabled = enabled;
    }

    @Override
    public Optional<AiPrediction> classify(Path image) {
        if (!enabled) {
            return Optional.empty();
        }
        MultiValueMap<String, Object> form = new LinkedMultiValueMap<>();
        form.add("file", new FileSystemResource(image));
        try {
            Map<?, ?> body = client.post().uri("/predict")
                    .contentType(MediaType.MULTIPART_FORM_DATA)
                    .body(form)
                    .retrieve()
                    .body(Map.class);
            if (body == null || !(body.get("label") instanceof String label)
                    || !(body.get("confidence") instanceof Number confidence)
                    || !(body.get("embedding") instanceof String embedding)) {
                log.warn("AI 서버 응답을 읽을 수 없습니다: {}", body == null ? null : body.keySet());
                return Optional.empty();
            }
            return Optional.of(new AiPrediction(label, confidence.doubleValue(), Base64.getDecoder().decode(embedding),
                    String.valueOf(body.get("model_version"))));
        } catch (RestClientException | IllegalArgumentException e) {
            // 서버가 꺼져 있거나(연결 거부), 모델이 아직 없거나(503), 시간이 넘은 경우
            log.warn("AI 판정을 건너뜁니다: {}", e.getMessage());
            return Optional.empty();
        }
    }
}
