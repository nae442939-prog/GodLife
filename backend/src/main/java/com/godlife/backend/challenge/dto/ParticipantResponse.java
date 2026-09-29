package com.godlife.backend.challenge.dto;

/** 참가자 목록에는 공개 프로필(닉네임, 사진)만 보낸다. */
public record ParticipantResponse(String nickname, String profileImageUrl) {
}
