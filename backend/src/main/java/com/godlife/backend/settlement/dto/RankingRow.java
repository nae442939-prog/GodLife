package com.godlife.backend.settlement.dto;

/** 챌린지 랭킹 한 줄 (순위·내 줄 표시는 서비스에서 붙인다) */
public record RankingRow(Long userId, String nickname, String profileImageUrl, int successDays, int maxStreak,
                         int currentStreak) {
}
