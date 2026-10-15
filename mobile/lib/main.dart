import 'package:flutter/material.dart';
import 'package:provider/provider.dart';

import 'api/api_client.dart';
import 'app_state.dart';
import 'screens/home_shell.dart';
import 'screens/login_screen.dart';
import 'theme.dart';

Future<void> main() async {
  WidgetsFlutterBinding.ensureInitialized();
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
