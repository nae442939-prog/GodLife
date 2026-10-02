package com.godlife.backend.message;

import com.godlife.backend.auth.AuthUser;
import com.godlife.backend.message.dto.MessageDtos.ConversationResponse;
import com.godlife.backend.message.dto.MessageDtos.InviteRequest;
import com.godlife.backend.message.dto.MessageDtos.MessageResponse;
import com.godlife.backend.message.dto.MessageDtos.RoomResponse;
import com.godlife.backend.message.dto.MessageDtos.SendRequest;
import jakarta.validation.Valid;
import lombok.RequiredArgsConstructor;
import org.springframework.http.HttpStatus;
import org.springframework.http.ResponseEntity;
import org.springframework.security.core.annotation.AuthenticationPrincipal;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestParam;
import org.springframework.web.bind.annotation.RestController;

import java.util.List;
import java.util.Map;

/** 1:1 메시지 (로그인 회원). 맞팔로우·같은 챌린지는 바로, 그 밖은 메시지 요청 */
@RestController
@RequiredArgsConstructor
public class DirectMessageController {

    private final DirectMessageService messageService;

    /** 대화 목록 */
    @GetMapping("/api/messages")
    public List<ConversationResponse> conversations(@AuthenticationPrincipal AuthUser authUser) {
        return messageService.conversations(authUser.id());
    }

    /** 안 읽은 메시지 수와 받은 메시지 요청 수 (헤더) */
    @GetMapping("/api/messages/unread-count")
    public Map<String, Long> unread(@AuthenticationPrincipal AuthUser authUser) {
        return messageService.unreadCount(authUser.id());
    }

    /** 받은 메시지 요청 수락 */
    @PostMapping("/api/messages/{userId}/accept")
    public ResponseEntity<Void> accept(@PathVariable Long userId, @AuthenticationPrincipal AuthUser authUser) {
        messageService.accept(authUser.id(), userId);
        return ResponseEntity.noContent().build();
    }

    /** 받은 메시지 요청 거절 */
    @PostMapping("/api/messages/{userId}/decline")
    public ResponseEntity<Void> decline(@PathVariable Long userId, @AuthenticationPrincipal AuthUser authUser) {
        messageService.decline(authUser.id(), userId);
        return ResponseEntity.noContent().build();
    }

    /** 대화방 머리: 상대 + 지금 보낼 수 있는지 + 메시지 요청 상태 */
    @GetMapping("/api/messages/{userId}/room")
    public RoomResponse room(@PathVariable Long userId, @AuthenticationPrincipal AuthUser authUser) {
        return messageService.room(authUser.id(), userId);
    }

    /** 메시지 (?after= 새 메시지 / ?before= 이전 메시지) */
    @GetMapping("/api/messages/{userId}")
    public List<MessageResponse> list(@PathVariable Long userId, @AuthenticationPrincipal AuthUser authUser,
                                      @RequestParam(required = false) Long after,
                                      @RequestParam(required = false) Long before) {
        return messageService.list(authUser.id(), userId, after, before);
    }

    @PostMapping("/api/messages/{userId}")
    public ResponseEntity<MessageResponse> send(@PathVariable Long userId, @AuthenticationPrincipal AuthUser authUser,
                                                @Valid @RequestBody SendRequest request) {
        return ResponseEntity.status(HttpStatus.CREATED)
                .body(messageService.send(authUser.id(), userId, request.content()));
    }

    /** 대화 상대를 챌린지에 초대한다 (대화방에 초대 카드가 올라간다) */
    @PostMapping("/api/messages/{userId}/challenge-invite")
    public ResponseEntity<MessageResponse> invite(@PathVariable Long userId, @AuthenticationPrincipal AuthUser authUser,
                                                  @Valid @RequestBody InviteRequest request) {
        return ResponseEntity.status(HttpStatus.CREATED)
                .body(messageService.invite(authUser.id(), userId, request.challengeId()));
    }
}
