package com.godlife.backend.ranking;

/**
 * 개인 지표 기준.
 * TOTAL_SUCCESS = 지금까지 인증에 성공한 횟수(누적) / MONTH_VERIFY = 이번 달 인증에 성공한 횟수 / MAX_STREAK = 최장 연속 기록
 * SUCCESS_RATE = 누적 성공률 (끝난 챌린지에서 필요한 인증 중 채운 비율, 끝난 챌린지가 있는 사람만) — 프로필에만 쓰고 랭킹 기준은 아니다
 */
public enum RankingMetric {
    TOTAL_SUCCESS, MONTH_VERIFY, MAX_STREAK, SUCCESS_RATE;

    /** 랭킹 메뉴에서 고를 수 있는 기준인지 (전체 · 이번 달 · 최장 연속) */
    public boolean ranked() {
        return this != SUCCESS_RATE;
    }
}
