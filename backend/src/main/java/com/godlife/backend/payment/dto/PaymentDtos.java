package com.godlife.backend.payment.dto;

import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.Size;

import java.util.List;

/** 결제(포인트 충전) 요청 · 응답 */
public final class PaymentDtos {

    private PaymentDtos() {
    }

    /**
     * 충전 화면이 처음에 받는 설정.
     * @param amounts         고를 수 있는 충전 금액 (1P = 1원)
     * @param enabled         결제 수단이 하나라도 있는지
     * @param tossEnabled     토스 테스트 키가 설정돼 있는지
     * @param tossClientKey   토스 결제창을 띄울 때 쓰는 공개 키 (테스트 키)
     * @param kakaoPayEnabled 카카오페이 테스트 키가 설정돼 있는지
     */
    public record PaymentConfig(List<Long> amounts, boolean enabled, boolean tossEnabled, String tossClientKey,
                                boolean kakaoPayEnabled) {

        public PaymentConfig(List<Long> amounts, boolean tossEnabled, String tossClientKey, boolean kakaoPayEnabled) {
            this(amounts, tossEnabled || kakaoPayEnabled, tossEnabled, tossClientKey, kakaoPayEnabled);
        }
    }

    /** @param provider TOSS(기본) | KAKAOPAY */
    public record ReadyRequest(long amount, String provider) {
    }

    /**
     * 결제 준비 결과. 금액은 서버가 주문에 적어 둔 값이고, 승인할 때 이 값과 다르면 거절한다.
     * 카카오페이는 결제창 주소(PC · 모바일 웹)가 함께 온다. 토스는 둘 다 null (화면이 토스 결제창을 직접 띄운다).
     */
    public record ReadyResponse(String orderId, long amount, String orderName, String provider,
                                String redirectUrl, String mobileRedirectUrl) {
    }

    /** 카카오페이 결제창이 승인 주소로 넘겨 준 pg_token 과 주문 번호 */
    public record KakaoApproveRequest(@NotBlank @Size(max = 64) String orderId,
                                      @NotBlank @Size(max = 200) String pgToken) {
    }

    /** 토스 결제창이 성공 주소로 넘겨 준 값 그대로 */
    public record TossConfirmRequest(@NotBlank @Size(max = 200) String paymentKey,
                                     @NotBlank @Size(max = 64) String orderId, long amount) {
    }
}
