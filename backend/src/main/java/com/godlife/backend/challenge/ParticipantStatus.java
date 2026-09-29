package com.godlife.backend.challenge;

/** LEFT = 시작 전에 참여를 취소함. 다시 참여하면 ACTIVE 로 되돌린다. */
public enum ParticipantStatus {
    ACTIVE, COMPLETED, FAILED, LEFT
}
