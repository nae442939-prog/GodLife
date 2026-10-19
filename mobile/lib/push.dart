import 'dart:async';

import 'package:firebase_core/firebase_core.dart';
import 'package:firebase_messaging/firebase_messaging.dart';
import 'package:flutter/foundation.dart';

import 'api/api_client.dart';

/// 앱 푸시(안드로이드, FCM). 로그인하면 이 휴대폰의 FCM 토큰을 서버에 남기고, 로그아웃하면 지운다.
/// 서버가 알림함에 알림을 만들 때 같은 내용이 휴대폰 알림으로 온다 (앱이 꺼져 있어도 안드로이드가 띄운다).
///
/// Firebase 설정값은 저장소에 넣지 않고 빌드할 때 넣는다 (규칙 6):
///   flutter build apk --dart-define-from-file=firebase-defines.json   (mobile/firebase-defines.json 은 .gitignore)
/// 값이 없으면 푸시만 꺼진 채로 앱은 그대로 돈다.
class AppPush {
  static const _apiKey = String.fromEnvironment('FIREBASE_API_KEY');
  static const _appId = String.fromEnvironment('FIREBASE_APP_ID');
  static const _senderId = String.fromEnvironment('FIREBASE_SENDER_ID');
  static const _projectId = String.fromEnvironment('FIREBASE_PROJECT_ID');

  /// 알림을 눌러 앱이 열렸을 때 (link = 열 화면 주소, 예: /challenges/3)
  void Function(String link)? onOpen;

  /// 앱을 보고 있는 중에 알림이 왔을 때 (안드로이드는 이때 알림을 띄우지 않아서 화면 안에 보여 준다)
  void Function(String title, String? body, String link)? onForeground;

  // 토큰: firebase_messaging 17 은 getToken · onTokenRefresh 대신 register() · onRegistered(설치 ID, FID)를 권하지만,
  // 서버는 오래 쓰여 온 FCM 등록 토큰으로 HTTP v1 에 보낸다 (message.token). 그래서 지원 중단 예고된 쪽을 그대로 쓴다.
  bool _ready = false;
  StreamSubscription<String>? _tokenRefresh;

  static bool get configured =>
      !kIsWeb &&
      defaultTargetPlatform == TargetPlatform.android &&
      _apiKey.isNotEmpty &&
      _appId.isNotEmpty &&
      _senderId.isNotEmpty &&
      _projectId.isNotEmpty;

  /// 앱을 켤 때 한 번. 알림을 눌러 앱이 켜졌으면 그 화면을 연다
  Future<void> start() async {
    if (!configured || _ready) return;
    try {
      await Firebase.initializeApp(
        options: const FirebaseOptions(
          apiKey: _apiKey,
          appId: _appId,
          messagingSenderId: _senderId,
          projectId: _projectId,
        ),
      );
      _ready = true;
    } catch (e) {
      debugPrint('Firebase 시작 실패, 앱 푸시를 끕니다: $e');
      return;
    }
    FirebaseMessaging.onMessageOpenedApp.listen((m) => onOpen?.call(_link(m)));
    FirebaseMessaging.onMessage.listen((m) {
      final n = m.notification;
      if (n != null) onForeground?.call(n.title ?? '갓생살기', n.body, _link(m));
    });
    final initial = await FirebaseMessaging.instance.getInitialMessage();
    if (initial != null) onOpen?.call(_link(initial));
  }

  /// 로그인한 뒤: 알림 권한을 묻고(안드로이드 13+) 이 휴대폰의 토큰을 서버에 남긴다. 실패해도 로그인은 그대로
  Future<void> register(ApiClient api) async {
    if (!_ready) return;
    try {
      final settings = await FirebaseMessaging.instance.requestPermission();
      if (settings.authorizationStatus == AuthorizationStatus.denied) return;
      // ignore: deprecated_member_use
      final token = await FirebaseMessaging.instance.getToken();
      if (token != null) await api.post('/api/push/app-tokens', body: {'token': token});
      // ignore: deprecated_member_use
      _tokenRefresh ??= FirebaseMessaging.instance.onTokenRefresh.listen((t) async {
        try {
          await api.post('/api/push/app-tokens', body: {'token': t});
        } catch (_) {
          // 다음 로그인 때 다시 남긴다
        }
      });
    } catch (e) {
      debugPrint('앱 푸시 등록 실패: $e');
    }
  }

  /// 로그아웃하기 전: 이 휴대폰으로 더 오지 않게 서버에서 토큰을 지운다
  Future<void> unregister(ApiClient api) async {
    if (!_ready) return;
    await _tokenRefresh?.cancel();
    _tokenRefresh = null;
    try {
      // ignore: deprecated_member_use
      final token = await FirebaseMessaging.instance.getToken();
      if (token != null) await api.delete('/api/push/app-tokens', body: {'token': token});
    } catch (e) {
      debugPrint('앱 푸시 해제 실패: $e');
    }
  }

  static String _link(RemoteMessage m) => (m.data['link'] as String?) ?? '/';
}
