import 'package:flutter/material.dart';
import 'package:provider/provider.dart';

import '../api/models.dart';
import '../app_state.dart';
import '../theme.dart';
import '../widgets/async_view.dart';
import 'challenge_detail_screen.dart';
import 'post_detail_screen.dart';

/// 알림의 화면 주소(link)를 연다: 챌린지 · 글이면 그 화면, 그 밖에는 알림함 (휴대폰 알림을 눌렀을 때도 쓴다)
void openNotificationLink(NavigatorState nav, String? link, {bool fallbackToList = false}) {
  final path = link ?? '';
  final challenge = RegExp(r'^/challenges/(\d+)').firstMatch(path);
  final post = RegExp(r'^/community/(\d+)').firstMatch(path);
  if (challenge != null) {
    nav.push(MaterialPageRoute(builder: (_) => ChallengeDetailScreen(id: int.parse(challenge.group(1)!))));
  } else if (post != null) {
    nav.push(MaterialPageRoute(builder: (_) => PostDetailScreen(id: int.parse(post.group(1)!))));
  } else if (fallbackToList) {
    nav.push(MaterialPageRoute(builder: (_) => const NotificationsScreen()));
  }
}

/// 알림함 (홈 위쪽의 종을 누르면 열린다). 누르면 읽음 처리하고, 챌린지 · 글 알림이면 그 화면으로 간다
class NotificationsScreen extends StatefulWidget {
  const NotificationsScreen({super.key});

  @override
  State<NotificationsScreen> createState() => _NotificationsScreenState();
}

class _NotificationsScreenState extends State<NotificationsScreen> {
  final _view = GlobalKey<AsyncViewState<List<AppNotification>>>();

  Future<void> _open(AppNotification n) async {
    final api = context.read<AppState>().api;
    if (!n.read) {
      setState(() => n.read = true);
      try {
        await api.post('/api/notifications/${n.id}/read');
      } catch (_) {
        // 읽음 표시는 다음에 다시 시도된다
      }
    }
    if (!mounted) return;
    openNotificationLink(Navigator.of(context), n.link);
  }

  Future<void> _readAll() async {
    try {
      await context.read<AppState>().api.post('/api/notifications/read-all');
      await _view.currentState?.reload();
    } catch (e) {
      if (mounted) showMessage(context, '$e');
    }
  }

  @override
  Widget build(BuildContext context) {
    final api = context.read<AppState>().api;
    return Scaffold(
      appBar: AppBar(
        title: const Text('알림'),
        actions: [
          TextButton(onPressed: _readAll, child: const Text('모두 읽음')),
          const SizedBox(width: 8),
        ],
      ),
      body: SafeArea(
        child: AsyncView<List<AppNotification>>(
          key: _view,
          load: () async {
            final data = await api.get('/api/notifications') as List;
            return data.map((n) => AppNotification.fromJson(n as Map<String, dynamic>)).toList();
          },
          builder: (context, items, reload) {
            if (items.isEmpty) {
              return ListView(children: const [
                SizedBox(height: 120),
                Center(child: Text('아직 알림이 없어요.', style: TextStyle(color: AppColors.muted))),
              ]);
            }
            return ListView.separated(
              padding: const EdgeInsets.fromLTRB(16, 8, 16, 24),
              itemCount: items.length,
              separatorBuilder: (_, _) => const SizedBox(height: 8),
              itemBuilder: (context, i) {
                final n = items[i];
                return Card(
                  color: n.read ? AppColors.surface : AppColors.surfaceAlt,
                  clipBehavior: Clip.antiAlias,
                  child: InkWell(
                    onTap: () => _open(n),
                    child: Padding(
                      padding: const EdgeInsets.fromLTRB(14, 14, 16, 14),
                      child: Row(crossAxisAlignment: CrossAxisAlignment.start, children: [
                        Padding(
                          padding: const EdgeInsets.only(top: 2),
                          child: Icon(
                            n.read ? Icons.notifications_none : Icons.notifications_active,
                            color: n.read ? AppColors.muted : AppColors.brown,
                            size: 22,
                          ),
                        ),
                        const SizedBox(width: 12),
                        // 긴 문구도 잘리지 않고 줄을 바꿔 다 보이게 한다
                        Expanded(
                          child: Column(crossAxisAlignment: CrossAxisAlignment.start, children: [
                            Text(n.title,
                                style: TextStyle(
                                    fontWeight: n.read ? FontWeight.w600 : FontWeight.w800, fontSize: 15, height: 1.3)),
                            if (n.body != null && n.body!.isNotEmpty) ...[
                              const SizedBox(height: 3),
                              Text(n.body!, style: const TextStyle(fontSize: 13.5, height: 1.4)),
                            ],
                            const SizedBox(height: 5),
                            Text(shortDateTime(n.sentAt), style: const TextStyle(fontSize: 12, color: AppColors.muted)),
                          ]),
                        ),
                      ]),
                    ),
                  ),
                );
              },
            );
          },
        ),
      ),
    );
  }
}
