import 'package:flutter/foundation.dart';

import 'api/api_client.dart';
import 'api/models.dart';

/// 로그인 상태. 앱을 켜면 저장된 리프레시 쿠키로 조용히 다시 로그인해 본다 (자동 로그인).
class AppState extends ChangeNotifier {
  AppState(this.api) {
    api.onSessionExpired = _expire;
  }

  final ApiClient api;

  User? user;
  bool booting = true;

  bool get loggedIn => user != null;

  Future<void> boot() async {
    try {
      await api.refresh();
      user = User.fromJson(await api.get('/api/users/me') as Map<String, dynamic>);
    } catch (_) {
      user = null;
    }
    booting = false;
    notifyListeners();
  }

  Future<void> login(String email, String password) async {
    await api.login(email, password);
    user = User.fromJson(await api.get('/api/users/me') as Map<String, dynamic>);
    notifyListeners();
  }

  Future<void> logout() async {
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
