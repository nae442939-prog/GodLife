package com.godlife.backend.chat;

import com.godlife.backend.auth.AuthUser;
import com.godlife.backend.chat.dto.ChatMessageResponse;
import com.godlife.backend.chat.dto.ChatSendRequest;
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

/** 챌린지 오픈채팅. 로그인 + 개설자/참가자만 (아니면 404). */
@RestController
@RequiredArgsConstructor
public class ChatController {

    private final ChatService chatService;

    /** ?after={마지막 id} 로 3초마다 새 메시지를 가져가고, ?before={첫 id} 로 이전 메시지를 더 불러온다. */
    @GetMapping("/api/challenges/{id}/messages")
    public List<ChatMessageResponse> list(@PathVariable Long id, @AuthenticationPrincipal AuthUser authUser,
                                          @RequestParam(required = false) Long after,
                                          @RequestParam(required = false) Long before) {
        return chatService.list(id, authUser.id(), after, before);
    }

    @PostMapping("/api/challenges/{id}/messages")
    public ResponseEntity<ChatMessageResponse> send(@PathVariable Long id, @AuthenticationPrincipal AuthUser authUser,
                                                    @Valid @RequestBody ChatSendRequest request) {
        return ResponseEntity.status(HttpStatus.CREATED).body(chatService.send(id, authUser.id(), request.content()));
    }
}
