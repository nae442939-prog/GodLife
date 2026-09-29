package com.godlife.backend.chat.dto;

import java.time.LocalDateTime;

/**
 * 채팅 메시지. 보낸 사람은 공개 프로필(닉네임, 사진)만 준다.
 *
 * @param mine 로그인한 사람이 보낸 메시지인지 (오른쪽 말풍선)
 */
public record ChatMessageResponse(Long id, String senderNickname, String senderProfileImageUrl, boolean mine,
                                  String content, LocalDateTime createdAt) {

    public static ChatMessageResponse of(ChatMessageRow row, Long viewerId) {
        return new ChatMessageResponse(row.id(), row.senderNickname(), row.senderProfileImageUrl(),
                row.senderId().equals(viewerId), row.content(), row.createdAt());
    }
}
