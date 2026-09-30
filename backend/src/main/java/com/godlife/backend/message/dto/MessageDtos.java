package com.godlife.backend.message.dto;

import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.Size;

import java.time.LocalDateTime;

/** 1:1 메시지 요청·응답 */
public final class MessageDtos {

    private MessageDtos() {
    }

    /** 대화 상대 한 명의 짧은 정보 */
    public record Partner(Long id, String nickname, String profileImageUrl) {
    }

    /**
     * 대화방 머리: 상대 + 지금 보낼 수 있는지.
     * @param canSend 맞팔로우이고 차단 사이가 아닐 때 true
     * @param reason  보낼 수 없는 이유 — NOT_FOLLOWING(내가 안 함) / NOT_FOLLOWED_BACK(상대가 안 함) / BLOCKED, 보낼 수 있으면 null
     */
    public record RoomResponse(Partner partner, boolean canSend, String reason) {
    }

    public record MessageResponse(Long id, String content, LocalDateTime createdAt, boolean mine, boolean read) {
    }

    /** 대화 목록 한 줄: 상대 + 마지막 메시지 + 안 읽은 수 */
    public record ConversationResponse(Partner partner, String lastContent, LocalDateTime lastAt, boolean lastMine,
                                       long unread) {
    }

    public record SendRequest(@NotBlank(message = "메시지를 입력해 주세요.")
                              @Size(max = 500, message = "메시지는 500자까지 보낼 수 있어요.") String content) {
    }
}
