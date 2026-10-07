package com.godlife.backend.chat;

/** OPEN = 쌓이는 중 / RESOLVED = 방장이 강퇴했거나 넘김 (누적 수에서 빠진다) */
public enum ReportStatus {
    OPEN, RESOLVED
}
