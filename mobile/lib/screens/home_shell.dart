import 'package:flutter/material.dart';
import 'package:provider/provider.dart';

import '../app_state.dart';
import 'challenge_list_screen.dart';
import 'my_challenges_screen.dart';
import 'my_page_screen.dart';
import 'notifications_screen.dart';
import 'wallet_screen.dart';

/// 로그인 뒤의 뼈대: 아래 탭 5개 (둘러보기 · 내 챌린지 · 지갑 · 알림 · 마이)
class HomeShell extends StatefulWidget {
  const HomeShell({super.key});

  @override
  State<HomeShell> createState() => _HomeShellState();
}

class _HomeShellState extends State<HomeShell> {
  int _tab = 1;
  int _unread = 0;

  @override
  void initState() {
    super.initState();
    _loadUnread();
  }

  Future<void> _loadUnread() async {
    try {
      final data = await context.read<AppState>().api.get('/api/notifications/unread-count');
      if (mounted) setState(() => _unread = ((data as Map)['count'] as num?)?.toInt() ?? 0);
    } catch (_) {
      // 숫자 표시만 빠진다
    }
  }

  @override
  Widget build(BuildContext context) {
    // 탭을 다시 누르면 새로 불러오도록 고른 탭만 그린다 (화면마다 당겨서 새로 고침도 있다)
    final body = switch (_tab) {
      0 => const ChallengeListScreen(),
      1 => const MyChallengesScreen(),
      2 => const WalletScreen(),
      3 => NotificationsScreen(onChanged: _loadUnread),
      _ => const MyPageScreen(),
    };
    return Scaffold(
      body: SafeArea(child: body),
      bottomNavigationBar: NavigationBar(
        selectedIndex: _tab,
        onDestinationSelected: (i) {
          setState(() => _tab = i);
          _loadUnread();
        },
        destinations: [
          const NavigationDestination(icon: Icon(Icons.explore_outlined), label: '둘러보기'),
          const NavigationDestination(icon: Icon(Icons.flag_outlined), label: '내 챌린지'),
          const NavigationDestination(icon: Icon(Icons.account_balance_wallet_outlined), label: '지갑'),
          NavigationDestination(
            icon: Badge(
              isLabelVisible: _unread > 0,
              label: Text('$_unread'),
              child: const Icon(Icons.notifications_none),
            ),
            label: '알림',
          ),
          const NavigationDestination(icon: Icon(Icons.person_outline), label: '마이'),
        ],
      ),
    );
  }
}
