import 'package:flutter/material.dart';
import 'package:provider/provider.dart';

import '../api/models.dart';
import '../app_state.dart';
import '../theme.dart';
import '../widgets/async_view.dart';
import 'challenge_detail_screen.dart';
import 'verify_screen.dart';

/// 내 챌린지: 오늘 인증할 것이 맨 위에 오고, 바로 [인증하기]로 간다
class MyChallengesScreen extends StatelessWidget {
  const MyChallengesScreen({super.key});

  @override
  Widget build(BuildContext context) {
    final state = context.read<AppState>();
    return Scaffold(
      appBar: AppBar(title: const Text('내 챌린지')),
      body: AsyncView<List<MyChallenge>>(
      load: () async {
        final data = await state.api.get('/api/me/challenges') as List;
        final items = data.map((c) => MyChallenge.fromJson(c as Map<String, dynamic>)).toList();
        // 오늘 인증할 수 있는 것 → 참여 중인 것 → 개설만 한 것 순서
        int order(MyChallenge c) => c.canVerify ? 0 : (c.joined ? 1 : 2);
        items.sort((a, b) => order(a) - order(b));
        return items;
      },
      builder: (context, items, reload) {
        return ListView(padding: const EdgeInsets.fromLTRB(16, 8, 16, 24), children: [
          if (items.isEmpty)
            const Padding(
              padding: EdgeInsets.only(top: 80),
              child: Center(
                child: Text('참여 중인 챌린지가 없어요.\n둘러보기에서 챌린지에 참여해 보세요.',
                    textAlign: TextAlign.center, style: TextStyle(color: AppColors.muted)),
              ),
            ),
          for (final c in items) ...[
            MyChallengeCard(c, reload: reload),
            const SizedBox(height: 12),
          ],
        ]);
      },
    ));
  }
}

class MyChallengeCard extends StatelessWidget {
  const MyChallengeCard(this.c, {super.key, required this.reload});

  final MyChallenge c;
  final Future<void> Function() reload;

  @override
  Widget build(BuildContext context) {
    return Card(
      clipBehavior: Clip.antiAlias,
      child: InkWell(
        onTap: () async {
          await Navigator.of(context).push(MaterialPageRoute(builder: (_) => ChallengeDetailScreen(id: c.id)));
          reload();
        },
        child: Padding(
          padding: const EdgeInsets.all(16),
          child: Column(crossAxisAlignment: CrossAxisAlignment.start, children: [
            Row(children: [
              Tag(c.category.name),
              const SizedBox(width: 6),
              Tag(_stateLabel(), color: c.canVerify ? AppColors.primary : AppColors.muted),
            ]),
            const SizedBox(height: 10),
            Text(c.title, style: const TextStyle(fontSize: 17, fontWeight: FontWeight.w800)),
            const SizedBox(height: 4),
            Text('${c.startDate} ~ ${c.endDate}', style: const TextStyle(color: AppColors.muted, fontSize: 13)),
            if (c.joined) ...[
              const SizedBox(height: 12),
              ClipRRect(
                borderRadius: BorderRadius.circular(99),
                child: LinearProgressIndicator(
                  value: c.progress,
                  minHeight: 8,
                  backgroundColor: AppColors.border,
                  color: AppColors.mint,
                ),
              ),
              const SizedBox(height: 6),
              Text('인증 ${c.successDays} / ${c.targetCount}회',
                  style: const TextStyle(fontSize: 13, fontWeight: FontWeight.w600)),
            ],
            if (c.canVerify) ...[
              const SizedBox(height: 12),
              FilledButton.icon(
                onPressed: () async {
                  await Navigator.of(context)
                      .push(MaterialPageRoute(builder: (_) => VerifyScreen(challengeId: c.id, title: c.title)));
                  reload();
                },
                icon: const Icon(Icons.photo_camera_outlined),
                label: const Text('인증하기'),
              ),
            ],
          ]),
        ),
      ),
    );
  }

  String _stateLabel() {
    if (!c.joined) return '개설만 함';
    return switch (c.verifyState) {
      'OPEN' => '오늘 인증 전',
      'DONE_TODAY' => '오늘 인증 완료',
      'WEEK_DONE' => '이번 주 완료',
      'TIME_CLOSED' => '인증 시간이 아니에요',
      'NOT_STARTED' => daysUntil(c.startDate) > 0 ? '시작 전 · D-${daysUntil(c.startDate)}' : '시작 전',
      'ENDED' => '종료',
      _ => '참여 중',
    };
  }
}
