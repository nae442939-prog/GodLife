package com.godlife.backend.challenge.dto;

/** 참가자 목록에는 공개 프로필(닉네임, 사진)만 보낸다. userId 는 방장의 '내보내기'와 차단에 쓴다. */
public record ParticipantResponse(Long userId, String nickname, String profileImageUrl) {
}
