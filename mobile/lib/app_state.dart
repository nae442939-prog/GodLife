import 'dart:async';

import 'package:app_links/app_links.dart';
import 'package:flutter/foundation.dart';

import 'api/api_client.dart';
import 'api/models.dart';
import 'push.dart';

/// 로그인 상태. 앱을 켜면 저장된 리프레시 쿠키로 조용히 다시 로그인해 본다 (자동 로그인).
class AppState extends ChangeNotifier {
  AppState(this.api) {
    api.onSessionExpired = _expire;
  }

  final ApiClient api;

  /// 휴대폰 알림(FCM). 로그인하면 이 휴대폰을 서버에 남기고, 로그아웃하면 지운다
  final AppPush push = AppPush();

  User? user;
  bool booting = true;

  bool get loggedIn => user != null;

  /// 소셜 로그인이 실패해서 앱으로 돌아왔을 때 로그인 화면에 보여 줄 말
  String? socialError;

  StreamSubscription<Uri>? _links;

  Future<void> boot() async {
    try {
      await api.refresh();
      user = User.fromJson(await api.get('/api/users/me') as Map<String, dynamic>);
      unawaited(push.register(api));
    } catch (_) {
      user = null;
    }
    booting = false;
    notifyListeners();
  }

  /// 안드로이드 앱: 소셜 로그인은 브라우저 탭에서 하고, 끝나면 godlife://login?code=... 로 앱이 다시 열린다
  void listenSocialLogin() {
    if (kIsWeb) return; // 모바일 웹은 같은 창에서 웹 로그인 흐름을 그대로 탄다
    _links = AppLinks().uriLinkStream.listen((uri) {
      if (uri.scheme == 'godlife' && uri.host == 'login') _finishSocialLogin(uri);
    });
  }

  Future<void> _finishSocialLogin(Uri uri) async {
    final code = uri.queryParameters['code'];
    if (code == null || loggedIn) {
      if (!loggedIn) {
        socialError = '소셜 로그인에 실패했어요. 다시 시도해 주세요.';
        notifyListeners();
      }
      return;
    }
    try {
      await api.appLogin(code);
      user = User.fromJson(await api.get('/api/users/me') as Map<String, dynamic>);
      socialError = null;
      unawaited(push.register(api));
    } catch (_) {
      socialError = '소셜 로그인에 실패했어요. 다시 시도해 주세요.';
    }
    notifyListeners();
  }

  @override
  void dispose() {
    _links?.cancel();
    super.dispose();
  }

  Future<void> login(String email, String password) async {
    await api.login(email, password);
    user = User.fromJson(await api.get('/api/users/me') as Map<String, dynamic>);
    notifyListeners();
    unawaited(push.register(api));
  }

  /// 프로필 · 설정을 바꾼 뒤 내 정보를 다시 읽는다
  Future<void> reloadUser() async {
    user = User.fromJson(await api.get('/api/users/me') as Map<String, dynamic>);
    notifyListeners();
  }

  Future<void> logout() async {
    await push.unregister(api); // 로그인이 살아 있을 때 지워야 해서 로그아웃보다 먼저
    await api.logout();
    user = null;
    notifyListeners();
  }

  void _expire() {
    if (user == null) return;
    user = null;
    notifyListeners();
  }
}
