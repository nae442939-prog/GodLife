import 'package:flutter/material.dart';
import 'package:provider/provider.dart';

import '../app_state.dart';
import '../theme.dart';
import '../widgets/async_view.dart';
import '../widgets/design.dart';

/// (서버 값, 묶음 이름, 색, 아이콘)
const _groups = [
  ('VERIFY', '인증 횟수', ColorTag.coral, Icons.photo_camera),
  ('STREAK', '연속 인증', ColorTag.gold, Icons.local_fire_department),
  ('FINISH', '완주', ColorTag.mint, Icons.flag),
  ('ACTIVITY', '활동', ColorTag.lav, Icons.auto_awesome),
];

/// 뱃지 전체 보기 (웹의 /me/badges 와 같다): 딴 개수와 묶음별 뱃지 · 얼마나 왔는지
class BadgesScreen extends StatelessWidget {
  const BadgesScreen({super.key});

  @override
  Widget build(BuildContext context) {
    final state = context.read<AppState>();
    return Scaffold(
      appBar: AppBar(title: const Text('뱃지')),
      body: SafeArea(
        child: AsyncView<List<Map<String, dynamic>>>(
          load: () async {
            final profile = await state.api.get('/api/users/${state.user!.id}/profile') as Map<String, dynamic>;
            return ((profile['badges'] as List?) ?? []).cast<Map<String, dynamic>>();
          },
          builder: (context, badges, reload) {
            final earned = badges.where((b) => b['earned'] == true).length;
            return ListView(padding: const EdgeInsets.fromLTRB(20, 4, 20, 28), children: [
              AppCard(
                bordered: false,
                radius: 22,
                padding: const EdgeInsets.all(18),
                child: Column(crossAxisAlignment: CrossAxisAlignment.start, children: [
                  Row(crossAxisAlignment: CrossAxisAlignment.end, children: [
                    Text('$earned', style: const TextStyle(fontSize: 26, fontWeight: FontWeight.w800, height: 1)),
                    Padding(
                      padding: const EdgeInsets.only(left: 4, bottom: 2),
                      child: Text('/ ${badges.length}개',
                          style: const TextStyle(color: AppColors.muted, fontSize: 13, fontWeight: FontWeight.w700)),
                    ),
                  ]),
                  const SizedBox(height: 12),
                  ClipRRect(
                    borderRadius: BorderRadius.circular(99),
                    child: LinearProgressIndicator(
                      value: badges.isEmpty ? 0 : earned / badges.length,
                      minHeight: 8,
                      backgroundColor: AppColors.surfaceAlt,
                      color: AppColors.mint,
                    ),
                  ),
                  const SizedBox(height: 10),
                  Text(
                      earned == badges.length && badges.isNotEmpty
                          ? '모든 뱃지를 모았어요!'
                          : earned == 0
                              ? '첫 인증을 하면 첫 뱃지를 받아요.'
                              : '뱃지 ${badges.length - earned}개가 더 남았어요.',
                      style: const TextStyle(color: AppColors.muted, fontSize: 12, fontWeight: FontWeight.w600)),
                ]),
              ),
              for (final (key, name, colors, icon) in _groups) ...[
                const SizedBox(height: 22),
                Text(name, style: const TextStyle(fontSize: 17, fontWeight: FontWeight.w800, letterSpacing: -0.2)),
                const SizedBox(height: 12),
                AppCard(
                  bordered: false,
                  padding: EdgeInsets.zero,
                  child: Column(children: [
                    for (final b in badges.where((b) => b['group'] == key)) _badge(b, colors, icon),
                  ]),
                ),
              ],
            ]);
          },
        ),
      ),
    );
  }

  /// 뱃지 한 줄: 딴 것은 색 동그라미 + '획득', 아직이면 회색 + 진행 막대
  Widget _badge(Map<String, dynamic> b, (Color, Color) colors, IconData icon) {
    final earned = b['earned'] == true;
    final current = (b['current'] as num).toInt();
    final target = (b['target'] as num).toInt();
    return Container(
      padding: const EdgeInsets.symmetric(horizontal: 16, vertical: 13),
      decoration: const BoxDecoration(border: Border(bottom: BorderSide(color: Color(0xFFF5F2EA)))),
      child: Row(children: [
        Container(
          width: 40,
          height: 40,
          decoration: BoxDecoration(color: earned ? colors.$1 : AppColors.surfaceAlt, shape: BoxShape.circle),
          child: Icon(icon, size: 19, color: earned ? colors.$2 : AppColors.navIdle),
        ),
        const SizedBox(width: 12),
        Expanded(
          child: Column(crossAxisAlignment: CrossAxisAlignment.start, children: [
            Text(b['name'] as String,
                style: TextStyle(
                    fontSize: 13.5, fontWeight: FontWeight.w800, color: earned ? AppColors.text : AppColors.chipText)),
            const SizedBox(height: 3),
            Text(b['description'] as String,
                style: const TextStyle(color: AppColors.muted, fontSize: 11.5, fontWeight: FontWeight.w600)),
            if (!earned) ...[
              const SizedBox(height: 7),
              ClipRRect(
                borderRadius: BorderRadius.circular(99),
                child: LinearProgressIndicator(
                  value: target == 0 ? 0 : current / target,
                  minHeight: 5,
                  backgroundColor: AppColors.surfaceAlt,
                  color: colors.$2,
                ),
              ),
            ],
          ]),
        ),
        const SizedBox(width: 10),
        earned
            ? ColorTag.of('획득', colors)
            : Text('$current / $target',
                style: const TextStyle(color: AppColors.muted, fontSize: 11.5, fontWeight: FontWeight.w700)),
      ]),
    );
  }
}
