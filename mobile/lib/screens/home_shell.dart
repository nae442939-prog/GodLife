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

/// 로그인 뒤의 뼈대: 위에는 로고와 알림 종, 아래에는 탭 5개 (챌린지 · 커뮤니티 · 홈 · 갓생기록 · 마이페이지)
class HomeShell extends StatefulWidget {
  const HomeShell({super.key});

  @override
  State<HomeShell> createState() => _HomeShellState();
}

class _HomeShellState extends State<HomeShell> {
  /// 아래 탭 순서: 챌린지 · 커뮤니티 · 홈 · 갓생기록 · 마이페이지 (홈이 한가운데)
  static const _home = 2;
  int _tab = _initialTab();

  /// 웹 화면(충전 · 상점)의 아래 탭에서 넘어오면 주소의 ?tab= 으로 처음 탭을 고른다
  static int _initialTab() {
    return switch (Uri.base.queryParameters['tab']) {
      'challenges' => 0,
      'community' => 1,
      'records' => 3,
      'my' => 4,
      _ => _home,
    };
  }
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
      0 => const ChallengeListScreen(),
      1 => const CommunityScreen(),
      _home => HomeScreen(onBrowse: () => setState(() => _tab = 0), onCommunity: () => setState(() => _tab = 1)),
      3 => const RecordsScreen(),
      _ => const MyPageScreen(),
    };
    return Scaffold(
      appBar: AppBar(
        titleSpacing: 20,
        // 종 위의 숫자 표시가 화면 위쪽 · 오른쪽 끝에서 잘리지 않게 여유를 둔다
        toolbarHeight: 58,
        title: Row(children: [
          Container(
            width: 10,
            height: 10,
            decoration: const BoxDecoration(color: AppColors.primary, shape: BoxShape.circle),
          ),
          const SizedBox(width: 7),
          const Text('갓생살기'),
        ]),
        actions: [
          // 종 + 오른쪽 위의 작은 코랄 동그라미(안 읽은 수)
          IconButton(
            tooltip: '알림',
            onPressed: _openNotifications,
            padding: const EdgeInsets.all(12),
            icon: Stack(clipBehavior: Clip.none, children: [
              const Icon(Icons.notifications_none, size: 24, color: AppColors.brown),
              if (_unread > 0)
                Positioned(
                  top: -4,
                  right: -5,
                  child: Container(
                    constraints: const BoxConstraints(minWidth: 16, minHeight: 16),
                    padding: const EdgeInsets.symmetric(horizontal: 3),
                    alignment: Alignment.center,
                    decoration: BoxDecoration(color: AppColors.primary, borderRadius: BorderRadius.circular(99)),
                    child: Text(_unread > 99 ? '99+' : '$_unread',
                        style: const TextStyle(color: Colors.white, fontSize: 9, fontWeight: FontWeight.w800, height: 1)),
                  ),
                ),
            ]),
          ),
          const SizedBox(width: 10),
        ],
      ),
      body: body,
      bottomNavigationBar: _BottomTabs(
        selected: _tab,
        onSelect: (i) {
          setState(() => _tab = i);
          _loadUnread();
        },
      ),
    );
  }
}

/// 시안의 아래 탭: 고른 탭만 먹색 알약 안에 흰 아이콘, 나머지는 옅은 회색 선 아이콘
class _BottomTabs extends StatelessWidget {
  const _BottomTabs({required this.selected, required this.onSelect});

  final int selected;
  final ValueChanged<int> onSelect;

  static const _tabs = [
    (Icons.flag_outlined, Icons.flag, '챌린지'),
    (Icons.chat_bubble_outline, Icons.chat_bubble, '커뮤니티'),
    (Icons.home_outlined, Icons.home, '홈'),
    (Icons.calendar_today_outlined, Icons.calendar_today, '갓생기록'),
    (Icons.person_outline, Icons.person, '마이페이지'),
  ];

  @override
  Widget build(BuildContext context) {
    return Container(
      decoration: const BoxDecoration(
        color: AppColors.surface,
        border: Border(top: BorderSide(color: AppColors.border)),
      ),
      child: SafeArea(
        top: false,
        minimum: const EdgeInsets.only(bottom: 10),
        child: Padding(
          padding: const EdgeInsets.fromLTRB(10, 10, 10, 0),
          child: Row(children: [
            for (final (i, (icon, activeIcon, label)) in _tabs.indexed)
              Expanded(
                child: InkWell(
                  borderRadius: BorderRadius.circular(16),
                  onTap: () => onSelect(i),
                  child: Padding(
                    padding: const EdgeInsets.symmetric(vertical: 2),
                    child: Column(mainAxisSize: MainAxisSize.min, children: [
                      AnimatedContainer(
                        duration: const Duration(milliseconds: 180),
                        width: 42,
                        height: 28,
                        decoration: BoxDecoration(
                          color: i == selected ? AppColors.text : Colors.transparent,
                          borderRadius: BorderRadius.circular(999),
                        ),
                        child: Icon(i == selected ? activeIcon : icon,
                            size: 18, color: i == selected ? Colors.white : AppColors.navIdle),
                      ),
                      const SizedBox(height: 4),
                      Text(label,
                          style: TextStyle(
                            fontSize: 10,
                            fontWeight: i == selected ? FontWeight.w800 : FontWeight.w700,
                            color: i == selected ? AppColors.text : AppColors.navIdle,
                          )),
                    ]),
                  ),
                ),
              ),
          ]),
        ),
      ),
    );
  }
}
