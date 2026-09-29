package com.godlife.backend.challenge;

/**
 * LEFT = 시작 전에 참여를 취소함. 다시 참여하면 ACTIVE 로 되돌린다.
 * KICKED = 방장이 내보냄. 다시 참여할 수 없고(초대 링크 포함) 채팅·상세도 볼 수 없다.
 */
public enum ParticipantStatus {
    ACTIVE, COMPLETED, FAILED, LEFT, KICKED;

    /** 챌린지 멤버로 보는 상태 (채팅·비공개 상세·초대 링크) */
    public boolean isMember() {
        return this != LEFT && this != KICKED;
    }
}
