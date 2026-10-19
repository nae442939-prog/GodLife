import 'package:flutter/material.dart';
import 'package:flutter/services.dart';
import 'package:provider/provider.dart';

import 'api/api_client.dart';
import 'app_state.dart';
import 'screens/home_shell.dart';
import 'screens/login_screen.dart';
import 'screens/notifications_screen.dart';
import 'theme.dart';

Future<void> main() async {
  WidgetsFlutterBinding.ensureInitialized();
  // 화면을 시스템 내비게이션 바 밑까지 그리고, 바 자리는 투명하게 둔다.
  // 버튼 3개 내비게이션 폰은 안드로이드가 바 자리에 회색 막을 깔아서 아래 탭이 떠 보였다 (제스처 폰은 막이 없어 멀쩡했다)
  SystemChrome.setEnabledSystemUIMode(SystemUiMode.edgeToEdge);
  SystemChrome.setSystemUIOverlayStyle(const SystemUiOverlayStyle(
    statusBarColor: Colors.transparent,
    statusBarIconBrightness: Brightness.dark,
    systemNavigationBarColor: Colors.transparent,
    systemNavigationBarDividerColor: Colors.transparent,
    systemNavigationBarIconBrightness: Brightness.dark,
    systemNavigationBarContrastEnforced: false,
  ));
  final api = await ApiClient.create();
  final state = AppState(api);
  runApp(ChangeNotifierProvider.value(value: state, child: const GodLifeApp()));
  _listenPush(state);
  await state.push.start(); // 알림을 눌러 앱이 켜졌으면 여기서 onOpen 이 불린다 (로그인 확인 뒤에 연다)
  await state.boot();
  state.listenSocialLogin();
  final link = _pendingLink;
  _pendingLink = null;
  if (link != null && state.loggedIn) _openLink(link);
}

/// 화면 밖(휴대폰 알림)에서 화면을 열기 위한 열쇠
final _navigator = GlobalKey<NavigatorState>();
final _messenger = GlobalKey<ScaffoldMessengerState>();
String? _pendingLink;

void _listenPush(AppState state) {
  state.push.onOpen = (link) {
    if (state.booting) {
      _pendingLink = link;
    } else if (state.loggedIn) {
      _openLink(link);
    }
  };
  // 앱을 보고 있을 때 온 알림은 아래에 잠깐 띄운다
  state.push.onForeground = (title, body, link) {
    _messenger.currentState?.showSnackBar(SnackBar(
      content: Text(body == null || body.isEmpty ? title : '$title\n$body'),
      action: SnackBarAction(label: '보기', onPressed: () => _openLink(link)),
      duration: const Duration(seconds: 5),
    ));
  };
}

void _openLink(String link) {
  final nav = _navigator.currentState;
  if (nav != null) openNotificationLink(nav, link, fallbackToList: true);
}

class GodLifeApp extends StatelessWidget {
  const GodLifeApp({super.key});

  @override
  Widget build(BuildContext context) {
    final state = context.watch<AppState>();
    return MaterialApp(
      navigatorKey: _navigator,
      scaffoldMessengerKey: _messenger,
      title: '갓생살기',
      debugShowCheckedModeBanner: false,
      theme: buildTheme(),
      home: state.booting
          ? const Scaffold(body: Center(child: CircularProgressIndicator()))
          : state.loggedIn
              ? const HomeShell()
              : const LoginScreen(),
    );
  }
}
