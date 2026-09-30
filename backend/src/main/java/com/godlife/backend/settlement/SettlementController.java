package com.godlife.backend.settlement;

import com.godlife.backend.auth.AuthUser;
import com.godlife.backend.settlement.dto.RankingResponse;
import com.godlife.backend.settlement.dto.SettlementSummaryResponse;
import lombok.RequiredArgsConstructor;
import org.springframework.http.ResponseEntity;
import org.springframework.security.core.annotation.AuthenticationPrincipal;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.RestController;

import java.util.List;

/** 챌린지 정산 결과 · 랭킹. 로그인 + 개설자/참가자만 (아니면 404). */
@RestController
@RequiredArgsConstructor
public class SettlementController {

    private final SettlementViewService viewService;

    /** 어제(주 N회는 지난주) 결과. 아직 끝난 기간이 없으면 204. */
    @GetMapping("/api/challenges/{id}/settlements/latest")
    public ResponseEntity<SettlementSummaryResponse> latest(@PathVariable Long id,
                                                            @AuthenticationPrincipal AuthUser authUser) {
        return viewService.latest(id, authUser.id())
                .map(ResponseEntity::ok)
                .orElseGet(() -> ResponseEntity.noContent().build());
    }

    @GetMapping("/api/challenges/{id}/ranking")
    public List<RankingResponse> ranking(@PathVariable Long id, @AuthenticationPrincipal AuthUser authUser) {
        return viewService.ranking(id, authUser.id());
    }
}
