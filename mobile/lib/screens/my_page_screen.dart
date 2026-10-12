import 'package:flutter/material.dart';
import 'package:provider/provider.dart';

import '../api/api_client.dart';
import '../api/models.dart';
import '../app_state.dart';
import '../theme.dart';
import '../widgets/async_view.dart';
import '../widgets/design.dart';
import 'my_challenges_screen.dart';
import 'notifications_screen.dart';
import 'ranking_screen.dart';
import 'settings_screen.dart';
import 'wallet_screen.dart';

/// 마이페이지 (시안): 프로필 → 포인트 → 메뉴(동그란 색 아이콘) → 안내 문구
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
      builder: (context, data, reload) => ListView(padding: const EdgeInsets.fromLTRB(20, 4, 20, 28), children: [
        // 프로필
        AppCard(
          bordered: false,
          padding: const EdgeInsets.all(18),
          child: Row(children: [
            Container(
              width: 54,
              height: 54,
              decoration: const BoxDecoration(
                shape: BoxShape.circle,
                gradient: LinearGradient(
                  begin: Alignment.topLeft,
                  end: Alignment.bottomRight,
                  colors: [Color(0xFFFBE4D8), Color(0xFFF0876A)],
                ),
              ),
              clipBehavior: Clip.antiAlias,
              // 프로필 사진은 공개 주소라 토큰 없이 받는다. 없으면 사람 그림
              child: image != null
                  ? Image.network('$serverBase$image',
                      fit: BoxFit.cover,
                      errorBuilder: (_, _, _) => const Icon(Icons.person, color: Colors.white, size: 30))
                  : const Icon(Icons.person, color: Colors.white, size: 30),
            ),
            const SizedBox(width: 14),
            Expanded(
              child: Column(crossAxisAlignment: CrossAxisAlignment.start, children: [
                Row(children: [
                  Flexible(
                    child: Text(user.nickname,
                        overflow: TextOverflow.ellipsis,
                        style: const TextStyle(fontSize: 16, fontWeight: FontWeight.w800, letterSpacing: -0.1)),
                  ),
                  if (admin) ...[const SizedBox(width: 7), ColorTag.of('관리자', ColorTag.gold)],
                ]),
                if (user.bio != null && user.bio!.isNotEmpty) ...[
                  const SizedBox(height: 4),
                  Text(user.bio!, style: const TextStyle(fontSize: 12.5, fontWeight: FontWeight.w600)),
                ],
                if (user.email != null) ...[
                  const SizedBox(height: 4),
                  Text(user.email!,
                      style: const TextStyle(color: AppColors.muted, fontSize: 12, fontWeight: FontWeight.w600)),
                ],
              ]),
            ),
          ]),
        ),
        const SizedBox(height: 16),
        // 포인트: 누르면 지갑으로
        AppCard(
          bordered: false,
          padding: const EdgeInsets.symmetric(horizontal: 16, vertical: 18),
          onTap: () => open(const WalletScreen()),
          child: Row(children: [
            Expanded(child: _point('충전 포인트', data.wallet.chargedBalance, AppColors.text)),
            Container(
                width: 1, height: 32, color: AppColors.border, margin: const EdgeInsets.symmetric(horizontal: 18)),
            Expanded(child: _point('보상 포인트', data.wallet.rewardBalance, AppColors.mint)),
            const Icon(Icons.chevron_right, color: Color(0xFFC7C0B0), size: 20),
          ]),
        ),
        if (data.tier != null) ...[
          const SizedBox(height: 16),
          _TierCard(data.tier!),
        ],
        if (!user.phoneVerified) ...[
          const SizedBox(height: 16),
          AppCard(
            bordered: false,
            padding: const EdgeInsets.all(16),
            onTap: () => open(const SettingsScreen()),
            child: const Text('휴대폰 본인인증을 해야 챌린지에 참여할 수 있어요. 설정에서 인증해 주세요.',
                style: TextStyle(fontSize: 12.5, fontWeight: FontWeight.w600, height: 1.5)),
          ),
        ],
        const SizedBox(height: 16),
        // 메뉴
        AppCard(
          bordered: false,
          padding: EdgeInsets.zero,
          child: Column(children: [
            _menu(Icons.flag, ColorTag.coral, '내 챌린지', () => open(const MyChallengesScreen())),
            _menu(Icons.emoji_events, ColorTag.gold, '랭킹', () => open(const RankingScreen())),
            _menu(Icons.account_balance_wallet, ColorTag.gold, '포인트 지갑 · 거래 내역', () => open(const WalletScreen())),
            _menu(Icons.shopping_bag, ColorTag.mint, '포인트 상점', () => openWeb('/shop')),
            _menu(Icons.notifications, ColorTag.lav, '알림', () => open(const NotificationsScreen())),
            _menu(Icons.settings, (AppColors.surfaceAlt, AppColors.brown), '설정', () => open(const SettingsScreen())),
            _menu(Icons.logout, (const Color(0xFFF6E2DC), const Color(0xFFC2574A)), '로그아웃', () => state.logout(),
                danger: true, last: true),
          ]),
        ),
        const SizedBox(height: 16),
        const Padding(
          padding: EdgeInsets.symmetric(horizontal: 4),
          child: Text(
            '갓생살기의 포인트는 챌린지 참여와 포인트 상점에서만 쓸 수 있어요.\n충전 · 환불은 포인트 지갑에서, 상점은 위 메뉴에서 열 수 있어요.',
            style: TextStyle(color: AppColors.navIdle, fontSize: 11, fontWeight: FontWeight.w600, height: 1.7),
          ),
        ),
      ]),
    );
  }

  Widget _point(String label, int value, Color color) {
    return Column(crossAxisAlignment: CrossAxisAlignment.start, children: [
      Text(label, style: const TextStyle(color: AppColors.muted, fontSize: 11.5, fontWeight: FontWeight.w700)),
      const SizedBox(height: 7),
      Text('${points(value)}P',
          style: TextStyle(fontSize: 17, fontWeight: FontWeight.w800, color: color, letterSpacing: -0.1)),
    ]);
  }

  /// 메뉴 한 줄: 동그란 색 아이콘 · 이름 · 화살표 (로그아웃은 붉은 글자, 화살표 없음)
  Widget _menu(IconData icon, (Color, Color) colors, String label, VoidCallback onTap,
      {bool danger = false, bool last = false}) {
    return InkWell(
      onTap: onTap,
      child: Container(
        padding: const EdgeInsets.symmetric(horizontal: 16, vertical: 15),
        decoration: BoxDecoration(
          border: last ? null : const Border(bottom: BorderSide(color: Color(0xFFF5F2EA))),
        ),
        child: Row(children: [
          Container(
            width: 36,
            height: 36,
            decoration: BoxDecoration(color: colors.$1, shape: BoxShape.circle),
            child: Icon(icon, size: 18, color: colors.$2),
          ),
          const SizedBox(width: 13),
          Expanded(
            child: Text(label,
                style: TextStyle(
                    fontSize: 13.5, fontWeight: FontWeight.w700, color: danger ? colors.$2 : AppColors.text)),
          ),
          if (!danger) const Icon(Icons.chevron_right, size: 18, color: Color(0xFFC7C0B0)),
        ]),
      ),
    );
  }
}

class _MyData {
  _MyData(this.wallet, this.tier);

  final Wallet wallet;
  final Map<String, dynamic>? tier;
}

/// 칭호(티어) 점수. 관리자는 칭호 대상이 아니라 보이지 않는다
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

    return AppCard(
      bordered: false,
      padding: const EdgeInsets.all(16),
      child: Column(crossAxisAlignment: CrossAxisAlignment.start, children: [
        Row(children: [
          ColorTag.of('${current?['name'] ?? '칭호'}', ColorTag.gold),
          const SizedBox(width: 8),
          Text('$score점', style: const TextStyle(fontSize: 17, fontWeight: FontWeight.w800)),
          const Spacer(),
          if (next != null)
            Text('다음 ${next['name']} ${next['minScore']}점',
                style: const TextStyle(color: AppColors.muted, fontSize: 11.5, fontWeight: FontWeight.w700)),
        ]),
        const SizedBox(height: 12),
        ClipRRect(
          borderRadius: BorderRadius.circular(999),
          child: LinearProgressIndicator(
            value: progress,
            minHeight: 6,
            backgroundColor: AppColors.surfaceAlt,
            color: AppColors.mint,
          ),
        ),
        const SizedBox(height: 12),
        Text(
          '인증 ${record['verified'] ?? 0}회 · 완주 ${record['completed'] ?? 0}회 · 실패 ${record['failed'] ?? 0}회 · 포기 ${record['gaveUp'] ?? 0}회',
          style: const TextStyle(fontSize: 12, fontWeight: FontWeight.w700, color: AppColors.muted),
        ),
      ]),
    );
  }
}
