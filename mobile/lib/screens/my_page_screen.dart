import 'package:flutter/material.dart';
import 'package:provider/provider.dart';

import '../api/api_client.dart';
import '../app_state.dart';
import '../theme.dart';
import '../widgets/async_view.dart';

/// 마이페이지: 내 프로필 · 칭호(티어) 점수 · 로그아웃
class MyPageScreen extends StatelessWidget {
  const MyPageScreen({super.key});

  @override
  Widget build(BuildContext context) {
    final state = context.watch<AppState>();
    final user = state.user;
    if (user == null) return const SizedBox.shrink();
    final image = user.profileImageUrl;

    return ListView(padding: const EdgeInsets.fromLTRB(16, 20, 16, 24), children: [
      const Padding(
        padding: EdgeInsets.fromLTRB(4, 0, 4, 14),
        child: Text('마이페이지', style: TextStyle(fontSize: 24, fontWeight: FontWeight.w900)),
      ),
      Card(
        child: Padding(
          padding: const EdgeInsets.all(18),
          child: Row(children: [
            CircleAvatar(
              radius: 34,
              backgroundColor: AppColors.surfaceAlt,
              // 프로필 사진은 공개 주소라 토큰 없이 받는다
              backgroundImage: image != null ? NetworkImage('$serverBase$image') : null,
              child: image == null ? const Icon(Icons.person, size: 36, color: AppColors.muted) : null,
            ),
            const SizedBox(width: 16),
            Expanded(
              child: Column(crossAxisAlignment: CrossAxisAlignment.start, children: [
                Row(children: [
                  Flexible(
                    child: Text(user.nickname,
                        overflow: TextOverflow.ellipsis,
                        style: const TextStyle(fontSize: 20, fontWeight: FontWeight.w900)),
                  ),
                  if (user.role == 'ADMIN') ...[const SizedBox(width: 8), const Tag('관리자')],
                ]),
                if (user.bio != null && user.bio!.isNotEmpty) ...[
                  const SizedBox(height: 4),
                  Text(user.bio!, style: const TextStyle(color: AppColors.text)),
                ],
                if (user.email != null) ...[
                  const SizedBox(height: 4),
                  Text(user.email!, style: const TextStyle(color: AppColors.muted, fontSize: 13)),
                ],
              ]),
            ),
          ]),
        ),
      ),
      const SizedBox(height: 12),
      if (user.role != 'ADMIN')
        SizedBox(
          height: 150,
          child: AsyncView<Map<String, dynamic>>(
            load: () async => await state.api.get('/api/users/me/tier') as Map<String, dynamic>,
            builder: (context, tier, reload) => ListView(physics: const NeverScrollableScrollPhysics(), children: [
              _TierCard(tier),
            ]),
          ),
        ),
      if (!user.phoneVerified)
        const Card(
          color: AppColors.surfaceAlt,
          child: Padding(
            padding: EdgeInsets.all(16),
            child: Text('휴대폰 본인인증을 해야 챌린지에 참여할 수 있어요. 인증은 웹의 설정 > 내 정보에서 할 수 있어요.'),
          ),
        ),
      const SizedBox(height: 12),
      Card(
        clipBehavior: Clip.antiAlias,
        child: ListTile(
          leading: const Icon(Icons.logout, color: AppColors.muted),
          title: const Text('로그아웃'),
          onTap: () => state.logout(),
        ),
      ),
      const SizedBox(height: 16),
      const Text(
        '갓생살기의 포인트는 챌린지 참여와 포인트 상점에서만 쓸 수 있어요.',
        textAlign: TextAlign.center,
        style: TextStyle(color: AppColors.muted, fontSize: 12.5),
      ),
    ]);
  }
}

class _TierCard extends StatelessWidget {
  const _TierCard(this.tier);

  final Map<String, dynamic> tier;

  @override
  Widget build(BuildContext context) {
    final current = tier['tier'] as Map<String, dynamic>?;
    final next = tier['next'] as Map<String, dynamic>?;
    final score = (tier['score'] as num?)?.toInt() ?? 0;
    final record = (tier['record'] as Map<String, dynamic>?) ?? const {};
    final floor = (current?['minScore'] as num?)?.toInt() ?? 0;
    final ceil = (next?['minScore'] as num?)?.toInt();
    final progress = ceil == null || ceil == floor ? 1.0 : ((score - floor) / (ceil - floor)).clamp(0, 1).toDouble();

    return Card(
      child: Padding(
        padding: const EdgeInsets.all(16),
        child: Column(crossAxisAlignment: CrossAxisAlignment.start, children: [
          Row(children: [
            Tag('${current?['name'] ?? '칭호'}', filled: true),
            const SizedBox(width: 8),
            Text('$score점', style: const TextStyle(fontSize: 18, fontWeight: FontWeight.w900)),
            const Spacer(),
            if (next != null)
              Text('다음 ${next['name']} ${next['minScore']}점', style: const TextStyle(color: AppColors.muted, fontSize: 12.5)),
          ]),
          const SizedBox(height: 12),
          ClipRRect(
            borderRadius: BorderRadius.circular(99),
            child: LinearProgressIndicator(
              value: progress,
              minHeight: 8,
              backgroundColor: AppColors.border,
              color: AppColors.mint,
            ),
          ),
          const SizedBox(height: 12),
          Text(
            '인증 ${record['verified'] ?? 0}회 · 완주 ${record['completed'] ?? 0}회 · 실패 ${record['failed'] ?? 0}회 · 포기 ${record['gaveUp'] ?? 0}회',
            style: const TextStyle(fontSize: 13, fontWeight: FontWeight.w600),
          ),
        ]),
      ),
    );
  }
}
