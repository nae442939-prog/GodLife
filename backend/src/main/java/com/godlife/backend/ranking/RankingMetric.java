package com.godlife.backend.ranking;

/**
 * 전체(개인) 랭킹 기준.
 * MONTH_VERIFY = 이번 달 인증 수 / MAX_STREAK = 최장 연속 기록 / MONTH_REWARD = 이번 달 받은 보상 포인트
 * SUCCESS_RATE = 누적 성공률 (끝난 챌린지에서 필요한 인증 중 채운 비율, 끝난 챌린지가 있는 사람만)
 */
public enum RankingMetric {
    MONTH_VERIFY, MAX_STREAK, MONTH_REWARD, SUCCESS_RATE
}
