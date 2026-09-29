package com.godlife.backend.chat;

import com.godlife.backend.auth.AuthUser;
import com.godlife.backend.challenge.ChallengeService;
import com.godlife.backend.challenge.dto.ChallengeDetailResponse;
import com.godlife.backend.chat.dto.ChatReportRequest;
import com.godlife.backend.chat.dto.NoticeRequest;
import com.godlife.backend.chat.dto.ReportAlertResponse;
import jakarta.validation.Valid;
import lombok.RequiredArgsConstructor;
import org.springframework.http.HttpStatus;
import org.springframework.http.ResponseEntity;
import org.springframework.security.core.annotation.AuthenticationPrincipal;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.PutMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RestController;

import java.util.List;

/** 오픈채팅 관리. 공지·내보내기·신고 알림은 방장만, 신고는 참가자 누구나. */
@RestController
@RequiredArgsConstructor
public class ChatModerationController {

    private final ChatModerationService moderationService;
    private final ChallengeService challengeService;

    @PutMapping("/api/challenges/{id}/notice")
    public ChallengeDetailResponse changeNotice(@PathVariable Long id, @AuthenticationPrincipal AuthUser authUser,
                                                @Valid @RequestBody NoticeRequest request) {
        moderationService.changeNotice(id, authUser.id(), request.content());
        return challengeService.detail(id, authUser.id());
    }

    @PostMapping("/api/challenges/{id}/participants/{userId}/kick")
    public ChallengeDetailResponse kick(@PathVariable Long id, @PathVariable Long userId,
                                        @AuthenticationPrincipal AuthUser authUser) {
        moderationService.kick(id, authUser.id(), userId);
        return challengeService.detail(id, authUser.id());
    }

    @PostMapping("/api/challenges/{id}/messages/{messageId}/reports")
    public ResponseEntity<Void> report(@PathVariable Long id, @PathVariable Long messageId,
                                       @AuthenticationPrincipal AuthUser authUser,
                                       @Valid @RequestBody ChatReportRequest request) {
        moderationService.report(id, authUser.id(), messageId, request);
        return ResponseEntity.status(HttpStatus.CREATED).build();
    }

    @GetMapping("/api/challenges/{id}/report-alerts")
    public List<ReportAlertResponse> alerts(@PathVariable Long id, @AuthenticationPrincipal AuthUser authUser) {
        return moderationService.alerts(id, authUser.id());
    }

    @PostMapping("/api/challenges/{id}/report-alerts/{userId}/dismiss")
    public ResponseEntity<Void> dismiss(@PathVariable Long id, @PathVariable Long userId,
                                        @AuthenticationPrincipal AuthUser authUser) {
        moderationService.dismiss(id, authUser.id(), userId);
        return ResponseEntity.noContent().build();
    }
}
