package com.godlife.backend.settlement;

import com.godlife.backend.challenge.Challenge;
import com.godlife.backend.challenge.FrequencyType;

import java.time.LocalDate;
import java.time.temporal.ChronoUnit;
import java.util.ArrayList;
import java.util.List;

/**
 * 정산 한 번의 단위 기간. 매일 챌린지는 하루, 주 N회는 시작일부터 7일씩 끊은 한 주 (마지막 주는 짧을 수 있다).
 * @param index    몇 번째 기간인지 (0부터) — 화면의 'N일째 · N주째'
 * @param required 이 기간에 채워야 하는 인증 수 (매일 1, 주 N회는 min(N, 그 주 일수))
 * @param last     마지막 기간인지 (참가 포인트를 나누고 남은 나머지를 여기에 더한다)
 */
public record Period(int index, LocalDate start, LocalDate end, int required, boolean last) {

    /** 챌린지의 모든 기간 */
    public static List<Period> all(Challenge c) {
        List<Period> periods = new ArrayList<>();
        if (c.getFrequencyType() == FrequencyType.DAILY) {
            int days = (int) c.totalDays();
            for (int i = 0; i < days; i++) {
                LocalDate d = c.getStartDate().plusDays(i);
                periods.add(new Period(i, d, d, 1, i == days - 1));
            }
            return periods;
        }
        int i = 0;
        for (LocalDate ws = c.getStartDate(); !ws.isAfter(c.getEndDate()); ws = ws.plusWeeks(1), i++) {
            LocalDate we = ws.plusDays(6).isAfter(c.getEndDate()) ? c.getEndDate() : ws.plusDays(6);
            int days = (int) ChronoUnit.DAYS.between(ws, we) + 1;
            periods.add(new Period(i, ws, we, Math.min(c.getWeeklyCount(), days), !we.isBefore(c.getEndDate())));
        }
        return periods;
    }

    /** 참가 포인트를 인증 1회당 몫으로 나눈 값 (나머지는 마지막 기간을 다 채운 사람에게) */
    public static long unit(Challenge c, long deposit) {
        return deposit / c.targetCount();
    }

    public static long remainder(Challenge c, long deposit) {
        return deposit - unit(c, deposit) * c.targetCount();
    }

    /** 이 기간 한 사람 몫 */
    public long value(Challenge c, long deposit) {
        return unit(c, deposit) * required + (last ? remainder(c, deposit) : 0);
    }

    /** done 번 인증한 사람이 돌려받는 몫 */
    public long refund(Challenge c, long deposit, int done) {
        int counted = Math.min(done, required);
        return unit(c, deposit) * counted + (last && counted == required ? remainder(c, deposit) : 0);
    }
}
