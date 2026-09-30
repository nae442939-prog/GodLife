package com.godlife.backend.wallet.dto;

import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.Size;

/** 충전 포인트 환불. requestKey 는 화면이 누를 때마다 만드는 값이라 같은 요청이 두 번 와도 한 번만 환불된다. */
public record RefundRequest(long amount, @NotBlank @Size(max = 60) String requestKey) {
}
