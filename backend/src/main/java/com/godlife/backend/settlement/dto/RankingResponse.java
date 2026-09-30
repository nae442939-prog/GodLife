package com.godlife.backend.settlement.dto;

/** 챌린지 랭킹 한 줄. 같은 기록이면 같은 순위. */
public record RankingResponse(int rank, String nickname, String profileImageUrl, int successDays, int maxStreak,
                              int currentStreak, boolean mine) {
}
