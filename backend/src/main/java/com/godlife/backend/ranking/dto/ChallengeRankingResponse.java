package com.godlife.backend.ranking.dto;

import com.godlife.backend.challenge.ChallengeMode;
import com.godlife.backend.challenge.dto.CategoryResponse;

/**
 * 챌린지(팀) 랭킹 한 줄: 진행 중인 공개 챌린지를 참가자 평균 달성률로.
 * @param rate      끝난 기간(어제까지) 동안 필요한 인증 중 채운 비율 (0~100, 소수 첫째 자리)
 * @param dayNumber 오늘이 며칠째인지
 */
public record ChallengeRankingResponse(int rank, Long id, String title, CategoryResponse category, ChallengeMode mode,
                                       int participantCount, double rate, long dayNumber, long totalDays) {
}
