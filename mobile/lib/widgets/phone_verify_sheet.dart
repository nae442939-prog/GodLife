import 'package:flutter/material.dart';
import 'package:provider/provider.dart';

import '../app_state.dart';
import '../theme.dart';

/// 휴대폰 본인인증 (앱 안에서): 번호 → 인증번호 받기 → 확인 → 내 계정에 번호 등록.
/// 소셜 로그인으로 가입하면 번호가 없어 챌린지에 참여할 수 없으므로 참여 · 설정 · 마이페이지에서 띄운다.
/// 데모 모드(문자를 실제로 보내지 않음)면 받은 인증번호를 칸에 바로 채워 준다.
/// 등록까지 마치면 true.
Future<bool> verifyPhone(BuildContext context) async {
  final done = await showModalBottomSheet<bool>(
    context: context,
    isScrollControlled: true,
    backgroundColor: AppColors.bg,
    builder: (_) => const _PhoneVerifySheet(),
  );
  return done == true;
}

class _PhoneVerifySheet extends StatefulWidget {
  const _PhoneVerifySheet();

  @override
  State<_PhoneVerifySheet> createState() => _PhoneVerifySheetState();
}

class _PhoneVerifySheetState extends State<_PhoneVerifySheet> {
  final _phone = TextEditingController();
  final _code = TextEditingController();
  bool _sent = false;
  bool _demo = false;
  bool _busy = false;
  String? _error;

  @override
  void dispose() {
    _phone.dispose();
    _code.dispose();
    super.dispose();
  }

  String get _number => _phone.text.replaceAll(RegExp(r'[^0-9]'), '');

  Future<void> _run(Future<void> Function() step) async {
    setState(() {
      _busy = true;
      _error = null;
    });
    try {
      await step();
    } catch (e) {
      if (mounted) setState(() => _error = '$e');
    } finally {
      if (mounted) setState(() => _busy = false);
    }
  }

  Future<void> _send() => _run(() async {
        final api = context.read<AppState>().api;
        final res = await api.post('/api/phone-verifications', body: {'phone': _number}, auth: false) as Map;
        final demoCode = res['demoCode'] as String?;
        setState(() {
          _sent = true;
          _demo = demoCode != null;
          if (demoCode != null) _code.text = demoCode;
        });
      });

  Future<void> _confirm() => _run(() async {
        final state = context.read<AppState>();
        final proof = await state.api.post('/api/phone-verifications/confirm',
            body: {'phone': _number, 'code': _code.text.trim()}, auth: false) as Map;
        await state.api.post('/api/users/me/phone', body: {'phoneProof': proof['phoneProof']});
        await state.reloadUser();
        if (mounted) Navigator.of(context).pop(true);
      });

  @override
  Widget build(BuildContext context) {
    return Padding(
      padding: EdgeInsets.fromLTRB(20, 22, 20, 20 + MediaQuery.of(context).viewInsets.bottom),
      child: Column(mainAxisSize: MainAxisSize.min, crossAxisAlignment: CrossAxisAlignment.stretch, children: [
        const Text('휴대폰 본인인증', style: TextStyle(fontSize: 18, fontWeight: FontWeight.w900)),
        const SizedBox(height: 6),
        const Text('챌린지에 참여하려면 한 번만 인증하면 돼요. 번호 하나에 계정 하나만 인증할 수 있어요.',
            style: TextStyle(color: AppColors.muted, fontSize: 12.5, fontWeight: FontWeight.w600, height: 1.5)),
        const SizedBox(height: 16),
        TextField(
          controller: _phone,
          enabled: !_sent,
          keyboardType: TextInputType.phone,
          decoration: const InputDecoration(labelText: '휴대폰 번호', hintText: '01012345678'),
        ),
        if (_sent) ...[
          const SizedBox(height: 10),
          TextField(
            controller: _code,
            keyboardType: TextInputType.number,
            maxLength: 6,
            decoration: const InputDecoration(labelText: '인증번호', counterText: ''),
          ),
          if (_demo)
            const Padding(
              padding: EdgeInsets.only(top: 6),
              child: Text('테스트 버전이라 문자를 보내지 않고 인증번호를 바로 채워 드렸어요.',
                  style: TextStyle(color: AppColors.mint, fontSize: 12, fontWeight: FontWeight.w700)),
            ),
        ],
        if (_error != null)
          Padding(
            padding: const EdgeInsets.only(top: 8),
            child: Text(_error!, style: const TextStyle(color: AppColors.danger, fontWeight: FontWeight.w600)),
          ),
        const SizedBox(height: 14),
        FilledButton(
          onPressed: _busy ? null : (_sent ? _confirm : _send),
          child: Text(_busy ? '잠시만요…' : (_sent ? '인증하고 등록하기' : '인증번호 받기')),
        ),
        if (_sent)
          TextButton(
            onPressed: _busy
                ? null
                : () => setState(() {
                      _sent = false;
                      _code.clear();
                    }),
            child: const Text('번호 다시 입력하기'),
          ),
      ]),
    );
  }
}
