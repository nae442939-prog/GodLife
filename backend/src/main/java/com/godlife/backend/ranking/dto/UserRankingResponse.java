package com.godlife.backend.ranking.dto;

import java.util.List;

/**
 * 전체 랭킹. 상위 N명 + 내 줄(로그인했고 기록이 있으면).
 * value 는 기준에 따라 인증 수 · 연속 일수 · 포인트 · 성공률(소수 첫째 자리까지, 0~100).
 */
public record UserRankingResponse(List<Entry> top, Entry me) {

    public record Entry(int rank, String nickname, String profileImageUrl, double value, boolean mine) {
    }
}
