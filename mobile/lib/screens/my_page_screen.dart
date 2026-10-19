import 'package:flutter/material.dart';
import 'package:provider/provider.dart';

import '../api/api_client.dart';
import '../api/models.dart';
import '../app_state.dart';
import '../theme.dart';
import '../widgets/async_view.dart';
import '../widgets/badge_medal.dart';
import '../widgets/design.dart';
import '../widgets/phone_verify_sheet.dart';
import 'admin_screen.dart';
import 'my_challenges_screen.dart';
import 'notifications_screen.dart';
import 'profile_edit_screen.dart';
import 'badges_screen.dart';
import 'follow_list_screen.dart';
import 'ranking_screen.dart';
import 'settings_screen.dart';
import 'wallet_screen.dart';
import 'weekly_report_screen.dart';

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

    Future<void> open(Widget page) async {
      await Navigator.of(context).push(MaterialPageRoute(builder: (_) => page));
    }

    return AsyncView<_MyData>(
      load: () async {
        final wallet = Wallet.fromJson(await state.api.get('/api/wallet') as Map<String, dynamic>);
        final tier = admin ? null : await state.api.get('/api/users/me/tier') as Map<String, dynamic>;
        // 팔로워 · 팔로잉 수는 내 공개 프로필에서
        final profile = await state.api.get('/api/users/${user.id}/profile') as Map<String, dynamic>;
        return _MyData(
          wallet,
          tier,
          followers: (profile['followerCount'] as num).toInt(),
          following: (profile['followingCount'] as num).toInt(),
          badges: ((profile['badges'] as List?) ?? []).cast<Map<String, dynamic>>(),
          equippedBadge: profile['equippedBadge'] as String?,
        );
      },
      builder: (context, data, reload) => ListView(padding: const EdgeInsets.fromLTRB(20, 4, 20, 28), children: [
        // 프로필: 누르면 프로필 수정 (사진 · 닉네임 · 소개 · 뱃지 착용)
        AppCard(
          bordered: false,
          padding: const EdgeInsets.all(18),
          onTap: () => open(const ProfileEditScreen()).then((_) => reload()),
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
                  // 착용한 뱃지 (프로필 수정에서 착용 · 해제)
                  if (data.equippedBadge != null) ...[
                    const SizedBox(width: 6),
                    BadgeMedal(data.equippedBadge, earned: true, size: 22, showLabel: false),
                  ],
                  // 이름 옆 칭호 (웹의 프로필 카드와 같다). 관리자는 칭호 대신 '관리자'
                  if (admin)
                    ...[const SizedBox(width: 7), ColorTag.of('관리자', ColorTag.gold)]
                  else if (data.tier?['tier'] case {'name': final String tierName})
                    ...[const SizedBox(width: 7), ColorTag.of(tierName, ColorTag.gold)],
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
                const SizedBox(height: 8),
                // 팔로워 · 팔로잉: 누르면 목록으로
                Row(children: [
                  _follow(context, '팔로워', data.followers, true, reload),
                  const SizedBox(width: 16),
                  _follow(context, '팔로잉', data.following, false, reload),
                ]),
              ]),
            ),
            const Icon(Icons.chevron_right, color: Color(0xFFC7C0B0), size: 20),
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
        if (data.badges.isNotEmpty) ...[
          const SizedBox(height: 16),
          _BadgeCard(data.badges, onAll: () => open(const BadgesScreen())),
        ],
        if (!user.phoneVerified) ...[
          const SizedBox(height: 16),
          AppCard(
            bordered: false,
            padding: const EdgeInsets.all(16),
            onTap: () => verifyPhone(context),
            child: const Text('휴대폰 본인인증을 해야 챌린지에 참여할 수 있어요. 여기를 눌러 인증해 주세요.',
                style: TextStyle(fontSize: 12.5, fontWeight: FontWeight.w600, height: 1.5)),
          ),
        ],
        const SizedBox(height: 16),
        // 메뉴
        AppCard(
          bordered: false,
          padding: EdgeInsets.zero,
          child: Column(children: [
            if (admin)
              _menu(Icons.admin_panel_settings, ColorTag.gold, '관리자 · 검토 · 신고 · 문의',
                  () => open(const AdminScreen())),
            _menu(Icons.flag, ColorTag.coral, '내 챌린지', () => open(const MyChallengesScreen())),
            _menu(Icons.emoji_events, ColorTag.gold, '랭킹', () => open(const RankingScreen())),
            _menu(Icons.insights, ColorTag.coral, '주간 회고', () => open(const WeeklyReportScreen())),
            _menu(Icons.account_balance_wallet, ColorTag.gold, '포인트 지갑 · 거래 내역', () => open(const WalletScreen())),
            _menu(Icons.shopping_bag, ColorTag.mint, '포인트 상점', () => openWeb('/shop')),
            _menu(Icons.notifications, ColorTag.lav, '알림', () => open(const NotificationsScreen())),
            // 설정에서 닉네임 · 착용한 뱃지를 바꾸고 돌아오면 다시 불러온다
            _menu(Icons.settings, (AppColors.surfaceAlt, AppColors.brown), '설정',
                () => open(const SettingsScreen()).then((_) => reload())),
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

  Widget _follow(BuildContext context, String label, int count, bool followers, Future<void> Function() reload) {
    return GestureDetector(
      behavior: HitTestBehavior.opaque,
      onTap: () async {
        await Navigator.of(context)
            .push(MaterialPageRoute(builder: (_) => FollowListScreen(followers: followers)));
        reload();
      },
      child: Padding(
        padding: const EdgeInsets.symmetric(vertical: 2),
        child: Row(mainAxisSize: MainAxisSize.min, children: [
          Text('$count', style: const TextStyle(fontSize: 13.5, fontWeight: FontWeight.w800)),
          const SizedBox(width: 4),
          Text(label, style: const TextStyle(color: AppColors.muted, fontSize: 12, fontWeight: FontWeight.w700)),
        ]),
      ),
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
  _MyData(this.wallet, this.tier,
      {required this.followers,
      required this.following,
      required this.badges,
      required this.equippedBadge});

  final Wallet wallet;
  final Map<String, dynamic>? tier;
  final int followers;
  final int following;

  /// 전체 뱃지 (딴 것 · 아직인 것)
  final List<Map<String, dynamic>> badges;

  /// 착용한 뱃지 코드 (없으면 null)
  final String? equippedBadge;
}

/// 뱃지 줄 (웹 마이페이지와 같다): 미리보기 7개를 옆으로 넘겨 본다. 딴 것 먼저 또렷하게, 남는 자리는 아직인 것을 흐리게 + 따는 조건
class _BadgeCard extends StatelessWidget {
  const _BadgeCard(this.badges, {required this.onAll});

  final List<Map<String, dynamic>> badges;
  final VoidCallback onAll;

  static const _preview = 7;

  @override
  Widget build(BuildContext context) {
    final earned = badges.where((b) => b['earned'] == true).toList();
    final shown = [...earned, ...badges.where((b) => b['earned'] != true)].take(_preview).toList();
    return AppCard(
      bordered: false,
      padding: const EdgeInsets.fromLTRB(0, 16, 0, 14),
      child: Column(crossAxisAlignment: CrossAxisAlignment.start, children: [
        Padding(
          padding: const EdgeInsets.symmetric(horizontal: 16),
          child: Row(crossAxisAlignment: CrossAxisAlignment.end, children: [
            const Text('뱃지', style: TextStyle(fontSize: 15, fontWeight: FontWeight.w800, letterSpacing: -0.2)),
            const SizedBox(width: 6),
            Text('${earned.length}/${badges.length}',
                style: const TextStyle(color: AppColors.muted, fontSize: 12.5, fontWeight: FontWeight.w700)),
            const Spacer(),
            GestureDetector(
              behavior: HitTestBehavior.opaque,
              onTap: onAll,
              child: const Text('전체 보기 ›',
                  style: TextStyle(color: AppColors.brown, fontSize: 12.5, fontWeight: FontWeight.w700)),
            ),
          ]),
        ),
        const SizedBox(height: 12),
        SizedBox(
          height: 138,
          child: ListView.separated(
            scrollDirection: Axis.horizontal,
            padding: const EdgeInsets.symmetric(horizontal: 16),
            itemCount: shown.length,
            separatorBuilder: (_, _) => const SizedBox(width: 10),
            itemBuilder: (_, i) => _tile(shown[i]),
          ),
        ),
      ]),
    );
  }

  Widget _tile(Map<String, dynamic> b) {
    final earned = b['earned'] == true;
    return Container(
      width: 104,
      padding: const EdgeInsets.fromLTRB(6, 11, 6, 8),
      decoration: BoxDecoration(
        color: earned ? const Color(0xFFFDF8E7) : AppColors.surfaceAlt,
        border: Border.all(color: earned ? const Color(0xFFEACF93) : const Color(0xFFE6E0D2)),
        borderRadius: BorderRadius.circular(14),
      ),
      child: Column(children: [
        BadgeMedal(b['code'] as String?, earned: earned, size: 50),
        const SizedBox(height: 6),
        Text(b['name'] as String,
            maxLines: 1,
            overflow: TextOverflow.ellipsis,
            style: TextStyle(
                fontSize: 12.5, fontWeight: FontWeight.w800, color: earned ? AppColors.text : AppColors.muted)),
        const SizedBox(height: 3),
        Text(earned ? '획득' : b['description'] as String,
            maxLines: 2,
            overflow: TextOverflow.ellipsis,
            textAlign: TextAlign.center,
            style: TextStyle(
                fontSize: 10.5,
                height: 1.35,
                fontWeight: earned ? FontWeight.w800 : FontWeight.w600,
                color: earned ? const Color(0xFFB07A12) : AppColors.muted)),
      ]),
    );
  }
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
