package com.godlife.backend.verification;

/** 지금 인증할 수 있는지. 화면의 [오늘 인증하기] 버튼 모양과 제출 시 거절 사유가 같은 판단을 쓴다. */
public enum VerifyState {
    /** 지금 올릴 수 있음 */
    OPEN,
    /** 오늘 이미 인증함 (다시 올리기 없음) */
    DONE_TODAY,
    /** 주 N회 챌린지에서 이번 주 횟수를 다 채움 */
    WEEK_DONE,
    /** 인증 가능 시간대 밖 */
    TIME_CLOSED,
    NOT_STARTED,
    ENDED
}
