package com.godlife.backend.verification.dto;

import com.godlife.backend.verification.VerifyState;

import java.time.LocalDate;

/**
 * 챌린지 상세에서 보여 줄 내 인증 현황.
 * @param today       서버 기준 오늘 (화면의 진행률을 서버 날짜로 맞춘다)
 * @param successDays 지금까지 인증한 횟수
 * @param targetCount 끝까지 성공하려면 필요한 인증 횟수
 * @param weekCount   주 N회 챌린지의 이번 주 인증 횟수 (매일 챌린지거나 진행 중이 아니면 null)
 */
public record MyVerificationResponse(VerifyState state, LocalDate today, int successDays, int targetCount,
                                     int currentStreak, int maxStreak, Integer weekCount) {
}
