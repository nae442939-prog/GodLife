package com.godlife.backend.wallet;

import com.godlife.backend.auth.AuthUser;
import com.godlife.backend.payment.PaymentRefundService;
import com.godlife.backend.wallet.dto.PointTransactionResponse;
import com.godlife.backend.wallet.dto.RefundRequest;
import com.godlife.backend.wallet.dto.WalletResponse;
import jakarta.validation.Valid;
import lombok.RequiredArgsConstructor;
import org.springframework.security.core.annotation.AuthenticationPrincipal;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestParam;
import org.springframework.web.bind.annotation.RestController;

import java.util.List;

/**
 * 내 포인트 지갑. 로그인 회원만. 현금 출금·환전은 없고, 쓰지 않은 충전 포인트의 결제 취소 환불만 있다 (CLAUDE.md 규칙 2).
 * 충전은 PG 결제(테스트 모드)로만 한다 → payment 패키지의 /api/payments/**.
 */
@RestController
@RequiredArgsConstructor
public class WalletController {

    private final WalletService walletService;
    private final PaymentRefundService refundService;

    @GetMapping("/api/wallet")
    public WalletResponse wallet(@AuthenticationPrincipal AuthUser authUser) {
        return walletService.wallet(authUser.id());
    }

    /** 거래 내역 더 보기 (?page=1 부터) */
    @GetMapping("/api/wallet/transactions")
    public List<PointTransactionResponse> transactions(@AuthenticationPrincipal AuthUser authUser,
                                                       @RequestParam(defaultValue = "0") int page) {
        return walletService.transactions(authUser.id(), page);
    }

    /** 충전 포인트 환불 (쓰지 않은 충전 포인트까지만, PG 결제 취소). 보상 포인트는 환불하지 않는다. */
    @PostMapping("/api/wallet/refund")
    public WalletResponse refund(@AuthenticationPrincipal AuthUser authUser,
                                 @Valid @RequestBody RefundRequest request) {
        return refundService.refund(authUser.id(), request.amount(), request.requestKey());
    }
}
