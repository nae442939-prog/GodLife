import 'package:flutter/material.dart';
import 'package:provider/provider.dart';

import '../app_state.dart';
import '../theme.dart';
import '../widgets/async_view.dart';
import '../widgets/design.dart';

const _weekdays = ['월', '화', '수', '목', '금', '토', '일'];

/// 주간 회고 (웹의 /records/report 와 같다): 그 주의 성공률 · 코칭 한마디 · 요일별 · 챌린지별 결과.
/// 위에서 주를 골라 지난 주 회고도 본다. 이번 주는 볼 때마다 다시 계산한 값이다.
class WeeklyReportScreen extends StatefulWidget {
  const WeeklyReportScreen({super.key});

  @override
  State<WeeklyReportScreen> createState() => _WeeklyReportScreenState();
}

class _WeeklyReportScreenState extends State<WeeklyReportScreen> {
  String? _week; // 고른 주의 시작일 (없으면 이번 주)

  Future<Map<String, dynamic>> _load() async => await context
      .read<AppState>()
      .api
      .get(_week == null ? '/api/weekly-reports' : '/api/weekly-reports?week=$_week') as Map<String, dynamic>;

  String _short(String date) {
    final p = date.split('-');
    return '${int.parse(p[1])}.${int.parse(p[2])}';
  }

  @override
  Widget build(BuildContext context) {
    return Scaffold(
      appBar: AppBar(title: const Text('주간 회고')),
      body: SafeArea(
        child: AsyncView<Map<String, dynamic>>(
          key: ValueKey(_week),
          load: _load,
          builder: (context, r, reload) {
            final weeks = ((r['weeks'] as List?) ?? []).cast<String>();
            final start = r['weekStart'] as String;
            final stats = r['stats'] as Map<String, dynamic>;
            final rate = (r['successRate'] as num?)?.toInt();
            final previous = (stats['previousRate'] as num?)?.toInt();
            final worst = (r['worstWeekday'] as num?)?.toInt();
            final days = ((stats['weekdays'] as List?) ?? []).cast<Map<String, dynamic>>();
            final challenges = ((stats['challenges'] as List?) ?? []).cast<Map<String, dynamic>>();
            final diff = rate == null || previous == null ? null : rate - previous;
            return ListView(padding: const EdgeInsets.fromLTRB(0, 0, 0, 28), children: [
              if (weeks.length > 1)
                SingleChildScrollView(
                  scrollDirection: Axis.horizontal,
                  padding: const EdgeInsets.fromLTRB(16, 0, 16, 12),
                  child: Row(children: [
                    for (final (i, w) in weeks.indexed)
                      Padding(
                        padding: const EdgeInsets.symmetric(horizontal: 4),
                        child: PillChip(i == 0 ? '이번 주' : '${_short(w)} 주',
                            selected: w == start, onTap: () => setState(() => _week = w)),
                      ),
                  ]),
                ),
              Padding(
                padding: const EdgeInsets.symmetric(horizontal: 20),
                child: Column(crossAxisAlignment: CrossAxisAlignment.stretch, children: [
                  // 성공률
                  Container(
                    padding: const EdgeInsets.all(20),
                    decoration: BoxDecoration(
                      borderRadius: BorderRadius.circular(22),
                      gradient: const LinearGradient(
                        begin: Alignment.topLeft,
                        end: Alignment.bottomRight,
                        colors: [Color(0xFFFDE7D8), Color(0xFFD9F1EA)],
                      ),
                    ),
                    child: Column(crossAxisAlignment: CrossAxisAlignment.start, children: [
                      Text(
                          '${_short(start)} ~ ${_short(r['weekEnd'] as String)}${r['inProgress'] == true ? ' · 진행 중' : ''}',
                          style: const TextStyle(color: AppColors.brown, fontSize: 12, fontWeight: FontWeight.w700)),
                      const SizedBox(height: 10),
                      Row(crossAxisAlignment: CrossAxisAlignment.end, children: [
                        Text(rate == null ? '-' : '$rate%',
                            style: const TextStyle(
                                fontSize: 34, fontWeight: FontWeight.w800, height: 1, letterSpacing: -0.6)),
                        const SizedBox(width: 10),
                        Padding(
                          padding: const EdgeInsets.only(bottom: 4),
                          child: Text('${stats['done']} / ${stats['total']}회 인증',
                              style: const TextStyle(
                                  color: AppColors.brown, fontSize: 12.5, fontWeight: FontWeight.w700)),
                        ),
                      ]),
                      if (diff != null) ...[
                        const SizedBox(height: 10),
                        Text(
                            diff == 0
                                ? '지난주와 같은 성공률이에요.'
                                : '지난주보다 ${diff.abs()}%p ${diff > 0 ? '올랐어요' : '내려갔어요'}.',
                            style: const TextStyle(fontSize: 12.5, fontWeight: FontWeight.w700)),
                      ],
                    ]),
                  ),
                  if ((r['coaching'] as String?)?.isNotEmpty ?? false) ...[
                    const SizedBox(height: 14),
                    AppCard(
                      bordered: false,
                      padding: const EdgeInsets.all(16),
                      child: Row(crossAxisAlignment: CrossAxisAlignment.start, children: [
                        Container(
                          width: 34,
                          height: 34,
                          decoration: BoxDecoration(color: ColorTag.gold.$1, shape: BoxShape.circle),
                          child: Icon(Icons.lightbulb, size: 17, color: ColorTag.gold.$2),
                        ),
                        const SizedBox(width: 12),
                        Expanded(
                          child: Text(r['coaching'] as String,
                              style: const TextStyle(fontSize: 13, fontWeight: FontWeight.w600, height: 1.6)),
                        ),
                      ]),
                    ),
                  ],
                  const SizedBox(height: 22),
                  const Text('요일별', style: TextStyle(fontSize: 17, fontWeight: FontWeight.w800, letterSpacing: -0.2)),
                  const SizedBox(height: 12),
                  AppCard(
                    bordered: false,
                    padding: const EdgeInsets.fromLTRB(10, 16, 10, 14),
                    child: Row(children: [
                      for (final d in days) Expanded(child: _day(d, worst)),
                    ]),
                  ),
                  if (worst != null)
                    Padding(
                      padding: const EdgeInsets.fromLTRB(4, 8, 4, 0),
                      child: Text('가장 많이 놓친 요일은 ${_weekdays[worst - 1]}요일이에요.',
                          style: const TextStyle(color: AppColors.muted, fontSize: 11.5, fontWeight: FontWeight.w600)),
                    ),
                  const SizedBox(height: 22),
                  const Text('챌린지별', style: TextStyle(fontSize: 17, fontWeight: FontWeight.w800, letterSpacing: -0.2)),
                  const SizedBox(height: 12),
                  if (challenges.isEmpty)
                    AppCard(
                      bordered: false,
                      padding: const EdgeInsets.all(16),
                      child: const Text('이 주에는 인증할 챌린지가 없었어요.',
                          style: TextStyle(color: AppColors.muted, fontSize: 12.5, fontWeight: FontWeight.w600)),
                    )
                  else
                    AppCard(
                      bordered: false,
                      padding: EdgeInsets.zero,
                      child: Column(children: [
                        for (final (i, c) in challenges.indexed)
                          Container(
                            padding: const EdgeInsets.symmetric(horizontal: 16, vertical: 13),
                            decoration: BoxDecoration(
                              border: i == challenges.length - 1
                                  ? null
                                  : const Border(bottom: BorderSide(color: Color(0xFFF5F2EA))),
                            ),
                            child: Row(children: [
                              Expanded(
                                child: Text(c['title'] as String,
                                    maxLines: 1,
                                    overflow: TextOverflow.ellipsis,
                                    style: const TextStyle(fontSize: 13.5, fontWeight: FontWeight.w800)),
                              ),
                              const SizedBox(width: 10),
                              ColorTag.of('성공 ${c['done']}', ColorTag.mint),
                              if ((c['fail'] as num) > 0) ...[
                                const SizedBox(width: 6),
                                ColorTag.of('실패 ${c['fail']}', ColorTag.coral),
                              ],
                            ]),
                          ),
                      ]),
                    ),
                ]),
              ),
            ]);
          },
        ),
      ),
    );
  }

  /// 요일 한 칸: 성공(민트) · 실패(코랄) 수와 요일. 가장 많이 놓친 요일은 글자를 진하게
  Widget _day(Map<String, dynamic> d, int? worst) {
    final weekday = (d['weekday'] as num).toInt();
    final done = (d['done'] as num).toInt();
    final fail = (d['fail'] as num).toInt();
    final none = done + fail == 0;
    return Column(children: [
      Container(
        width: 34,
        height: 34,
        alignment: Alignment.center,
        decoration: BoxDecoration(
          color: none ? AppColors.surfaceAlt : (fail == 0 ? ColorTag.mint.$1 : ColorTag.coral.$1),
          shape: BoxShape.circle,
        ),
        child: Text(none ? '-' : '$done/${done + fail}',
            style: TextStyle(
                fontSize: 10.5,
                fontWeight: FontWeight.w800,
                color: none ? AppColors.navIdle : (fail == 0 ? ColorTag.mint.$2 : ColorTag.coral.$2))),
      ),
      const SizedBox(height: 7),
      Text(_weekdays[weekday - 1],
          style: TextStyle(
              fontSize: 11.5,
              fontWeight: weekday == worst ? FontWeight.w800 : FontWeight.w700,
              color: weekday == worst ? AppColors.text : AppColors.muted)),
    ]);
  }
}
