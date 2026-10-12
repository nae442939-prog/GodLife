import 'package:flutter/material.dart';
import 'package:provider/provider.dart';

import '../api/api_client.dart';
import '../api/models.dart';
import '../app_state.dart';
import '../theme.dart';
import '../widgets/async_view.dart';
import 'my_challenges_screen.dart';
import 'notifications_screen.dart';
import 'wallet_screen.dart';

/// 마이페이지: 내 프로필 · 포인트 · 칭호(티어) · 메뉴(내 챌린지 · 지갑 · 알림 · 로그아웃)
class MyPageScreen extends StatelessWidget {
  const MyPageScreen({super.key});

  @override
  Widget build(BuildContext context) {
    final state = context.watch<AppState>();
    final user = state.user;
    if (user == null) return const SizedBox.shrink();
    final image = user.profileImageUrl;
    final admin = user.role == 'ADMIN';

    void open(Widget page) => Navigator.of(context).push(MaterialPageRoute(builder: (_) => page));

    return AsyncView<_MyData>(
      load: () async {
        final wallet = Wallet.fromJson(await state.api.get('/api/wallet') as Map<String, dynamic>);
        final tier = admin ? null : await state.api.get('/api/users/me/tier') as Map<String, dynamic>;
        return _MyData(wallet, tier);
      },
      builder: (context, data, reload) => ListView(padding: const EdgeInsets.fromLTRB(16, 8, 16, 28), children: [
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
                    if (admin) ...[const SizedBox(width: 8), const Tag('관리자')],
                  ]),
                  if (user.bio != null && user.bio!.isNotEmpty) ...[
                    const SizedBox(height: 4),
                    Text(user.bio!),
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
        // 포인트: 누르면 지갑으로
        Card(
          clipBehavior: Clip.antiAlias,
          child: InkWell(
            onTap: () => open(const WalletScreen()),
            child: Padding(
              padding: const EdgeInsets.all(16),
              child: Row(children: [
                Expanded(child: _point('충전 포인트', data.wallet.chargedBalance, AppColors.brown)),
                Container(width: 1, height: 40, color: AppColors.border),
                const SizedBox(width: 16),
                Expanded(child: _point('보상 포인트', data.wallet.rewardBalance, AppColors.mint)),
                const Icon(Icons.chevron_right, color: AppColors.muted),
              ]),
            ),
          ),
        ),
        if (data.tier != null) ...[
          const SizedBox(height: 12),
          _TierCard(data.tier!),
        ],
        if (!user.phoneVerified) ...[
          const SizedBox(height: 12),
          const Card(
            color: AppColors.surfaceAlt,
            child: Padding(
              padding: EdgeInsets.all(16),
              child: Text('휴대폰 본인인증을 해야 챌린지에 참여할 수 있어요. 인증은 웹의 설정 > 내 정보에서 할 수 있어요.'),
            ),
          ),
        ],
        const SizedBox(height: 12),
        Card(
          clipBehavior: Clip.antiAlias,
          child: Column(children: [
            _menu(Icons.flag_outlined, '내 챌린지', () => open(const MyChallengesScreen())),
            const Divider(height: 1, color: AppColors.border),
            _menu(Icons.account_balance_wallet_outlined, '포인트 지갑 · 거래 내역', () => open(const WalletScreen())),
            const Divider(height: 1, color: AppColors.border),
            _menu(Icons.storefront_outlined, '포인트 상점', () => openWeb('/shop')),
            const Divider(height: 1, color: AppColors.border),
            _menu(Icons.notifications_none, '알림', () => open(const NotificationsScreen())),
            const Divider(height: 1, color: AppColors.border),
            _menu(Icons.logout, '로그아웃', () => state.logout(), chevron: false),
          ]),
        ),
        const SizedBox(height: 16),
        const Text(
          '갓생살기의 포인트는 챌린지 참여와 포인트 상점에서만 쓸 수 있어요.\n충전 · 환불은 포인트 지갑에서, 상점은 위 메뉴에서 열 수 있어요.',
          textAlign: TextAlign.center,
          style: TextStyle(color: AppColors.muted, fontSize: 12.5, height: 1.5),
        ),
      ]),
    );
  }

  Widget _point(String label, int value, Color color) {
    return Column(crossAxisAlignment: CrossAxisAlignment.start, children: [
      Text(label, style: const TextStyle(color: AppColors.muted, fontSize: 13, fontWeight: FontWeight.w600)),
      const SizedBox(height: 4),
      Text('${points(value)}P', style: TextStyle(fontSize: 19, fontWeight: FontWeight.w900, color: color)),
    ]);
  }

  Widget _menu(IconData icon, String label, VoidCallback onTap, {bool chevron = true}) {
    return ListTile(
      leading: Icon(icon, color: AppColors.brown),
      title: Text(label, style: const TextStyle(fontWeight: FontWeight.w600)),
      trailing: chevron ? const Icon(Icons.chevron_right, color: AppColors.muted) : null,
      onTap: onTap,
    );
  }
}

class _MyData {
  _MyData(this.wallet, this.tier);

  final Wallet wallet;
  final Map<String, dynamic>? tier;
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
