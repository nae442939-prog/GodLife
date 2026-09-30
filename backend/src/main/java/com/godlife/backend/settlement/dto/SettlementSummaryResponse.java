package com.godlife.backend.settlement.dto;

import java.time.LocalDate;

/**
 * 가장 최근에 끝난 기간(어제 / 지난주)의 결과. 끝난 기간이 없으면 응답 자체가 없다(204).
 * 포인트는 매일 결과만 쌓이고, 챌린지가 끝나면 한 번에 지급된다.
 * @param periodIndex 몇 번째 기간인지 (0부터: 화면에서 'N일째 · N주째')
 * @param weekly      주 N회 챌린지라 기간이 한 주인지
 * @param bet         포인트 챌린지인지 (무료 챌린지는 인원만 센다)
 * @param settled     그 기간 결과가 기록됐는지 (자정이 지나기 전이면 false)
 * @param mySuccess   내가 그 기간 목표를 채웠는지 (참가자가 아니면 null)
 * @param myRefund    그 기간 내가 돌려받을 몫
 * @param myReward    그 기간 내가 받을 보상
 * @param myLost      그 기간 내가 못 채워서 잃은(깎인) 포인트
 * @param totalRefund 지금까지 쌓인 내 환급 (paid 면 실제로 받은 금액)
 * @param totalReward 지금까지 쌓인 내 보상 (paid 면 실제로 받은 금액)
 * @param paid        챌린지가 끝나 지갑으로 한 번에 지급됐는지
 */
public record SettlementSummaryResponse(int periodIndex, LocalDate periodStart, LocalDate periodEnd, boolean weekly,
                                        boolean bet, boolean settled, int successCount, int failCount,
                                        long forfeitedPool, long rewardShare,
                                        Boolean mySuccess, long myRefund, long myReward, long myLost,
                                        long totalRefund, long totalReward, boolean paid) {
}
