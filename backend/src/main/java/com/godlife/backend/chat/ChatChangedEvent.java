package com.godlife.backend.chat;

/** 이 챌린지 오픈채팅에 새 메시지(글 · 사진 · 안내)가 저장됐다 */
public record ChatChangedEvent(Long challengeId) {
}
