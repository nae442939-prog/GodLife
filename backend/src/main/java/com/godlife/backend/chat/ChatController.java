package com.godlife.backend.chat;

import com.godlife.backend.auth.AuthUser;
import com.godlife.backend.chat.dto.ChatMessageResponse;
import com.godlife.backend.chat.dto.ChatSendRequest;
import jakarta.validation.Valid;
import lombok.RequiredArgsConstructor;
import org.springframework.core.io.FileSystemResource;
import org.springframework.core.io.Resource;
import org.springframework.http.CacheControl;
import org.springframework.http.HttpStatus;
import org.springframework.http.MediaType;
import org.springframework.http.ResponseEntity;
import org.springframework.security.core.annotation.AuthenticationPrincipal;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestParam;
import org.springframework.web.bind.annotation.RequestPart;
import org.springframework.web.bind.annotation.RestController;
import org.springframework.web.multipart.MultipartFile;

import java.nio.file.Path;
import java.time.Duration;
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

    /** 사진 보내기 (multipart: file = JPG/PNG 5MB 이하, content = 붙일 글(선택)) */
    @PostMapping(value = "/api/challenges/{id}/messages/image", consumes = MediaType.MULTIPART_FORM_DATA_VALUE)
    public ResponseEntity<ChatMessageResponse> sendImage(@PathVariable Long id,
                                                         @AuthenticationPrincipal AuthUser authUser,
                                                         @RequestPart("file") MultipartFile file,
                                                         @RequestParam(required = false) String content) {
        return ResponseEntity.status(HttpStatus.CREATED)
                .body(chatService.sendImage(id, authUser.id(), content, file));
    }

    /** 사진 받기. 참가자만 (이미지 태그로는 토큰을 못 보내서 프론트가 fetch 로 받아 보여 준다). */
    @GetMapping("/api/challenges/{id}/messages/{messageId}/image")
    public ResponseEntity<Resource> image(@PathVariable Long id, @PathVariable Long messageId,
                                          @AuthenticationPrincipal AuthUser authUser) {
        Path path = chatService.imageFile(id, authUser.id(), messageId);
        return ResponseEntity.ok()
                .contentType(MediaType.IMAGE_JPEG)
                .cacheControl(CacheControl.maxAge(Duration.ofDays(1)).cachePrivate())
                .body(new FileSystemResource(path));
    }

    @PostMapping("/api/challenges/{id}/messages")
    public ResponseEntity<ChatMessageResponse> send(@PathVariable Long id, @AuthenticationPrincipal AuthUser authUser,
                                                    @Valid @RequestBody ChatSendRequest request) {
        return ResponseEntity.status(HttpStatus.CREATED).body(chatService.send(id, authUser.id(), request.content()));
    }
}
