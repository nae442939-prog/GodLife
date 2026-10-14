import 'package:flutter/material.dart';
import 'package:provider/provider.dart';

import '../api/api_client.dart';
import '../app_state.dart';
import '../theme.dart';
import '../widgets/async_view.dart';
import '../widgets/design.dart';
import '../widgets/phone_verify_sheet.dart';
import 'profile_edit_screen.dart';

/// 설정: 프로필 · 알림 · 보안(자동 로그인 · 비밀번호) · 차단한 사용자 · 고객센터 · 탈퇴 · 로그아웃.
/// 휴대폰 번호 변경 · 1:1 문의 · 회원 탈퇴는 확인 절차가 긴 화면이라 웹 설정으로 보낸다.
class SettingsScreen extends StatefulWidget {
  const SettingsScreen({super.key});

  @override
  State<SettingsScreen> createState() => _SettingsScreenState();
}

class _SettingsScreenState extends State<SettingsScreen> {
  ApiClient get _api => context.read<AppState>().api;

  Future<_SettingsData> _load() async {
    final results = await Future.wait([
      _api.get('/api/notifications/settings'),
      _api.get('/api/users/me/account'),
      _api.get('/api/users/me/blocks'),
    ]);
    return _SettingsData(
      alarm: Map<String, dynamic>.from(results[0] as Map),
      account: Map<String, dynamic>.from(results[1] as Map),
      blocks: (results[2] as List).cast<Map<String, dynamic>>(),
    );
  }

  Future<void> _setAlarm(_SettingsData data, String key, bool value) async {
    final before = data.alarm[key];
    setState(() => data.alarm[key] = value);
    try {
      await _api.put('/api/notifications/settings', body: data.alarm);
    } catch (e) {
      setState(() => data.alarm[key] = before);
      if (mounted) showMessage(context, '$e');
    }
  }

  Future<void> _setAutoLogin(bool value) async {
    final state = context.read<AppState>();
    try {
      await _api.put('/api/users/me/auto-login', body: {'enabled': value});
      // 쿠키 수명은 토큰을 다시 받을 때 바뀐다
      await _api.refresh();
      await state.reloadUser();
    } catch (e) {
      if (mounted) showMessage(context, '$e');
    }
  }

  Future<void> _unblock(_SettingsData data, Map<String, dynamic> user) async {
    try {
      await _api.delete('/api/users/me/blocks/${user['userId']}');
      setState(() => data.blocks.remove(user));
      if (mounted) showMessage(context, '${user['nickname']}님의 차단을 해제했어요.');
    } catch (e) {
      if (mounted) showMessage(context, '$e');
    }
  }

  @override
  Widget build(BuildContext context) {
    final state = context.watch<AppState>();
    final user = state.user;
    if (user == null) return const SizedBox.shrink();

    return Scaffold(
      appBar: AppBar(title: const Text('설정')),
      body: SafeArea(
        child: AsyncView<_SettingsData>(
          load: _load,
          builder: (context, data, reload) {
            final hasPassword = data.account['hasPassword'] == true;
            final socials = ((data.account['socialProviders'] as List?) ?? []).join(' · ');
            return ListView(padding: const EdgeInsets.fromLTRB(20, 4, 20, 32), children: [
              _title('프로필'),
              AppCard(
                padding: EdgeInsets.zero,
                child: Column(children: [
                  _row(Icons.badge_outlined, '프로필 수정 (사진 · 닉네임 · 소개 · 뱃지)',
                      value: user.nickname,
                      onTap: () => Navigator.of(context)
                          .push(MaterialPageRoute(builder: (_) => const ProfileEditScreen()))),
                  _line(),
                  _row(Icons.mail_outline, '이메일', value: user.email ?? '-'),
                  if (socials.isNotEmpty) ...[_line(), _row(Icons.link, '연결된 소셜 계정', value: socials)],
                  _line(),
                  _row(Icons.phone_iphone, '휴대폰 번호',
                      value: user.phoneVerified ? '인증 완료' : '인증 필요',
                      onTap: user.phoneVerified ? null : () => verifyPhone(context)),
                ]),
              ),
              _title('알림'),
              AppCard(
                padding: EdgeInsets.zero,
                child: Column(children: [
                  _switch('오늘 인증하는 날', '인증을 아직 안 했을 때 알려 드려요', data.alarm['verifyReminder'] == true,
                      (v) => _setAlarm(data, 'verifyReminder', v)),
                  _line(),
                  _switch('챌린지 결과', '챌린지가 끝나고 정산되면 알려 드려요', data.alarm['challengeResult'] == true,
                      (v) => _setAlarm(data, 'challengeResult', v)),
                  _line(),
                  _switch('팔로우 · 메시지', '팔로우하거나 메시지 요청이 오면 알려 드려요', data.alarm['social'] == true,
                      (v) => _setAlarm(data, 'social', v)),
                ]),
              ),
              _title('보안'),
              AppCard(
                padding: EdgeInsets.zero,
                child: Column(children: [
                  _switch('자동 로그인', '앱을 닫았다 열어도 로그인이 유지돼요', user.autoLogin, _setAutoLogin),
                  _line(),
                  hasPassword
                      ? _row(Icons.lock_outline, '비밀번호 변경', onTap: () => _changePassword())
                      : _row(Icons.lock_outline, '비밀번호', value: '소셜 로그인 계정이에요'),
                ]),
              ),
              _title('차단한 사용자'),
              AppCard(
                padding: EdgeInsets.zero,
                child: data.blocks.isEmpty
                    ? const Padding(
                        padding: EdgeInsets.all(16),
                        child: Row(children: [
                          Text('차단한 사용자가 없어요.', style: TextStyle(color: AppColors.muted, fontSize: 13.5)),
                        ]),
                      )
                    : Column(children: [
                        for (final (i, b) in data.blocks.indexed) ...[
                          if (i > 0) _line(),
                          Padding(
                            padding: const EdgeInsets.fromLTRB(16, 6, 8, 6),
                            child: Row(children: [
                              Expanded(
                                child: Text('${b['nickname']}', style: const TextStyle(fontWeight: FontWeight.w700)),
                              ),
                              TextButton(onPressed: () => _unblock(data, b), child: const Text('차단 해제')),
                            ]),
                          ),
                        ],
                      ]),
              ),
              _title('도움말'),
              AppCard(
                padding: EdgeInsets.zero,
                child: Column(children: [
                  _row(Icons.support_agent, '고객센터 · 1:1 문의', onTap: () => openWeb('/settings?tab=support')),
                  _line(),
                  _row(Icons.person_remove_outlined, '회원 탈퇴', onTap: () => openWeb('/settings?tab=withdraw')),
                ]),
              ),
              const SizedBox(height: 20),
              OutlinedButton(
                onPressed: () async {
                  await state.logout();
                  if (context.mounted) Navigator.of(context).popUntil((route) => route.isFirst);
                },
                style: OutlinedButton.styleFrom(
                  minimumSize: const Size.fromHeight(50),
                  side: const BorderSide(color: AppColors.cardBorder),
                  backgroundColor: AppColors.surface,
                  shape: RoundedRectangleBorder(borderRadius: BorderRadius.circular(999)),
                ),
                child: const Text('로그아웃', style: TextStyle(color: AppColors.text, fontWeight: FontWeight.w800)),
              ),
            ]);
          },
        ),
      ),
    );
  }

  /// 비밀번호 변경. 성공하면 모든 기기에서 로그아웃된다
  Future<void> _changePassword() async {
    final state = context.read<AppState>();
    final current = TextEditingController();
    final next = TextEditingController();
    await _sheet(
      title: '비밀번호 변경',
      fields: [
        TextField(controller: current, obscureText: true, decoration: const InputDecoration(labelText: '현재 비밀번호')),
        const SizedBox(height: 12),
        TextField(
            controller: next, obscureText: true, decoration: const InputDecoration(labelText: '새 비밀번호 (8~20자)')),
        const SizedBox(height: 8),
        const Text('비밀번호를 바꾸면 모든 기기에서 로그아웃돼요.', style: TextStyle(color: AppColors.muted, fontSize: 12.5)),
      ],
      action: '변경하기',
      onSubmit: () async {
        await _api.put('/api/users/me/password', body: {'currentPassword': current.text, 'newPassword': next.text});
        await state.logout();
        if (mounted) Navigator.of(context).popUntil((route) => route.isFirst);
        return '비밀번호를 바꿨어요. 다시 로그인해 주세요.';
      },
    );
  }

  /// 아래에서 올라오는 입력 창. onSubmit 이 돌려준 문구를 안내로 띄운다
  Future<void> _sheet({
    required String title,
    required List<Widget> fields,
    required String action,
    required Future<String> Function() onSubmit,
  }) async {
    final messenger = ScaffoldMessenger.of(context);
    final done = await showModalBottomSheet<String>(
      context: context,
      isScrollControlled: true,
      backgroundColor: AppColors.bg,
      builder: (sheetContext) {
        String? error;
        var busy = false;
        return StatefulBuilder(
          builder: (sheetContext, setSheet) => Padding(
            padding: EdgeInsets.fromLTRB(20, 22, 20, 20 + MediaQuery.of(sheetContext).viewInsets.bottom),
            child: Column(mainAxisSize: MainAxisSize.min, crossAxisAlignment: CrossAxisAlignment.stretch, children: [
              Text(title, style: const TextStyle(fontSize: 18, fontWeight: FontWeight.w900)),
              const SizedBox(height: 16),
              ...fields,
              if (error != null)
                Padding(
                  padding: const EdgeInsets.only(top: 8),
                  child: Text(error!, style: const TextStyle(color: AppColors.danger, fontWeight: FontWeight.w600)),
                ),
              const SizedBox(height: 14),
              FilledButton(
                onPressed: busy
                    ? null
                    : () async {
                        setSheet(() {
                          busy = true;
                          error = null;
                        });
                        try {
                          final message = await onSubmit();
                          if (sheetContext.mounted && Navigator.of(sheetContext).canPop()) {
                            Navigator.of(sheetContext).pop(message);
                          }
                        } catch (e) {
                          setSheet(() {
                            busy = false;
                            error = '$e';
                          });
                        }
                      },
                child: Text(busy ? '처리하는 중…' : action),
              ),
            ]),
          ),
        );
      },
    );
    if (done != null) {
      messenger
        ..hideCurrentSnackBar()
        ..showSnackBar(SnackBar(content: Text(done), behavior: SnackBarBehavior.floating));
    }
  }

  Widget _title(String text) {
    return Padding(
      padding: const EdgeInsets.fromLTRB(4, 22, 4, 10),
      child: Text(text, style: const TextStyle(fontSize: 13, fontWeight: FontWeight.w800, color: AppColors.chipText)),
    );
  }

  Widget _line() => const Divider(height: 1, indent: 16, endIndent: 16, color: AppColors.border);

  Widget _row(IconData icon, String label, {String? value, VoidCallback? onTap}) {
    return InkWell(
      onTap: onTap,
      child: Padding(
        padding: const EdgeInsets.fromLTRB(16, 15, 12, 15),
        child: Row(children: [
          Icon(icon, size: 20, color: AppColors.brown),
          const SizedBox(width: 12),
          Text(label, style: const TextStyle(fontSize: 14.5, fontWeight: FontWeight.w700)),
          const SizedBox(width: 12),
          Expanded(
            child: Text(value ?? '',
                textAlign: TextAlign.right,
                overflow: TextOverflow.ellipsis,
                style: const TextStyle(fontSize: 13, color: AppColors.muted, fontWeight: FontWeight.w600)),
          ),
          if (onTap != null) const Icon(Icons.chevron_right, size: 20, color: AppColors.navIdle),
        ]),
      ),
    );
  }

  Widget _switch(String label, String hint, bool value, ValueChanged<bool> onChanged) {
    return Padding(
      padding: const EdgeInsets.fromLTRB(16, 10, 10, 10),
      child: Row(children: [
        Expanded(
          child: Column(crossAxisAlignment: CrossAxisAlignment.start, children: [
            Text(label, style: const TextStyle(fontSize: 14.5, fontWeight: FontWeight.w700)),
            const SizedBox(height: 2),
            Text(hint, style: const TextStyle(fontSize: 12, color: AppColors.muted, fontWeight: FontWeight.w500)),
          ]),
        ),
        Switch(
          value: value,
          onChanged: onChanged,
          activeTrackColor: AppColors.mint,
          activeThumbColor: Colors.white,
          inactiveTrackColor: AppColors.surfaceAlt,
          inactiveThumbColor: Colors.white,
          trackOutlineColor: const WidgetStatePropertyAll(Colors.transparent),
        ),
      ]),
    );
  }
}

class _SettingsData {
  _SettingsData({required this.alarm, required this.account, required this.blocks});

  final Map<String, dynamic> alarm;
  final Map<String, dynamic> account;
  final List<Map<String, dynamic>> blocks;
}
