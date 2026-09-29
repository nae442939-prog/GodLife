package com.godlife.backend.block;

import com.godlife.backend.auth.AuthUser;
import com.godlife.backend.block.dto.BlockedUserResponse;
import lombok.RequiredArgsConstructor;
import org.springframework.http.ResponseEntity;
import org.springframework.security.core.annotation.AuthenticationPrincipal;
import org.springframework.web.bind.annotation.DeleteMapping;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.PutMapping;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;

import java.util.List;

/** 내 차단 목록. PUT 은 멱등(이미 차단했으면 그대로). */
@RestController
@RequestMapping("/api/users/me/blocks")
@RequiredArgsConstructor
public class BlockController {

    private final BlockService blockService;

    @GetMapping
    public List<BlockedUserResponse> list(@AuthenticationPrincipal AuthUser authUser) {
        return blockService.blocked(authUser.id());
    }

    @PutMapping("/{userId}")
    public ResponseEntity<Void> block(@AuthenticationPrincipal AuthUser authUser, @PathVariable Long userId) {
        blockService.block(authUser.id(), userId);
        return ResponseEntity.noContent().build();
    }

    @DeleteMapping("/{userId}")
    public ResponseEntity<Void> unblock(@AuthenticationPrincipal AuthUser authUser, @PathVariable Long userId) {
        blockService.unblock(authUser.id(), userId);
        return ResponseEntity.noContent().build();
    }
}
