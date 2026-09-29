package com.godlife.backend.chat;

import com.godlife.backend.challenge.ChallengeService;
import com.godlife.backend.chat.dto.ChatMessageResponse;
import com.godlife.backend.chat.dto.ChatMessageRow;
import com.godlife.backend.common.ratelimit.RequestThrottle;
import lombok.RequiredArgsConstructor;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.data.domain.PageRequest;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.time.Duration;
import java.util.List;

/**
 * 챌린지 오픈채팅 (1단계: 폴링). 개설자와 참가자만 읽고 쓴다.
 * 나중에 WebSocket(STOMP)으로 바꿔도 저장·권한은 이 서비스를 그대로 쓰고, 저장 후 방송만 더하면 된다.
 */
@Service
@RequiredArgsConstructor
public class ChatService {

    public static final int PAGE_SIZE = 50;
    private static final Duration SEND_WINDOW = Duration.ofSeconds(10);

    private final ChatMessageRepository messageRepository;
    private final ChallengeService challengeService;
    private final RequestThrottle throttle;

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

    /** 강퇴·공지 같은 안내 메시지. 권한 확인은 부르는 쪽(방장 기능)이 한다. */
    @Transactional
    public void postSystem(Long challengeId, Long hostId, String content) {
        messageRepository.save(ChatMessage.system(challengeId, hostId, content));
    }

    /** 닉네임·보낸 시각(DB 기본값)을 같이 돌려주려고 방금 저장한 한 건을 다시 읽는다. */
    private ChatMessageResponse saveAndRead(ChatMessage message, Long viewerId) {
        ChatMessage saved = messageRepository.saveAndFlush(message);
        return messageRepository.findAfter(saved.getChallengeId(), viewerId, saved.getId() - 1, PageRequest.of(0, 1))
                .stream()
                .map(row -> ChatMessageResponse.of(row, viewerId))
                .findFirst()
                .orElseThrow();
    }
}
