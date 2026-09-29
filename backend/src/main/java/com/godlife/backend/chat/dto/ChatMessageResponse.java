package com.godlife.backend.chat.dto;

import com.godlife.backend.chat.ChatMessageType;

import java.time.LocalDateTime;

/**
 * 채팅 메시지. 보낸 사람은 공개 프로필(닉네임, 사진)만 준다.
 *
 * @param senderId 신고·차단·내보내기 메뉴에 쓴다
 * @param mine     로그인한 사람이 보낸 메시지인지 (오른쪽 말풍선)
 * @param hidden   방장이 내보낸 참가자의 메시지라 가림. 이때 content 는 null
 */
public record ChatMessageResponse(Long id, ChatMessageType type, Long senderId, String senderNickname,
                                  String senderProfileImageUrl, boolean mine, boolean hidden, String content,
                                  LocalDateTime createdAt) {

    public static ChatMessageResponse of(ChatMessageRow row, Long viewerId) {
        boolean hidden = row.type() == ChatMessageType.USER && row.senderKicked();
        return new ChatMessageResponse(row.id(), row.type(), row.senderId(), row.senderNickname(),
                row.senderProfileImageUrl(), row.senderId().equals(viewerId), hidden,
                hidden ? null : row.content(), row.createdAt());
    }
}
