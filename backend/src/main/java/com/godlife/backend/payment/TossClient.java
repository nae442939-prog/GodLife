package com.godlife.backend.payment;

import com.godlife.backend.common.error.ErrorCode;
import lombok.extern.slf4j.Slf4j;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.http.MediaType;
import org.springframework.http.client.SimpleClientHttpRequestFactory;
import org.springframework.stereotype.Component;
import org.springframework.web.client.RestClient;
import org.springframework.web.client.RestClientException;
import org.springframework.web.client.RestClientResponseException;

import java.nio.charset.StandardCharsets;
import java.time.Duration;
import java.util.Base64;
import java.util.Map;

/**
 * 토스페이먼츠 API (결제 승인 · 결제 취소). 결제 대행사는 토스페이먼츠 하나만 쓴다. 테스트 키(test_ 로 시작)만 받는다 — 라이브 키가 들어오면 쓰지 않는다 (CLAUDE.md 규칙 3).
 * 카드 정보는 토스 결제창에서만 입력하고 서버로 오지 않는다. 서버는 결제키(paymentKey)만 다룬다.
 * 같은 Idempotency-Key 로 다시 부르면 토스가 처음 응답을 그대로 돌려주므로, 재시도해도 두 번 승인·취소되지 않는다.
 */
@Slf4j
@Component
public class TossClient {

    private static final String BASE_URL = "https://api.tosspayments.com";
    private static final Duration CONNECT_TIMEOUT = Duration.ofSeconds(3);
    private static final Duration READ_TIMEOUT = Duration.ofSeconds(15);

    private final RestClient client;
    private final String clientKey;
    private final boolean configured;

    public TossClient(@Value("${app.payment.toss.client-key:}") String clientKey,
                      @Value("${app.payment.toss.secret-key:}") String secretKey) {
        boolean test = clientKey.startsWith("test_") && secretKey.startsWith("test_");
        if (!test && !(clientKey.isBlank() && secretKey.isBlank())) {
            log.warn("토스페이먼츠 키가 테스트 키(test_...)가 아니어서 쓰지 않습니다. 결제는 테스트 모드로만 연동합니다.");
        }
        this.clientKey = test ? clientKey : "";
        this.configured = test;
        SimpleClientHttpRequestFactory factory = new SimpleClientHttpRequestFactory();
        factory.setConnectTimeout(CONNECT_TIMEOUT);
        factory.setReadTimeout(READ_TIMEOUT);
        String basic = Base64.getEncoder().encodeToString((secretKey + ":").getBytes(StandardCharsets.UTF_8));
        this.client = RestClient.builder().baseUrl(BASE_URL).requestFactory(factory)
                .defaultHeader("Authorization", "Basic " + basic).build();
    }

    public boolean configured() {
        return configured;
    }

    /** 결제창을 띄울 때 화면이 쓰는 공개 키 */
    public String clientKey() {
        return clientKey;
    }

    /** 결제 승인. 결제창에서 인증이 끝난 결제를 실제로 승인한다. 승인된 금액을 돌려준다. */
    public long confirm(String paymentKey, String orderId, long amount) {
        Map<?, ?> body = post("/v1/payments/confirm", "confirm-" + orderId,
                Map.of("paymentKey", paymentKey, "orderId", orderId, "amount", amount), ErrorCode.PAYMENT_FAILED);
        return body.get("totalAmount") instanceof Number total ? total.longValue() : amount;
    }

    /** 결제 취소 (부분 취소). 충전 포인트 환불에 쓴다. */
    public void cancel(String paymentKey, long cancelAmount, String reason, String idempotencyKey) {
        post("/v1/payments/" + paymentKey + "/cancel", idempotencyKey,
                Map.of("cancelReason", reason, "cancelAmount", cancelAmount), ErrorCode.REFUND_FAILED);
    }

    private Map<?, ?> post(String uri, String idempotencyKey, Map<String, Object> request, ErrorCode onError) {
        try {
            Map<?, ?> body = client.post().uri(uri)
                    .header("Idempotency-Key", idempotencyKey)
                    .contentType(MediaType.APPLICATION_JSON)
                    .body(request)
                    .retrieve()
                    .body(Map.class);
            return body == null ? Map.of() : body;
        } catch (RestClientResponseException e) {
            // 토스 오류 응답: {"code": "...", "message": "..."}
            String message = null;
            try {
                Map<?, ?> error = e.getResponseBodyAs(Map.class);
                message = error != null && error.get("message") instanceof String m ? m : null;
            } catch (RuntimeException ignored) {
                // 본문을 읽지 못하면 기본 문구를 쓴다
            }
            log.warn("토스페이먼츠 {} 실패: {} {}", uri, e.getStatusCode(), message);
            throw new PgException(onError, message);
        } catch (RestClientException e) {
            log.warn("토스페이먼츠 {} 연결 실패: {}", uri, e.getMessage());
            throw new PgException(onError, null);
        }
    }
}
