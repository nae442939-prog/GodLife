package com.godlife.backend.wallet;

import com.godlife.backend.auth.AuthUser;
import com.godlife.backend.wallet.dto.PointTransactionResponse;
import com.godlife.backend.wallet.dto.RefundRequest;
import com.godlife.backend.wallet.dto.TestChargeRequest;
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

/** 내 포인트 지갑. 로그인 회원만. 현금 출금·환전은 없고, 쓰지 않은 충전 포인트의 결제 취소 환불만 있다 (프로젝트 규칙 2). */
@RestController
@RequiredArgsConstructor
public class WalletController {

    private final WalletService walletService;

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

    /** 충전 포인트 환불 (쓰지 않은 충전 포인트까지만, 결제 취소). 보상 포인트는 환불하지 않는다. */
    @PostMapping("/api/wallet/refund")
    public WalletResponse refund(@AuthenticationPrincipal AuthUser authUser,
                                 @Valid @RequestBody RefundRequest request) {
        return walletService.refundCharged(authUser.id(), request.amount(), request.requestKey());
    }

    /** 테스트 충전 (결제 연동 전 가상 지급: 1,000 / 5,000 / 10,000P, 하루 한도 있음) */
    @PostMapping("/api/wallet/test-charge")
    public WalletResponse testCharge(@AuthenticationPrincipal AuthUser authUser,
                                     @Valid @RequestBody TestChargeRequest request) {
        return walletService.testCharge(authUser.id(), request.amount(), request.requestKey());
    }
}
