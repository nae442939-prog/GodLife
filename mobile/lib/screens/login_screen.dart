import 'package:flutter/foundation.dart' show kIsWeb;
import 'package:flutter/material.dart';
import 'package:provider/provider.dart';
import 'package:url_launcher/url_launcher.dart';

import '../api/api_client.dart';
import '../app_state.dart';
import '../theme.dart';

/// 이메일 로그인 + 소셜 로그인(카카오 · 네이버 · 구글). 회원가입 · 비밀번호 찾기는 웹 화면으로 보낸다.
class LoginScreen extends StatefulWidget {
  const LoginScreen({super.key});

  @override
  State<LoginScreen> createState() => _LoginScreenState();
}

class _LoginScreenState extends State<LoginScreen> {
  final _email = TextEditingController();
  final _password = TextEditingController();
  bool _busy = false;
  String? _error;

  @override
  void dispose() {
    _email.dispose();
    _password.dispose();
    super.dispose();
  }

  Future<void> _submit() async {
    if (_email.text.trim().isEmpty || _password.text.isEmpty) {
      setState(() => _error = '이메일과 비밀번호를 입력해 주세요.');
      return;
    }
    setState(() {
      _busy = true;
      _error = null;
    });
    try {
      await context.read<AppState>().login(_email.text.trim(), _password.text);
    } catch (e) {
      if (mounted) setState(() => _error = '$e');
    } finally {
      if (mounted) setState(() => _busy = false);
    }
  }

  /// 웹에만 있는 화면(가입 · 소셜 로그인)을 같은 창에서 연다
  Future<void> _openWeb(String path) async {
    await launchUrl(Uri.parse('$serverBase$path'), webOnlyWindowName: kIsWeb ? '_self' : null);
  }

  Widget _social(String label, Color background, Color foreground, String provider) {
    return Tooltip(
      message: '$label 로그인',
      child: InkWell(
        customBorder: const CircleBorder(),
        onTap: () => _openWeb('/oauth2/authorization/$provider'),
        child: Container(
          width: 52,
          height: 52,
          alignment: Alignment.center,
          decoration: BoxDecoration(
            color: background,
            shape: BoxShape.circle,
            border: Border.all(color: AppColors.border),
          ),
          child: Text(label.substring(0, 1),
              style: TextStyle(color: foreground, fontWeight: FontWeight.w900, fontSize: 18)),
        ),
      ),
    );
  }

  @override
  Widget build(BuildContext context) {
    return Scaffold(
      body: SafeArea(
        child: Center(
          child: SingleChildScrollView(
            padding: const EdgeInsets.all(24),
            child: AutofillGroup(
              child: Column(crossAxisAlignment: CrossAxisAlignment.stretch, children: [
                Row(mainAxisAlignment: MainAxisAlignment.center, children: [
                  Container(
                    width: 18,
                    height: 18,
                    decoration: BoxDecoration(color: AppColors.primary, borderRadius: BorderRadius.circular(6)),
                  ),
                  const SizedBox(width: 8),
                  const Text('갓생살기', style: TextStyle(fontSize: 28, fontWeight: FontWeight.w900)),
                ]),
                const SizedBox(height: 8),
                const Text('오늘의 습관에 도전하고, 성공하면 돌려받으세요',
                    textAlign: TextAlign.center, style: TextStyle(color: AppColors.muted)),
                const SizedBox(height: 36),
                TextField(
                  controller: _email,
                  keyboardType: TextInputType.emailAddress,
                  autofillHints: const [AutofillHints.email],
                  textInputAction: TextInputAction.next,
                  decoration: const InputDecoration(labelText: '이메일'),
                ),
                const SizedBox(height: 12),
                TextField(
                  controller: _password,
                  obscureText: true,
                  autofillHints: const [AutofillHints.password],
                  onSubmitted: (_) => _submit(),
                  decoration: const InputDecoration(labelText: '비밀번호'),
                ),
                if (_error != null) ...[
                  const SizedBox(height: 12),
                  Text(_error!, style: const TextStyle(color: AppColors.danger)),
                ],
                const SizedBox(height: 20),
                FilledButton(
                  onPressed: _busy ? null : _submit,
                  child: Text(_busy ? '로그인하는 중…' : '로그인'),
                ),
                const SizedBox(height: 16),
                // 소셜 로그인: 서버가 로그인 뒤 리프레시 쿠키를 심고 돌려보내면 앱이 켜지면서 자동으로 로그인된다
                Row(mainAxisAlignment: MainAxisAlignment.center, children: [
                  _social('카카오', const Color(0xFFFEE500), Colors.black87, 'kakao'),
                  const SizedBox(width: 14),
                  _social('네이버', const Color(0xFF03C75A), Colors.white, 'naver'),
                  const SizedBox(width: 14),
                  _social('구글', Colors.white, Colors.black87, 'google'),
                ]),
                const SizedBox(height: 14),
                Wrap(alignment: WrapAlignment.center, crossAxisAlignment: WrapCrossAlignment.center, children: [
                  TextButton(onPressed: () => _openWeb('/signup'), child: const Text('회원가입')),
                  TextButton(onPressed: () => _openWeb('/find-id'), child: const Text('아이디 찾기')),
                  TextButton(onPressed: () => _openWeb('/find-password'), child: const Text('비밀번호 찾기')),
                ]),
              ]),
            ),
          ),
        ),
      ),
    );
  }
}
