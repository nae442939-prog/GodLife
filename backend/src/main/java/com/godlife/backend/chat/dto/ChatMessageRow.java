package com.godlife.backend.chat.dto;

import com.godlife.backend.chat.ChatMessageType;

import java.time.LocalDateTime;

/** 메시지 + 보낸 사람 공개 프로필 + 보낸 사람이 강퇴됐는지 (DB 에서 한 번에 읽는 값) */
public record ChatMessageRow(Long id, ChatMessageType type, Long senderId, String senderNickname,
                             String senderProfileImageUrl, String content, LocalDateTime createdAt,
                             boolean senderKicked) {
}
