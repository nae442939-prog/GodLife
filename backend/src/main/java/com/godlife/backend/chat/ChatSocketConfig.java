package com.godlife.backend.chat;

import lombok.RequiredArgsConstructor;
import org.springframework.context.annotation.Configuration;
import org.springframework.web.socket.config.annotation.EnableWebSocket;
import org.springframework.web.socket.config.annotation.WebSocketConfigurer;
import org.springframework.web.socket.config.annotation.WebSocketHandlerRegistry;

/** 오픈채팅 실시간 알림 소켓 등록. 같은 출처(프론트와 같은 주소)에서 온 연결만 받는다 (Spring 기본값). */
@Configuration
@EnableWebSocket
@RequiredArgsConstructor
public class ChatSocketConfig implements WebSocketConfigurer {

    private final ChatSocketHandler chatSocketHandler;

    @Override
    public void registerWebSocketHandlers(WebSocketHandlerRegistry registry) {
        registry.addHandler(chatSocketHandler, "/ws/chat");
    }
}
