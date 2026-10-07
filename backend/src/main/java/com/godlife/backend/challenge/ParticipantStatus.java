package com.godlife.backend.challenge;

/**
 * COMPLETED / FAILED = 챌린지가 끝날 때(자정 스케줄러) 필요한 인증 횟수를 채웠는지로 판정.
 * GAVE_UP = 진행 중 스스로 포기함. 실패로 치고 챌린지에서 나간다(채팅·인증 사진·비공개 상세 접근 불가).
 * LEFT = 시작 전에 참여를 취소함. 다시 참여하면 ACTIVE 로 되돌린다.
 * KICKED = 방장이 내보냄. 다시 참여할 수 없고(초대 링크 포함) 채팅·상세도 볼 수 없다.
 */
public enum ParticipantStatus {
    ACTIVE, COMPLETED, FAILED, GAVE_UP, LEFT, KICKED;

    /** 챌린지 멤버로 보는 상태 (채팅·비공개 상세·초대 링크·인증 사진) */
    public boolean isMember() {
        return this != LEFT && this != KICKED && this != GAVE_UP;
    }

    /** 챌린지에서 나간 상태 (목록·인원수·채팅·인증 사진에서 빠진다) */
    public boolean isGone() {
        return !isMember();
    }
}
