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

import java.time.Duration;
import java.util.LinkedHashMap;
import java.util.Map;

/**
 * 카카오페이 온라인 단건 결제 API (결제 준비 · 승인 · 취소 · 조회). 테스트 모드 전용 (프로젝트 규칙 3):
 * 시크릿 키는 개발용(DEV...), 가맹점 코드는 테스트 코드(TC...)만 받는다 — 운영 키가 들어오면 쓰지 않는다.
 * 카드 정보는 카카오페이 결제창에서만 입력하고 서버로 오지 않는다. 서버는 결제 고유번호(tid)만 다룬다.
 */
@Slf4j
@Component
public class KakaoPayClient {

    private static final String BASE_URL = "https://open-api.kakaopay.com";
    private static final Duration CONNECT_TIMEOUT = Duration.ofSeconds(3);
    private static final Duration READ_TIMEOUT = Duration.ofSeconds(15);

    /** 결제 준비 결과: 결제 고유번호와 결제창 주소 (PC · 모바일 웹) */
    public record Ready(String tid, String pcUrl, String mobileUrl) {
    }

    private final RestClient client;
    private final String cid;
    private final boolean configured;

    public KakaoPayClient(@Value("${app.payment.kakaopay.secret-key:}") String secretKey,
                          @Value("${app.payment.kakaopay.cid:TC0ONETIME}") String cid) {
        boolean test = secretKey.startsWith("DEV") && cid.startsWith("TC");
        if (!test && !secretKey.isBlank()) {
            log.warn("카카오페이 키가 테스트 키(DEV... · 가맹점 TC...)가 아니어서 쓰지 않습니다. 결제는 테스트 모드로만 연동합니다.");
        }
        this.cid = cid;
        this.configured = test;
        SimpleClientHttpRequestFactory factory = new SimpleClientHttpRequestFactory();
        factory.setConnectTimeout(CONNECT_TIMEOUT);
        factory.setReadTimeout(READ_TIMEOUT);
        this.client = RestClient.builder().baseUrl(BASE_URL).requestFactory(factory)
                .defaultHeader("Authorization", "SECRET_KEY " + (test ? secretKey : "")).build();
    }

    public boolean configured() {
        return configured;
    }

    /** 결제 준비. approvalUrl 뒤에 카카오페이가 pg_token 을 붙여 돌려보낸다 */
    public Ready ready(String orderId, Long userId, String itemName, long amount,
                       String approvalUrl, String cancelUrl, String failUrl) {
        Map<String, Object> request = new LinkedHashMap<>();
        request.put("cid", cid);
        request.put("partner_order_id", orderId);
        request.put("partner_user_id", String.valueOf(userId));
        request.put("item_name", itemName);
        request.put("quantity", 1);
        request.put("total_amount", amount);
        request.put("tax_free_amount", 0);
        request.put("approval_url", approvalUrl);
        request.put("cancel_url", cancelUrl);
        request.put("fail_url", failUrl);
        Map<?, ?> body = post("/online/v1/payment/ready", request, ErrorCode.PAYMENT_FAILED);
        return new Ready((String) body.get("tid"), (String) body.get("next_redirect_pc_url"),
                (String) body.get("next_redirect_mobile_url"));
    }

    /** 결제 승인. 승인된 금액을 돌려준다 */
    public long approve(String tid, String orderId, Long userId, String pgToken) {
        Map<?, ?> body = post("/online/v1/payment/approve", Map.of("cid", cid, "tid", tid,
                "partner_order_id", orderId, "partner_user_id", String.valueOf(userId), "pg_token", pgToken),
                ErrorCode.PAYMENT_FAILED);
        return total(body.get("amount"));
    }

    /** 결제 (부분) 취소. 충전 포인트 환불에 쓴다 */
    public void cancel(String tid, long cancelAmount) {
        post("/online/v1/payment/cancel", Map.of("cid", cid, "tid", tid, "cancel_amount", cancelAmount,
                "cancel_tax_free_amount", 0), ErrorCode.REFUND_FAILED);
    }

    /**
     * 카카오페이에 기록된 이 결제의 취소된 금액 합.
     * 카카오페이 취소에는 멱등 키가 없어서, 취소 응답을 못 받고 다시 부를 때 두 번 취소하지 않으려고 먼저 본다.
     */
    public long canceledTotal(String tid) {
        Map<?, ?> body = post("/online/v1/payment/order", Map.of("cid", cid, "tid", tid), ErrorCode.REFUND_FAILED);
        return total(body.get("canceled_amount"));
    }

    private static long total(Object amount) {
        return amount instanceof Map<?, ?> m && m.get("total") instanceof Number n ? n.longValue() : 0;
    }

    private Map<?, ?> post(String uri, Map<String, Object> request, ErrorCode onError) {
        try {
            Map<?, ?> body = client.post().uri(uri)
                    .contentType(MediaType.APPLICATION_JSON)
                    .body(request)
                    .retrieve()
                    .body(Map.class);
            return body == null ? Map.of() : body;
        } catch (RestClientResponseException e) {
            // 카카오페이 오류 응답: {"error_code": ..., "error_message": "..."}
            String message = null;
            try {
                Map<?, ?> error = e.getResponseBodyAs(Map.class);
                message = error != null && error.get("error_message") instanceof String m ? m : null;
            } catch (RuntimeException ignored) {
                // 본문을 읽지 못하면 기본 문구를 쓴다
            }
            log.warn("카카오페이 {} 실패: {} {}", uri, e.getStatusCode(), message);
            throw new PgException(onError, message);
        } catch (RestClientException e) {
            log.warn("카카오페이 {} 연결 실패: {}", uri, e.getMessage());
            throw new PgException(onError, null);
        }
    }
}
