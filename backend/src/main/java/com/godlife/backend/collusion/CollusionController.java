package com.godlife.backend.collusion;

import com.godlife.backend.collusion.CollusionService.Flag;
import lombok.RequiredArgsConstructor;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestParam;
import org.springframework.web.bind.annotation.RestController;

import java.util.List;

/** 관리자: 담합 의심 조합 (/api/admin/** 는 관리자만 들어올 수 있다) */
@RestController
@RequiredArgsConstructor
public class CollusionController {

    private final CollusionService collusionService;

    /** 의심 조합 목록 (?status=OPEN 이면 아직 안 본 것만) */
    @GetMapping("/api/admin/collusion")
    public List<Flag> list(@RequestParam(required = false) String status) {
        return collusionService.list(status);
    }

    @PostMapping("/api/admin/collusion/{id}/confirm")
    public ResponseEntity<Void> confirm(@PathVariable Long id) {
        collusionService.decide(id, "CONFIRMED");
        return ResponseEntity.noContent().build();
    }

    @PostMapping("/api/admin/collusion/{id}/dismiss")
    public ResponseEntity<Void> dismiss(@PathVariable Long id) {
        collusionService.decide(id, "DISMISSED");
        return ResponseEntity.noContent().build();
    }
}
