package com.godlife.backend.challenge;

import com.godlife.backend.auth.AuthUser;
import com.godlife.backend.challenge.ChallengeService.SortOption;
import com.godlife.backend.challenge.dto.CategoryResponse;
import com.godlife.backend.challenge.dto.ChallengeCreateRequest;
import com.godlife.backend.challenge.dto.ChallengeDetailResponse;
import com.godlife.backend.challenge.dto.ChallengeSummaryResponse;
import com.godlife.backend.challenge.dto.PageResponse;
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

    @DeleteMapping("/api/challenges/{id}/participants/me")
    public ChallengeDetailResponse leave(@PathVariable Long id, @AuthenticationPrincipal AuthUser authUser) {
        challengeService.leave(id, authUser.id());
        return challengeService.detail(id, authUser.id());
    }

    private static SortOption parseSort(String sort) {
        try {
            return SortOption.valueOf(sort.toUpperCase(Locale.ROOT));
        } catch (IllegalArgumentException e) {
            return SortOption.POPULAR;
        }
    }
}
