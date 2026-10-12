import 'package:flutter/material.dart';
import 'package:provider/provider.dart';

import '../app_state.dart';
import '../theme.dart';
import 'challenge_list_screen.dart';
import 'community_screen.dart';
import 'home_screen.dart';
import 'my_page_screen.dart';
import 'notifications_screen.dart';
import 'records_screen.dart';

/// 로그인 뒤의 뼈대: 위에는 로고와 알림 종, 아래에는 탭 5개 (홈 · 챌린지 · 커뮤니티 · 갓생기록 · 마이페이지)
class HomeShell extends StatefulWidget {
  const HomeShell({super.key});

  @override
  State<HomeShell> createState() => _HomeShellState();
}

class _HomeShellState extends State<HomeShell> {
  int _tab = 0;
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

  Future<void> _openNotifications() async {
    await Navigator.of(context).push(MaterialPageRoute(builder: (_) => const NotificationsScreen()));
    _loadUnread();
  }

  @override
  Widget build(BuildContext context) {
    // 탭을 누를 때마다 그 화면을 새로 불러온다 (화면마다 당겨서 새로 고침도 있다)
    final body = switch (_tab) {
      0 => HomeScreen(onBrowse: () => setState(() => _tab = 1), onCommunity: () => setState(() => _tab = 2)),
      1 => const ChallengeListScreen(),
      2 => const CommunityScreen(),
      3 => const RecordsScreen(),
      _ => const MyPageScreen(),
    };
    return Scaffold(
      appBar: AppBar(
        titleSpacing: 20,
        title: Row(children: [
          Container(
            width: 14,
            height: 14,
            decoration: BoxDecoration(color: AppColors.primary, borderRadius: BorderRadius.circular(5)),
          ),
          const SizedBox(width: 8),
          const Text('갓생살기'),
        ]),
        actions: [
          IconButton(
            tooltip: '알림',
            onPressed: _openNotifications,
            icon: Badge(
              isLabelVisible: _unread > 0,
              label: Text(_unread > 99 ? '99+' : '$_unread'),
              child: const Icon(Icons.notifications_none, size: 26),
            ),
          ),
          const SizedBox(width: 8),
        ],
      ),
      body: body,
      bottomNavigationBar: NavigationBar(
        selectedIndex: _tab,
        onDestinationSelected: (i) {
          setState(() => _tab = i);
          _loadUnread();
        },
        destinations: const [
          NavigationDestination(icon: Icon(Icons.home_outlined), selectedIcon: Icon(Icons.home), label: '홈'),
          NavigationDestination(icon: Icon(Icons.flag_outlined), selectedIcon: Icon(Icons.flag), label: '챌린지'),
          NavigationDestination(icon: Icon(Icons.forum_outlined), selectedIcon: Icon(Icons.forum), label: '커뮤니티'),
          NavigationDestination(
              icon: Icon(Icons.calendar_month_outlined), selectedIcon: Icon(Icons.calendar_month), label: '갓생기록'),
          NavigationDestination(icon: Icon(Icons.person_outline), selectedIcon: Icon(Icons.person), label: '마이페이지'),
        ],
      ),
    );
  }
}
