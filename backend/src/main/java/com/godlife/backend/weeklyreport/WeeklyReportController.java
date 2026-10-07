package com.godlife.backend.weeklyreport;

import com.godlife.backend.auth.AuthUser;
import lombok.RequiredArgsConstructor;
import org.springframework.security.core.annotation.AuthenticationPrincipal;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.RequestParam;
import org.springframework.web.bind.annotation.RestController;

import java.time.LocalDate;
import java.time.format.DateTimeParseException;

/** 주간 회고 리포트 (로그인 회원, 본인 것만) */
@RestController
@RequiredArgsConstructor
public class WeeklyReportController {

    private final WeeklyReportService weeklyReportService;

    /** ?week=2026-09-28 (그 날짜가 속한 주. 없거나 잘못되면 이번 주, 이번 주가 비어 있으면 가장 최근 리포트) */
    @GetMapping("/api/weekly-reports")
    public WeeklyReportService.Report report(@RequestParam(required = false) String week,
                                             @AuthenticationPrincipal AuthUser authUser) {
        return weeklyReportService.view(authUser.id(), parse(week));
    }

    private static LocalDate parse(String week) {
        try {
            return week == null ? null : LocalDate.parse(week);
        } catch (DateTimeParseException e) {
            return null;
        }
    }
}
