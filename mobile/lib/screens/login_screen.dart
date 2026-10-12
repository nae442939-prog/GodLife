import 'package:flutter/foundation.dart' show kIsWeb;
import 'package:flutter/material.dart';
import 'package:flutter_svg/flutter_svg.dart';
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

  // 소셜 로그인 버튼의 로고 (웹의 SocialLoginButtons.jsx 와 같은 그림).
  // 각 회사가 로그인 버튼에 쓰라고 정해 둔 모양 · 색이다: 카카오 말풍선(#FEE500 바탕), 네이버 N(#03C75A 바탕), 구글 G(흰 바탕)
  static const _logos = {
    'kakao': '<svg viewBox="0 0 24 24"><path fill="#191919" d="M12 4C7.03 4 3 7.13 3 11c0 2.49 1.66 4.67 4.16 5.9l-.9 3.3c-.08.3.25.54.51.37l3.94-2.6c.42.04.85.06 1.29.06 4.97 0 9-3.13 9-7s-4.03-7-9-7z"/></svg>',
    'naver': '<svg viewBox="0 0 24 24"><path fill="#ffffff" d="M15.2 12.6 8.5 3H3v18h5.8v-9.6L15.5 21H21V3h-5.8z"/></svg>',
    'google': '<svg viewBox="0 0 18 18">'
        '<path fill="#EA4335" d="M9 3.48c1.69 0 2.83.73 3.48 1.34l2.54-2.48C13.46.89 11.43 0 9 0 5.48 0 2.44 2.02.96 4.96l2.91 2.26C4.6 5.05 6.62 3.48 9 3.48z"/>'
        '<path fill="#4285F4" d="M17.64 9.2c0-.74-.06-1.28-.19-1.84H9v3.34h4.96c-.1.83-.64 2.08-1.84 2.92l2.84 2.2c1.7-1.57 2.68-3.88 2.68-6.62z"/>'
        '<path fill="#FBBC05" d="M3.88 10.78A5.54 5.54 0 0 1 3.58 9c0-.62.11-1.22.29-1.78L.96 4.96A9.008 9.008 0 0 0 0 9c0 1.45.35 2.82.96 4.04l2.92-2.26z"/>'
        '<path fill="#34A853" d="M9 18c2.43 0 4.47-.8 5.96-2.18l-2.84-2.2c-.76.53-1.78.9-3.12.9-2.38 0-4.4-1.57-5.12-3.74L.97 13.04C2.45 15.98 5.48 18 9 18z"/>'
        '</svg>',
  };

  Widget _social(String label, Color background, Color foreground, String provider) {
    // 로고 크기는 웹과 같은 비율 (카카오 26 · 네이버 20 · 구글 22)
    final size = switch (provider) { 'kakao' => 26.0, 'naver' => 20.0, _ => 22.0 };
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
            border: Border.all(color: AppColors.cardBorder),
            boxShadow: cardShadow,
          ),
          child: SvgPicture.string(_logos[provider]!, width: size, height: size),
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
