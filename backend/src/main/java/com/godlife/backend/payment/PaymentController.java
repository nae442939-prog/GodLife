package com.godlife.backend.payment;

import com.godlife.backend.auth.AuthUser;
import com.godlife.backend.payment.dto.PaymentDtos.PaymentConfig;
import com.godlife.backend.payment.dto.PaymentDtos.ReadyRequest;
import com.godlife.backend.payment.dto.PaymentDtos.ReadyResponse;
import com.godlife.backend.payment.dto.PaymentDtos.TossConfirmRequest;
import com.godlife.backend.wallet.dto.WalletResponse;
import jakarta.validation.Valid;
import lombok.RequiredArgsConstructor;
import org.springframework.http.HttpStatus;
import org.springframework.security.core.annotation.AuthenticationPrincipal;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.ResponseStatus;
import org.springframework.web.bind.annotation.RestController;

/** 포인트 충전 결제 (토스페이먼츠 테스트 모드). 로그인 회원만. 환불(결제 취소)은 지갑 쪽 POST /api/wallet/refund 에 있다. */
@RestController
@RequiredArgsConstructor
public class PaymentController {

    private final PaymentService paymentService;

    /** 충전 화면 설정: 고를 수 있는 금액, 결제할 수 있는지, 토스 공개 키 */
    @GetMapping("/api/payments/config")
    public PaymentConfig config() {
        return paymentService.config();
    }

    /** 결제 준비: 주문을 만들고 주문 번호를 돌려준다 */
    @PostMapping("/api/payments/ready")
    public ReadyResponse ready(@AuthenticationPrincipal AuthUser authUser, @Valid @RequestBody ReadyRequest request) {
        return paymentService.ready(authUser.id(), request.amount());
    }

    /** 결제 승인 → 충전 */
    @PostMapping("/api/payments/toss/confirm")
    public WalletResponse confirm(@AuthenticationPrincipal AuthUser authUser,
                                  @Valid @RequestBody TossConfirmRequest request) {
        return paymentService.confirm(authUser.id(), request.paymentKey(), request.orderId(), request.amount());
    }

    /** 결제를 그만뒀거나 실패했을 때 승인 전 주문을 닫는다 */
    @PostMapping("/api/payments/{orderId}/fail")
    @ResponseStatus(HttpStatus.NO_CONTENT)
    public void fail(@AuthenticationPrincipal AuthUser authUser, @PathVariable String orderId) {
        paymentService.fail(authUser.id(), orderId);
    }
}
