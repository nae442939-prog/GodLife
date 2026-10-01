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
     * @param canSend        지금 보낼 수 있는지
     * @param reason         보낼 수 없는 이유 — BLOCKED / REQUEST_LIMIT(수락 전 3개를 다 보냄) / NOT_AVAILABLE(거절됨)
     * @param direct         맞팔로우·같은 챌린지·수락된 요청이라 바로 대화하는 사이인지 (false 면 메시지 요청)
     * @param mutual         서로 팔로우하는지
     * @param sharedChallenge 같은 챌린지 참가자인지
     * @param request        메시지 요청 상태 — SENT(내가 보내고 기다림) / RECEIVED(받음, 수락·거절 가능) / null
     * @param requestLeft    수락 전에 더 보낼 수 있는 개수 (내가 보낸 요청일 때)
     */
    public record RoomResponse(Partner partner, boolean canSend, String reason, boolean direct, boolean mutual,
                               boolean sharedChallenge, String request, int requestLeft) {
    }

    public record MessageResponse(Long id, String content, LocalDateTime createdAt, boolean mine, boolean read) {
    }

    /**
     * 대화 목록 한 줄: 상대 + 마지막 메시지 + 안 읽은 수.
     * @param request RECEIVED(받은 메시지 요청 → '요청' 탭) / SENT(보내고 기다림) / null(일반 대화)
     */
    public record ConversationResponse(Partner partner, String lastContent, LocalDateTime lastAt, boolean lastMine,
                                       long unread, String request) {
    }

    public record SendRequest(@NotBlank(message = "메시지를 입력해 주세요.")
                              @Size(max = 500, message = "메시지는 500자까지 보낼 수 있어요.") String content) {
    }
}
