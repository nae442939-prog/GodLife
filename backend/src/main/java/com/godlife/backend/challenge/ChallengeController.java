package com.godlife.backend.challenge;

import com.godlife.backend.auth.AuthUser;
import com.godlife.backend.challenge.ChallengeService.SortOption;
import com.godlife.backend.challenge.dto.CategoryResponse;
import com.godlife.backend.challenge.dto.ChallengeCreateRequest;
import com.godlife.backend.challenge.dto.ChallengeDetailResponse;
import com.godlife.backend.challenge.dto.ChallengeSummaryResponse;
import com.godlife.backend.challenge.dto.PageResponse;
import com.godlife.backend.chat.ChatService;
import jakarta.servlet.http.HttpServletRequest;
import jakarta.validation.Valid;
import lombok.RequiredArgsConstructor;
import org.springframework.http.HttpStatus;
import org.springframework.http.ResponseEntity;
import org.springframework.security.core.annotation.AuthenticationPrincipal;
import org.springframework.web.bind.annotation.DeleteMapping;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestParam;
import org.springframework.web.bind.annotation.RestController;

import java.util.List;
import java.util.Locale;

/** 목록/상세/카테고리는 비로그인도 볼 수 있다. 개설/참여/취소는 로그인 + 휴대폰 인증 회원만. */
@RestController
@RequiredArgsConstructor
public class ChallengeController {

    private final ChallengeService challengeService;
    private final ChatService chatService;

    @GetMapping("/api/categories")
    public List<CategoryResponse> categories() {
        return challengeService.categories().stream().map(CategoryResponse::from).toList();
    }

    @GetMapping("/api/challenges")
    public PageResponse<ChallengeSummaryResponse> search(@RequestParam(required = false) Integer categoryId,
                                                         @RequestParam(required = false) ChallengeMode mode,
                                                         @RequestParam(required = false) String q,
                                                         @RequestParam(defaultValue = "popular") String sort,
                                                         @RequestParam(defaultValue = "0") int page) {
        return challengeService.search(categoryId, mode, q, parseSort(sort), page);
    }

    @GetMapping("/api/challenges/{id}")
    public ChallengeDetailResponse detail(@PathVariable Long id, @AuthenticationPrincipal AuthUser authUser) {
        return challengeService.detail(id, authUser == null ? null : authUser.id());
    }

    @PostMapping("/api/challenges")
    public ResponseEntity<ChallengeDetailResponse> create(@AuthenticationPrincipal AuthUser authUser,
                                                          @Valid @RequestBody ChallengeCreateRequest request) {
        Challenge created = challengeService.create(authUser.id(), request);
        return ResponseEntity.status(HttpStatus.CREATED).body(challengeService.detail(created.getId(), authUser.id()));
    }

    @PostMapping("/api/challenges/{id}/participants")
    public ChallengeDetailResponse join(@PathVariable Long id, @AuthenticationPrincipal AuthUser authUser) {
        challengeService.join(id, authUser.id());
        return challengeService.detail(id, authUser.id());
    }

    /** 초대 링크 미리보기. 비로그인도 볼 수 있다(참여할 때 로그인). */
    @GetMapping("/api/challenges/invite/{code}")
    public ChallengeDetailResponse invite(@PathVariable String code, @AuthenticationPrincipal AuthUser authUser,
                                          HttpServletRequest http) {
        return challengeService.detailByInvite(code, authUser == null ? null : authUser.id(), http.getRemoteAddr());
    }

    @PostMapping("/api/challenges/invite/{code}/participants")
    public ChallengeDetailResponse joinByInvite(@PathVariable String code, @AuthenticationPrincipal AuthUser authUser,
                                                HttpServletRequest http) {
        Long challengeId = challengeService.joinByInvite(code, authUser.id(), http.getRemoteAddr());
        return challengeService.detail(challengeId, authUser.id());
    }

    /** 개설자만. 초대 코드를 새로 만들어 이전 링크를 막는다. */
    @PostMapping("/api/challenges/{id}/invite-code")
    public ChallengeDetailResponse regenerateInviteCode(@PathVariable Long id,
                                                        @AuthenticationPrincipal AuthUser authUser) {
        challengeService.regenerateInviteCode(id, authUser.id());
        return challengeService.detail(id, authUser.id());
    }

    /** 개설자만, 시작일 전날까지. */
    @DeleteMapping("/api/challenges/{id}")
    public ResponseEntity<Void> delete(@PathVariable Long id, @AuthenticationPrincipal AuthUser authUser) {
        challengeService.delete(id, authUser.id());
        return ResponseEntity.noContent().build();
    }

    @DeleteMapping("/api/challenges/{id}/participants/me")
    public ChallengeDetailResponse leave(@PathVariable Long id, @AuthenticationPrincipal AuthUser authUser) {
        challengeService.leave(id, authUser.id());
        return challengeService.detail(id, authUser.id());
    }

    /**
     * 진행 중 포기. 실패로 치고 챌린지에서 나간다. 채팅방에 안내가 남는다.
     * 나간 뒤에는 비공개 챌린지 상세를 볼 수 없으므로 상세 대신 204 를 돌려준다.
     */
    @PostMapping("/api/challenges/{id}/participants/me/give-up")
    public ResponseEntity<Void> giveUp(@PathVariable Long id, @AuthenticationPrincipal AuthUser authUser) {
        ChallengeService.GaveUp gaveUp = challengeService.giveUp(id, authUser.id());
        chatService.postSystem(id, gaveUp.hostId(), gaveUp.nickname() + "님이 챌린지를 포기하고 나갔어요.");
        return ResponseEntity.noContent().build();
    }

    private static SortOption parseSort(String sort) {
        try {
            return SortOption.valueOf(sort.toUpperCase(Locale.ROOT));
        } catch (IllegalArgumentException e) {
            return SortOption.POPULAR;
        }
    }
}
