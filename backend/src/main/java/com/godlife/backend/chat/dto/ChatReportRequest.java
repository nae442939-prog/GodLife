package com.godlife.backend.chat.dto;

import com.godlife.backend.chat.ReportReason;
import jakarta.validation.constraints.NotNull;
import jakarta.validation.constraints.Size;

public record ChatReportRequest(
        @NotNull(message = "신고 사유를 골라 주세요.")
        ReportReason reason,

        @Size(max = 300, message = "자세한 내용은 300자까지 쓸 수 있어요.")
        String detail) {
}
