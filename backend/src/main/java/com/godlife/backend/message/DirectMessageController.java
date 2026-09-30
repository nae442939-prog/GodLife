package com.godlife.backend.message;

import com.godlife.backend.auth.AuthUser;
import com.godlife.backend.message.dto.MessageDtos.ConversationResponse;
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

/** 1:1 메시지 (로그인 회원, 맞팔로우끼리만 보낼 수 있음) */
@RestController
@RequiredArgsConstructor
public class DirectMessageController {

    private final DirectMessageService messageService;

    /** 대화 목록 */
    @GetMapping("/api/messages")
    public List<ConversationResponse> conversations(@AuthenticationPrincipal AuthUser authUser) {
        return messageService.conversations(authUser.id());
    }

    /** 안 읽은 메시지 수 (헤더) */
    @GetMapping("/api/messages/unread-count")
    public Map<String, Long> unread(@AuthenticationPrincipal AuthUser authUser) {
        return Map.of("count", messageService.unreadCount(authUser.id()));
    }

    /** 대화방 머리: 상대 + 지금 보낼 수 있는지(맞팔로우) */
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
}
