package com.godlife.backend.wallet.dto;

import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.Size;

/**
 * 테스트 충전. requestKey 는 화면이 버튼을 누를 때마다 새로 만드는 값(UUID)이다.
 * 같은 값으로 두 번 오면(더블클릭·재전송) 한 번만 충전된다.
 */
public record TestChargeRequest(long amount, @NotBlank @Size(max = 60) String requestKey) {
}
