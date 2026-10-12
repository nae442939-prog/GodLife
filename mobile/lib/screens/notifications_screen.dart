import 'package:flutter/material.dart';
import 'package:provider/provider.dart';

import '../api/models.dart';
import '../app_state.dart';
import '../theme.dart';
import '../widgets/async_view.dart';
import 'challenge_detail_screen.dart';

/// 알림함 (웹의 종 모양과 같은 알림). 누르면 읽음 처리하고, 챌린지 알림이면 그 챌린지로 간다
class NotificationsScreen extends StatefulWidget {
  const NotificationsScreen({super.key, required this.onChanged});

  /// 읽음 상태가 바뀌면 아래 탭의 숫자를 다시 센다
  final VoidCallback onChanged;

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
        widget.onChanged();
      } catch (_) {
        // 읽음 표시는 다음에 다시 시도된다
      }
    }
    // 링크가 /challenges/{id}... 이면 그 챌린지 상세로
    final match = RegExp(r'^/challenges/(\d+)').firstMatch(n.link ?? '');
    if (match != null && mounted) {
      Navigator.of(context)
          .push(MaterialPageRoute(builder: (_) => ChallengeDetailScreen(id: int.parse(match.group(1)!))));
    }
  }

  Future<void> _readAll() async {
    try {
      await context.read<AppState>().api.post('/api/notifications/read-all');
      widget.onChanged();
      await _view.currentState?.reload();
    } catch (e) {
      if (mounted) showMessage(context, '$e');
    }
  }

  @override
  Widget build(BuildContext context) {
    final api = context.read<AppState>().api;
    return Column(crossAxisAlignment: CrossAxisAlignment.start, children: [
      Padding(
        padding: const EdgeInsets.fromLTRB(20, 20, 8, 4),
        child: Row(children: [
          const Expanded(child: Text('알림', style: TextStyle(fontSize: 24, fontWeight: FontWeight.w900))),
          TextButton(onPressed: _readAll, child: const Text('모두 읽음')),
        ]),
      ),
      Expanded(
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
              padding: const EdgeInsets.fromLTRB(16, 4, 16, 24),
              itemCount: items.length,
              separatorBuilder: (_, _) => const SizedBox(height: 8),
              itemBuilder: (context, i) {
                final n = items[i];
                return Card(
                  color: n.read ? AppColors.surface : AppColors.surfaceAlt,
                  clipBehavior: Clip.antiAlias,
                  child: ListTile(
                    onTap: () => _open(n),
                    leading: Icon(
                      n.read ? Icons.notifications_none : Icons.notifications_active,
                      color: n.read ? AppColors.muted : AppColors.brown,
                    ),
                    title: Text(n.title,
                        style: TextStyle(fontWeight: n.read ? FontWeight.w600 : FontWeight.w800, fontSize: 15)),
                    subtitle: Text(
                      [if (n.body != null && n.body!.isNotEmpty) n.body!, shortDateTime(n.sentAt)].join('\n'),
                      style: const TextStyle(fontSize: 12.5),
                    ),
                  ),
                );
              },
            );
          },
        ),
      ),
    ]);
  }
}
