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
     * @param amounts       고를 수 있는 충전 금액 (1P = 1원)
     * @param enabled       지금 결제할 수 있는지 (토스 테스트 키가 설정돼 있는지)
     * @param tossClientKey 토스 결제창을 띄울 때 쓰는 공개 키 (테스트 키)
     */
    public record PaymentConfig(List<Long> amounts, boolean enabled, String tossClientKey) {
    }

    public record ReadyRequest(long amount) {
    }

    /** 결제 준비 결과. 금액은 서버가 주문에 적어 둔 값이고, 승인할 때 이 값과 다르면 거절한다. */
    public record ReadyResponse(String orderId, long amount, String orderName) {
    }

    /** 토스 결제창이 성공 주소로 넘겨 준 값 그대로 */
    public record TossConfirmRequest(@NotBlank @Size(max = 200) String paymentKey,
                                     @NotBlank @Size(max = 64) String orderId, long amount) {
    }
}
