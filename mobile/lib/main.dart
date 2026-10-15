import 'package:flutter/material.dart';
import 'package:flutter/services.dart';
import 'package:provider/provider.dart';

import 'api/api_client.dart';
import 'app_state.dart';
import 'screens/home_shell.dart';
import 'screens/login_screen.dart';
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
  state.boot();
  state.listenSocialLogin();
}

class GodLifeApp extends StatelessWidget {
  const GodLifeApp({super.key});

  @override
  Widget build(BuildContext context) {
    final state = context.watch<AppState>();
    return MaterialApp(
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
