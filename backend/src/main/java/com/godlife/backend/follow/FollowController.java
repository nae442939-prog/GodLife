package com.godlife.backend.follow;

import com.godlife.backend.auth.AuthUser;
import com.godlife.backend.follow.FollowRepository.FollowUser;
import lombok.RequiredArgsConstructor;
import org.springframework.http.ResponseEntity;
import org.springframework.security.core.annotation.AuthenticationPrincipal;
import org.springframework.web.bind.annotation.DeleteMapping;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RestController;

import java.util.List;

/** 팔로우 / 언팔로우와 내 팔로잉 · 팔로워 목록 (로그인 회원). 팔로우는 여러 번 눌러도 결과가 같다. */
@RestController
@RequiredArgsConstructor
public class FollowController {

    private final FollowService followService;
    private final FollowRepository followRepository;

    /** 내가 팔로우하는 사람들 (마이페이지의 팔로잉 목록) */
    @GetMapping("/api/users/me/following")
    public List<FollowUser> following(@AuthenticationPrincipal AuthUser authUser) {
        return followRepository.following(authUser.id());
    }

    /** 나를 팔로우하는 사람들 (마이페이지의 팔로워 목록) */
    @GetMapping("/api/users/me/followers")
    public List<FollowUser> followers(@AuthenticationPrincipal AuthUser authUser) {
        return followRepository.followers(authUser.id());
    }

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
