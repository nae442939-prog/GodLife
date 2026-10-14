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
import '../widgets/celebrations.dart';

/// 로그인 뒤의 뼈대: 아래 탭 5개(챌린지 · 커뮤니티 · 홈 · 갓생기록 · 마이페이지)는 늘 그 자리에 있고,
/// 그 위에서만 화면이 바뀐다. 상세 · 인증 · 랭킹처럼 넘어가는 화면도 탭 위쪽 영역 안에서 열려 탭이 사라지지 않는다.
class HomeShell extends StatefulWidget {
  const HomeShell({super.key});

  @override
  State<HomeShell> createState() => _HomeShellState();
}

/// 아래 탭 순서: 챌린지 · 커뮤니티 · 홈 · 갓생기록 · 마이페이지 (홈이 한가운데)
const _home = 2;

class _HomeShellState extends State<HomeShell> {
  final _tab = ValueNotifier<int>(_initialTab());

  /// 탭 위쪽 영역의 화면 쌓임 (탭 화면 → 상세 → 인증 …)
  final _inner = GlobalKey<NavigatorState>();

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

  @override
  void dispose() {
    _tab.dispose();
    super.dispose();
  }

  /// 탭을 누르면 열려 있던 화면(상세 등)을 닫고 그 탭의 첫 화면으로 간다
  void _select(int i) {
    _inner.currentState?.popUntil((route) => route.isFirst);
    _tab.value = i;
  }

  @override
  Widget build(BuildContext context) {
    return Scaffold(
      // 뒤로 가기는 탭 위쪽 영역에 열린 화면부터 닫는다
      body: NavigatorPopHandler(
        onPopWithResult: (_) => _inner.currentState?.maybePop(),
        child: Navigator(
          key: _inner,
          onGenerateRoute: (settings) => MaterialPageRoute(
            settings: settings,
            builder: (_) => _TabsPage(tab: _tab),
          ),
        ),
      ),
      bottomNavigationBar: ValueListenableBuilder<int>(
        valueListenable: _tab,
        builder: (context, tab, _) => _BottomTabs(selected: tab, onSelect: _select),
      ),
    );
  }
}

/// 탭의 첫 화면: 위에는 로고와 알림 종, 그 아래에 고른 탭의 내용
class _TabsPage extends StatefulWidget {
  const _TabsPage({required this.tab});

  final ValueNotifier<int> tab;

  @override
  State<_TabsPage> createState() => _TabsPageState();
}

class _TabsPageState extends State<_TabsPage> {
  int _unread = 0;

  @override
  void initState() {
    super.initState();
    widget.tab.addListener(_onTab);
    _loadUnread();
    WidgetsBinding.instance.addPostFrameCallback((_) => _greet());
  }

  /// 웹에서 회원가입을 마치고 넘어왔으면(?welcome=1) 환영 창을 먼저 띄우고, 새로 딴 뱃지가 있으면 축하한다
  Future<void> _greet() async {
    if (!mounted) return;
    if (Uri.base.queryParameters['welcome'] == '1') await showWelcome(context);
    if (mounted) await checkNewBadges(context);
  }

  @override
  void dispose() {
    widget.tab.removeListener(_onTab);
    super.dispose();
  }

  void _onTab() {
    setState(() {});
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
    final body = switch (widget.tab.value) {
      0 => const ChallengeListScreen(),
      1 => const CommunityScreen(),
      _home => HomeScreen(onBrowse: () => widget.tab.value = 0, onCommunity: () => widget.tab.value = 1),
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
