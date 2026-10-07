package com.godlife.backend.chat;

import com.godlife.backend.auth.AuthUser;
import com.godlife.backend.auth.JwtProvider;
import com.godlife.backend.challenge.ChallengeService;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.stereotype.Component;
import org.springframework.transaction.event.TransactionalEventListener;
import org.springframework.web.socket.CloseStatus;
import org.springframework.web.socket.TextMessage;
import org.springframework.web.socket.WebSocketSession;
import org.springframework.web.socket.handler.ConcurrentWebSocketSessionDecorator;
import org.springframework.web.socket.handler.TextWebSocketHandler;
import tools.jackson.databind.ObjectMapper;

import java.io.IOException;
import java.util.Map;
import java.util.concurrent.ConcurrentHashMap;

/**
 * 오픈채팅 실시간 알림 소켓 (/ws/chat).
 * 이 통로로는 메시지 내용이 오가지 않는다 — 방에 새 메시지가 저장되면 "바뀌었다"는 신호만 보내고,
 * 화면은 그 신호를 받는 즉시 기존 REST(GET /messages?after=)로 새 메시지를 읽어 간다.
 * 그래서 읽기 권한 · 차단 · 강퇴 가림 같은 규칙은 REST 한 곳에만 있으면 되고, 소켓이 끊겨도 화면의 느린 폴링이 받쳐 준다.
 *
 * 들어오는 법: 연결한 뒤 첫 메시지로 {"token": 액세스 토큰, "challengeId": 방} 을 보낸다.
 * (브라우저 WebSocket 은 Authorization 헤더를 실을 수 없다) 토큰이 틀리거나 그 챌린지의 개설자 · 참가자가 아니면 바로 끊는다.
 * 통과하면 {"type":"ready"} 를 돌려주고, 그 뒤 클라이언트가 보내는 것은 모두 무시한다.
 */
@Slf4j
@Component
@RequiredArgsConstructor
public class ChatSocketHandler extends TextWebSocketHandler {

    private static final String ROOM = "challengeId";
    private static final TextMessage READY = new TextMessage("{\"type\":\"ready\"}");
    private static final TextMessage CHANGED = new TextMessage("{\"type\":\"changed\"}");
    /** 보내기가 이만큼(ms) 밀리거나 이만큼(bytes) 쌓인 느린 연결은 끊는다 */
    private static final int SEND_TIME_LIMIT = 5_000;
    private static final int SEND_BUFFER_LIMIT = 16 * 1024;

    private final JwtProvider jwtProvider;
    private final ChallengeService challengeService;
    private final ObjectMapper objectMapper;

    /** 방(챌린지) → 그 방을 보고 있는 연결들 (연결 id → 연결) */
    private final Map<Long, Map<String, WebSocketSession>> rooms = new ConcurrentHashMap<>();

    record Join(String token, Long challengeId) {
    }

    @Override
    protected void handleTextMessage(WebSocketSession session, TextMessage message) throws IOException {
        if (session.getAttributes().containsKey(ROOM)) {
            return;
        }
        try {
            Join join = objectMapper.readValue(message.getPayload(), Join.class);
            AuthUser user = jwtProvider.parse(join.token());
            challengeService.requireMember(join.challengeId(), user.id());

            WebSocketSession safe = new ConcurrentWebSocketSessionDecorator(session, SEND_TIME_LIMIT,
                    SEND_BUFFER_LIMIT);
            session.getAttributes().put(ROOM, join.challengeId());
            rooms.computeIfAbsent(join.challengeId(), k -> new ConcurrentHashMap<>()).put(session.getId(), safe);
            safe.sendMessage(READY);
        } catch (RuntimeException e) {
            // 토큰이 틀렸거나, 참가자가 아니거나, 형식이 이상한 요청
            session.close(CloseStatus.POLICY_VIOLATION);
        }
    }

    @Override
    public void afterConnectionClosed(WebSocketSession session, CloseStatus status) {
        Object room = session.getAttributes().get(ROOM);
        if (room instanceof Long challengeId) {
            rooms.computeIfPresent(challengeId, (k, sessions) -> {
                sessions.remove(session.getId());
                return sessions.isEmpty() ? null : sessions;
            });
        }
    }

    /** 메시지가 저장(커밋)된 뒤에 그 방을 보고 있는 연결들에 알린다 */
    @TransactionalEventListener(fallbackExecution = true)
    public void onChanged(ChatChangedEvent event) {
        Map<String, WebSocketSession> sessions = rooms.get(event.challengeId());
        if (sessions == null) {
            return;
        }
        for (WebSocketSession session : sessions.values()) {
            try {
                if (session.isOpen()) {
                    session.sendMessage(CHANGED);
                }
            } catch (IOException | RuntimeException e) {
                log.debug("오픈채팅 알림 전송 실패 (session={})", session.getId(), e);
                try {
                    session.close(CloseStatus.SESSION_NOT_RELIABLE);
                } catch (IOException | RuntimeException ignored) {
                    // 이미 끊긴 연결
                }
            }
        }
    }

    /** 지금 이 방을 보고 있는 연결 수 (테스트용) */
    int watching(Long challengeId) {
        Map<String, WebSocketSession> sessions = rooms.get(challengeId);
        return sessions == null ? 0 : sessions.size();
    }
}
