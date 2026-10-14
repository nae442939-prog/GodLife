import 'package:flutter/material.dart';
import 'package:provider/provider.dart';
import 'package:shared_preferences/shared_preferences.dart';

import '../app_state.dart';
import '../theme.dart';
import 'badge_medal.dart';

/// 이 기기에서 이미 축하한 뱃지 코드 (회원마다)
String _seenKey(int userId) => 'seenBadges.$userId';

/// 새로 딴 뱃지가 있으면 '뱃지를 받았어요!' 창을 띄운다. 앱을 열 때 · 인증 · 참여를 마친 뒤 부른다.
/// 뱃지는 서버가 기록으로 그때그때 계산하므로, 이미 축하한 뱃지는 기기에 적어 두고 새로 생긴 것만 띄운다.
/// 이 기기에서 처음 확인할 때는 지금 가진 뱃지를 적어만 둔다 (예전에 딴 뱃지를 한꺼번에 축하하지 않게).
Future<void> checkNewBadges(BuildContext context) async {
  final state = context.read<AppState>();
  final user = state.user;
  if (user == null) return;
  try {
    final profile = await state.api.get('/api/users/${user.id}/profile') as Map<String, dynamic>;
    final earned =
        ((profile['badges'] as List?) ?? []).cast<Map<String, dynamic>>().where((b) => b['earned'] == true).toList();
    final prefs = await SharedPreferences.getInstance();
    final seen = prefs.getStringList(_seenKey(user.id));
    await prefs.setStringList(_seenKey(user.id), [for (final b in earned) b['code'] as String]);
    if (seen == null) return;
    final fresh = earned.where((b) => !seen.contains(b['code'])).toList();
    if (fresh.isEmpty || !context.mounted) return;
    await showDialog<void>(context: context, builder: (_) => _BadgeDialog(fresh));
  } catch (_) {
    // 축하 창은 덤이라 못 불러오면 조용히 넘어간다
  }
}

/// 회원가입(소셜 가입자는 휴대폰 인증)을 마쳤을 때 한 번 띄우는 환영 창.
/// 주소의 ?welcome=1 은 새로 고쳐도 남아 있으므로, 회원마다 한 번만 띄우도록 기기에 적어 둔다
Future<void> showWelcome(BuildContext context) async {
  final user = context.read<AppState>().user;
  if (user == null) return;
  try {
    final prefs = await SharedPreferences.getInstance();
    if (prefs.getBool('welcomed.${user.id}') == true) return;
    await prefs.setBool('welcomed.${user.id}', true);
  } catch (_) {
    // 적어 두지 못해도 창은 띄운다
  }
  if (!context.mounted) return;
  final nickname = user.nickname;
  await showDialog<void>(
    context: context,
    builder: (context) => _Celebration(
      art: const Text('🎉', style: TextStyle(fontSize: 54)),
      eyebrow: '회원가입 완료',
      title: '$nickname님, 가입을 축하해요!',
      body: Column(children: [
        const Text('갓생살기에 오신 걸 환영해요. 이렇게 시작해 보세요.',
            textAlign: TextAlign.center,
            style: TextStyle(color: AppColors.muted, fontSize: 13, fontWeight: FontWeight.w600)),
        const SizedBox(height: 12),
        Container(
          width: double.infinity,
          padding: const EdgeInsets.fromLTRB(14, 10, 14, 10),
          decoration: BoxDecoration(color: AppColors.surfaceAlt, borderRadius: BorderRadius.circular(12)),
          child: const Text('1. 오늘 시작하는 챌린지에 참여해요\n2. 카메라로 인증 사진을 찍어 올려요\n3. 첫 인증에 성공하면 첫 뱃지를 받아요',
              style: TextStyle(fontSize: 12.5, fontWeight: FontWeight.w700, height: 1.8)),
        ),
      ]),
      actions: [FilledButton(onPressed: () => Navigator.of(context).pop(), child: const Text('시작하기'))],
    ),
  );
}

class _BadgeDialog extends StatefulWidget {
  const _BadgeDialog(this.fresh);

  final List<Map<String, dynamic>> fresh;

  @override
  State<_BadgeDialog> createState() => _BadgeDialogState();
}

class _BadgeDialogState extends State<_BadgeDialog> {
  bool _busy = false;
  bool _worn = false;
  String? _error;

  Map<String, dynamic> get _badge => widget.fresh.first;

  Future<void> _wear() async {
    setState(() {
      _busy = true;
      _error = null;
    });
    try {
      await context.read<AppState>().api.put('/api/users/me/badge', body: {'code': _badge['code']});
      if (mounted) setState(() => _worn = true);
    } catch (e) {
      if (mounted) setState(() => _error = '$e');
    } finally {
      if (mounted) setState(() => _busy = false);
    }
  }

  @override
  Widget build(BuildContext context) {
    final more = widget.fresh.length - 1;
    return _Celebration(
      art: BadgeMedal(_badge['code'] as String?, earned: true, size: 92),
      eyebrow: '축하해요!',
      title: '뱃지를 받았어요 🎉',
      body: Column(children: [
        Text(_badge['name'] as String, style: const TextStyle(fontSize: 16, fontWeight: FontWeight.w800)),
        const SizedBox(height: 4),
        Text(_badge['description'] as String,
            textAlign: TextAlign.center,
            style: const TextStyle(color: AppColors.muted, fontSize: 12.5, fontWeight: FontWeight.w600)),
        if (more > 0) ...[
          const SizedBox(height: 6),
          Text('이 밖에도 뱃지 $more개를 더 받았어요.',
              style: const TextStyle(color: AppColors.muted, fontSize: 12, fontWeight: FontWeight.w600)),
        ],
        if (_error != null) ...[
          const SizedBox(height: 8),
          Text(_error!, style: const TextStyle(color: AppColors.danger, fontSize: 12, fontWeight: FontWeight.w600)),
        ],
      ]),
      actions: [
        _worn
            ? const Padding(
                padding: EdgeInsets.symmetric(vertical: 10),
                child: Text('착용했어요! 이름 옆에 보여요.',
                    textAlign: TextAlign.center,
                    style: TextStyle(color: AppColors.mint, fontSize: 13, fontWeight: FontWeight.w800)),
              )
            : OutlinedButton(
                style: OutlinedButton.styleFrom(
                  minimumSize: const Size.fromHeight(46),
                  foregroundColor: AppColors.text,
                  side: const BorderSide(color: AppColors.cardBorder),
                  shape: RoundedRectangleBorder(borderRadius: BorderRadius.circular(999)),
                ),
                onPressed: _busy ? null : _wear,
                child: Text(_busy ? '착용하는 중…' : '이름 옆에 착용하기',
                    style: const TextStyle(fontWeight: FontWeight.w800)),
              ),
        const SizedBox(height: 8),
        FilledButton(onPressed: () => Navigator.of(context).pop(), child: const Text('확인')),
      ],
    );
  }
}

/// 축하 창의 공통 모양: 커지며 나타나는 그림 → 작은 제목 → 큰 제목 → 내용 → 버튼
class _Celebration extends StatelessWidget {
  const _Celebration(
      {required this.art, required this.eyebrow, required this.title, required this.body, required this.actions});

  final Widget art;
  final String eyebrow;
  final String title;
  final Widget body;
  final List<Widget> actions;

  @override
  Widget build(BuildContext context) {
    return Dialog(
      backgroundColor: AppColors.bg,
      shape: RoundedRectangleBorder(borderRadius: BorderRadius.circular(24)),
      child: Padding(
        padding: const EdgeInsets.fromLTRB(22, 26, 22, 20),
        child: Column(mainAxisSize: MainAxisSize.min, crossAxisAlignment: CrossAxisAlignment.stretch, children: [
          Center(
            child: TweenAnimationBuilder<double>(
              tween: Tween(begin: 0.4, end: 1),
              duration: const Duration(milliseconds: 500),
              curve: Curves.elasticOut,
              builder: (_, scale, child) => Transform.scale(scale: scale, child: child),
              child: Container(
                width: 116,
                height: 116,
                alignment: Alignment.center,
                decoration: const BoxDecoration(color: Color(0xFFFBF0D2), shape: BoxShape.circle),
                child: art,
              ),
            ),
          ),
          const SizedBox(height: 14),
          Text(eyebrow,
              textAlign: TextAlign.center,
              style: const TextStyle(color: AppColors.brown, fontSize: 12, fontWeight: FontWeight.w800)),
          const SizedBox(height: 4),
          Text(title,
              textAlign: TextAlign.center,
              style: const TextStyle(fontSize: 19, fontWeight: FontWeight.w900, letterSpacing: -0.3)),
          const SizedBox(height: 10),
          body,
          const SizedBox(height: 18),
          ...actions,
        ]),
      ),
    );
  }
}
