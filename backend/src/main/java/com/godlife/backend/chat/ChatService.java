package com.godlife.backend.chat;

import com.godlife.backend.challenge.ChallengeService;
import com.godlife.backend.chat.dto.ChatMessageResponse;
import com.godlife.backend.chat.dto.ChatMessageRow;
import com.godlife.backend.chat.dto.ChatSendRequest;
import com.godlife.backend.common.error.BusinessException;
import com.godlife.backend.common.error.ErrorCode;
import com.godlife.backend.common.ratelimit.RequestThrottle;
import com.godlife.backend.common.upload.ImageStore;
import lombok.RequiredArgsConstructor;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.context.ApplicationEventPublisher;
import org.springframework.data.domain.PageRequest;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;
import org.springframework.web.multipart.MultipartFile;

import java.nio.file.Files;
import java.nio.file.Path;
import java.time.Duration;
import java.util.List;

/**
 * 챌린지 오픈채팅. 개설자와 참가자만 읽고 쓴다.
 * 읽기 · 쓰기는 모두 이 서비스(REST)로 하고, 메시지가 저장되면 ChatChangedEvent 를 내서
 * 그 방을 보고 있는 화면에 WebSocket 으로 "새 메시지가 있다"만 알린다 (ChatSocketHandler). 내용은 화면이 다시 읽어 간다.
 */
@Service
@RequiredArgsConstructor
public class ChatService {

    public static final int PAGE_SIZE = 50;
    private static final Duration SEND_WINDOW = Duration.ofSeconds(10);

    private final ChatMessageRepository messageRepository;
    private final ChallengeService challengeService;
    private final RequestThrottle throttle;
    private final ImageStore imageStore;
    private final ApplicationEventPublisher events;

    /** 한 사람이 10초 동안 보낼 수 있는 메시지 수 (도배 방지) */
    @Value("${app.chat.send-limit:5}")
    private int sendLimit;

    /**
     * 메시지 읽기. 모두 오래된 순으로 돌려준다. 내가 차단한 사람의 메시지는 빠진다.
     * - afterId: 폴링. 그보다 새 메시지
     * - beforeId: 위로 스크롤. 그보다 오래된 메시지
     * - 둘 다 없으면: 가장 최근 메시지
     */
    @Transactional(readOnly = true)
    public List<ChatMessageResponse> list(Long challengeId, Long userId, Long afterId, Long beforeId) {
        challengeService.requireMember(challengeId, userId);
        PageRequest limit = PageRequest.of(0, PAGE_SIZE);
        List<ChatMessageRow> rows;
        if (afterId != null) {
            rows = messageRepository.findAfter(challengeId, userId, afterId, limit);
        } else {
            long before = beforeId != null ? beforeId : Long.MAX_VALUE;
            rows = messageRepository.findBefore(challengeId, userId, before, limit).reversed();
        }
        return rows.stream().map(row -> ChatMessageResponse.of(row, userId)).toList();
    }

    @Transactional
    public ChatMessageResponse send(Long challengeId, Long userId, String content) {
        challengeService.requireMember(challengeId, userId);
        throttle.check("chat:" + userId, sendLimit, SEND_WINDOW);
        return saveAndRead(ChatMessage.of(challengeId, userId, content.strip()), userId);
    }

    /**
     * 사진 보내기 (글은 붙여도 되고 없어도 된다). 사진은 ImageStore 가 검사·정리해서 저장한다.
     * DB 저장이 실패하면 방금 저장한 파일을 지운다.
     */
    @Transactional
    public ChatMessageResponse sendImage(Long challengeId, Long userId, String caption, MultipartFile file) {
        challengeService.requireMember(challengeId, userId);
        if (caption != null && caption.strip().length() > ChatSendRequest.MAX_LENGTH) {
            throw new BusinessException(ErrorCode.VALIDATION_ERROR, "메시지는 500자까지 보낼 수 있어요.");
        }
        throttle.check("chat:" + userId, sendLimit, SEND_WINDOW);
        String key = imageStore.storeChatImage(challengeId, file);
        try {
            return saveAndRead(ChatMessage.image(challengeId, userId, caption, key), userId);
        } catch (RuntimeException e) {
            imageStore.delete(key);
            throw e;
        }
    }

    /**
     * 사진 파일. 목록과 같은 규칙으로 보이는 메시지일 때만 준다
     * (참가자만 · 내가 차단한 사람 것은 안 줌 · 강퇴된 사람 것은 가려져서 안 줌).
     */
    @Transactional(readOnly = true)
    public Path imageFile(Long challengeId, Long userId, Long messageId) {
        challengeService.requireMember(challengeId, userId);
        String key = messageRepository.findAfter(challengeId, userId, messageId - 1, PageRequest.of(0, 1)).stream()
                .filter(row -> row.id().equals(messageId) && row.imageKey() != null && !row.senderKicked())
                .map(ChatMessageRow::imageKey)
                .findFirst()
                .orElseThrow(() -> new BusinessException(ErrorCode.MESSAGE_NOT_FOUND));
        Path path = imageStore.resolve(key);
        if (!Files.isReadable(path)) {
            throw new BusinessException(ErrorCode.MESSAGE_NOT_FOUND);
        }
        return path;
    }

    /** 강퇴·공지 같은 안내 메시지. 권한 확인은 부르는 쪽(방장 기능)이 한다. */
    @Transactional
    public void postSystem(Long challengeId, Long hostId, String content) {
        messageRepository.save(ChatMessage.system(challengeId, hostId, content));
        events.publishEvent(new ChatChangedEvent(challengeId));
    }

    /** 닉네임·보낸 시각(DB 기본값)을 같이 돌려주려고 방금 저장한 한 건을 다시 읽는다. */
    private ChatMessageResponse saveAndRead(ChatMessage message, Long viewerId) {
        ChatMessage saved = messageRepository.saveAndFlush(message);
        events.publishEvent(new ChatChangedEvent(saved.getChallengeId()));
        return messageRepository.findAfter(saved.getChallengeId(), viewerId, saved.getId() - 1, PageRequest.of(0, 1))
                .stream()
                .map(row -> ChatMessageResponse.of(row, viewerId))
                .findFirst()
                .orElseThrow();
    }
}
