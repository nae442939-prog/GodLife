package com.godlife.backend.chat.dto;

import com.godlife.backend.chat.ReportReason;

import java.util.List;
import java.util.Map;

/**
 * 방장 알림: 신고가 기준(기본 20건) 이상 쌓인 참가자. 방장이 사유를 보고 내보낼지 넘길지 정한다.
 *
 * @param reasons       사유별 건수
 * @param recentDetails 신고자가 적은 자세한 내용 (최근 것부터 최대 5개)
 */
public record ReportAlertResponse(Long userId, String nickname, long reportCount, Map<ReportReason, Long> reasons,
                                  List<String> recentDetails) {
}
