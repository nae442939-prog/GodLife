package com.godlife.backend.follow;

import com.godlife.backend.auth.AuthUser;
import lombok.RequiredArgsConstructor;
import org.springframework.http.ResponseEntity;
import org.springframework.security.core.annotation.AuthenticationPrincipal;
import org.springframework.web.bind.annotation.DeleteMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RestController;

/** 팔로우 / 언팔로우 (로그인 회원). 여러 번 눌러도 결과가 같다. */
@RestController
@RequiredArgsConstructor
public class FollowController {

    private final FollowService followService;

    @PostMapping("/api/users/{id}/follow")
    public ResponseEntity<Void> follow(@PathVariable Long id, @AuthenticationPrincipal AuthUser authUser) {
        followService.follow(authUser.id(), id);
        return ResponseEntity.noContent().build();
    }

    @DeleteMapping("/api/users/{id}/follow")
    public ResponseEntity<Void> unfollow(@PathVariable Long id, @AuthenticationPrincipal AuthUser authUser) {
        followService.unfollow(authUser.id(), id);
        return ResponseEntity.noContent().build();
    }
}
