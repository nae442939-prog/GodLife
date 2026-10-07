package com.godlife.backend.weeklyreport;

import com.godlife.backend.weeklyreport.WeeklyReportService.ChallengeStat;
import com.godlife.backend.weeklyreport.WeeklyReportService.Stats;
import com.godlife.backend.weeklyreport.WeeklyReportService.WeekdayStat;

import java.util.ArrayList;
import java.util.Comparator;
import java.util.List;

/**
 * 주간 회고의 코칭 문구. 외부 AI API 를 부르지 않고, 그 주의 실제 인증 기록(성공률 · 지난주와의 차이 ·
 * 자주 놓친 요일 · 챌린지별 결과)을 보고 정해 둔 규칙으로 문장을 고른다. 같은 기록이면 항상 같은 글이 나온다.
 */
final class WeeklyCoach {

    private static final String[] WEEKDAY = {"", "월", "화", "수", "목", "금", "토", "일"};
    /** 지난주와 이만큼(%p) 넘게 차이 나야 올랐다 · 내렸다고 말한다 */
    private static final int TREND = 5;

    private WeeklyCoach() {
    }

    /** @param finished 끝난 주면 true, 아직 진행 중인 이번 주면 false (말투만 다르다) */
    static String write(Stats s, Integer rate, Integer worstWeekday, boolean finished) {
        if (s.total() == 0 || rate == null) {
            return finished ? "이 주에는 인증할 날이 없었어요. 챌린지에 참여하면 다음 회고가 채워져요."
                    : "이번 주에는 아직 인증할 날이 없었어요. 챌린지에 참여하면 회고가 채워져요.";
        }
        String week = finished ? "지난 한 주" : "이번 주는 지금까지";
        List<String> lines = new ArrayList<>();

        if (rate == 100) {
            lines.add("%s 인증 %d번을 한 번도 놓치지 않았어요. 완벽해요 👏".formatted(week, s.total()));
        } else if (rate >= 80) {
            lines.add("%s %d번 중 %d번 인증했어요(성공률 %d%%). 꾸준함이 자리를 잡았어요."
                    .formatted(week, s.total(), s.done(), rate));
        } else if (rate >= 50) {
            lines.add("%s %d번 중 %d번 인증했어요(성공률 %d%%). 절반 넘게 해냈으니 조금만 더 채워 봐요."
                    .formatted(week, s.total(), s.done(), rate));
        } else if (rate > 0) {
            lines.add("%s %d번 중 %d번 인증했어요(성공률 %d%%). 쉽지 않았네요. 하루에 하나만 확실히 지키는 것부터 해 봐요."
                    .formatted(week, s.total(), s.done(), rate));
        } else {
            lines.add("%s 인증 %d번을 모두 놓쳤어요. 괜찮아요, 오늘 한 번 인증하는 것부터 다시 시작해 봐요."
                    .formatted(week, s.total()));
        }

        if (s.previousRate() != null) {
            int diff = rate - s.previousRate();
            if (diff >= TREND) {
                lines.add("그 전 주보다 성공률이 %d%%p 올랐어요 📈".formatted(diff));
            } else if (diff <= -TREND) {
                lines.add("그 전 주보다 성공률이 %d%%p 내려갔어요. 무엇이 달랐는지 한번 떠올려 보세요.".formatted(-diff));
            } else {
                lines.add("그 전 주와 비슷한 흐름을 이어 가고 있어요.");
            }
        }

        if (worstWeekday != null) {
            int missed = s.weekdays().stream().mapToInt(WeekdayStat::fail).max().orElse(0);
            // 똑같이 가장 많이 놓친 요일들. 넷 이상이면 특정 요일 문제가 아니라 고르게 놓친 것이다
            List<WeekdayStat> worst = s.weekdays().stream().filter(w -> w.fail() == missed).toList();
            if (worst.size() >= 4) {
                lines.add("특정 요일이 아니라 여러 날에 걸쳐 놓쳤어요. 매일 같은 시간에 인증하는 습관을 만들어 보세요.");
            } else if (worst.size() >= 2) {
                lines.add("가장 많이 놓친 날은 %s요일이에요(각 %d번). 그 요일엔 인증할 시간을 미리 정해 두면 좋아요."
                        .formatted(String.join(" · ", worst.stream().map(w -> WEEKDAY[w.weekday()]).toList()),
                                missed));
            } else if (worstWeekday >= 6) {
                lines.add("가장 많이 놓친 날은 %s요일이에요(%d번). 주말엔 일어나자마자 인증하는 식으로 시간을 정해 두면 좋아요."
                        .formatted(WEEKDAY[worstWeekday], missed));
            } else {
                lines.add("가장 많이 놓친 날은 %s요일이에요(%d번). %s요일엔 인증할 시간을 미리 정해 두면 좋아요."
                        .formatted(WEEKDAY[worstWeekday], missed, WEEKDAY[worstWeekday]));
            }
        }

        if (s.challenges().size() >= 2) {
            ChallengeStat best = s.challenges().stream().filter(c -> c.done() > 0)
                    .max(Comparator.comparingDouble(WeeklyCoach::rate)).orElse(null);
            ChallengeStat hardest = s.challenges().stream().filter(c -> c.fail() > 0)
                    .min(Comparator.comparingDouble(WeeklyCoach::rate)).orElse(null);
            if (best != null && hardest != null && !best.challengeId().equals(hardest.challengeId())) {
                lines.add("가장 잘 지킨 챌린지는 '%s'(%d/%d), 가장 어려웠던 챌린지는 '%s'(%d/%d)예요."
                        .formatted(best.title(), best.done(), best.done() + best.fail(), hardest.title(),
                                hardest.done(), hardest.done() + hardest.fail()));
            }
        }
        return String.join("\n", lines);
    }

    private static double rate(ChallengeStat c) {
        return (double) c.done() / (c.done() + c.fail());
    }
}
