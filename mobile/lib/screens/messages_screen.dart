import 'package:flutter/material.dart';
import 'package:provider/provider.dart';

import '../app_state.dart';
import '../theme.dart';
import '../widgets/async_view.dart';
import '../widgets/design.dart';
import '../widgets/user_avatar.dart';
import 'message_room_screen.dart';

/// 메시지 목록 (웹의 /messages 와 같다).
/// [대화] = 바로 대화하는 사이와 내가 보낸 요청, [요청] = 받은 메시지 요청 (대화방에서 수락 · 거절)
class MessagesScreen extends StatefulWidget {
  const MessagesScreen({super.key});

  @override
  State<MessagesScreen> createState() => _MessagesScreenState();
}

class _MessagesScreenState extends State<MessagesScreen> {
  bool _requests = false;

  Future<List<Map<String, dynamic>>> _load() async =>
      ((await context.read<AppState>().api.get('/api/messages')) as List).cast<Map<String, dynamic>>();

  @override
  Widget build(BuildContext context) {
    return Scaffold(
      appBar: AppBar(title: const Text('메시지')),
      body: SafeArea(
        child: AsyncView<List<Map<String, dynamic>>>(
          load: _load,
          builder: (context, all, reload) {
            final requests = all.where((c) => c['request'] == 'RECEIVED').toList();
            final chats = all.where((c) => c['request'] != 'RECEIVED').toList();
            final shown = _requests ? requests : chats;
            return ListView(padding: const EdgeInsets.fromLTRB(20, 0, 20, 28), children: [
              Row(children: [
                PillChip('대화', selected: !_requests, onTap: () => setState(() => _requests = false)),
                const SizedBox(width: 8),
                PillChip(requests.isEmpty ? '요청' : '요청 ${requests.length}',
                    selected: _requests, onTap: () => setState(() => _requests = true)),
              ]),
              const SizedBox(height: 14),
              if (_requests && requests.isNotEmpty)
                const Padding(
                  padding: EdgeInsets.fromLTRB(4, 0, 4, 12),
                  child: Text('맞팔로우도, 같은 챌린지 참가자도 아닌 회원이 보낸 메시지예요. 열어서 수락하거나 거절할 수 있어요.',
                      style: TextStyle(
                          color: AppColors.muted, fontSize: 11.5, fontWeight: FontWeight.w600, height: 1.6)),
                ),
              if (shown.isEmpty)
                Padding(
                  padding: const EdgeInsets.fromLTRB(12, 100, 12, 0),
                  child: Text(
                      _requests
                          ? '받은 메시지 요청이 없어요.'
                          : '아직 나눈 대화가 없어요.\n랭킹이나 챌린지 참가자의 프로필에서 [메시지]를 눌러 보세요.',
                      textAlign: TextAlign.center,
                      style: const TextStyle(
                          color: AppColors.muted, fontSize: 12.5, fontWeight: FontWeight.w600, height: 1.6)),
                )
              else
                AppCard(
                  bordered: false,
                  padding: EdgeInsets.zero,
                  child: Column(children: [
                    for (final (i, c) in shown.indexed) _row(c, last: i == shown.length - 1, reload: reload),
                  ]),
                ),
            ]);
          },
        ),
      ),
    );
  }

  Widget _row(Map<String, dynamic> c, {required bool last, required Future<void> Function() reload}) {
    final partner = c['partner'] as Map<String, dynamic>;
    final unread = (c['unread'] as num?)?.toInt() ?? 0;
    final request = c['request'] as String?;
    final at = c['lastAt'] == null ? null : DateTime.parse(c['lastAt'] as String);
    return InkWell(
      onTap: () async {
        await Navigator.of(context).push(
            MaterialPageRoute(builder: (_) => MessageRoomScreen(userId: (partner['id'] as num).toInt())));
        reload();
      },
      child: Container(
        padding: const EdgeInsets.symmetric(horizontal: 16, vertical: 13),
        decoration: BoxDecoration(
          border: last ? null : const Border(bottom: BorderSide(color: Color(0xFFF5F2EA))),
        ),
        child: Row(children: [
          UserAvatar(
              nickname: partner['nickname'] as String, image: partner['profileImageUrl'] as String?, size: 42),
          const SizedBox(width: 12),
          Expanded(
            child: Column(crossAxisAlignment: CrossAxisAlignment.start, children: [
              Row(children: [
                Flexible(
                  child: Text(partner['nickname'] as String,
                      overflow: TextOverflow.ellipsis,
                      style: const TextStyle(fontSize: 13.5, fontWeight: FontWeight.w800)),
                ),
                if (request == 'SENT') ...[const SizedBox(width: 6), ColorTag.of('요청 보냄', ColorTag.gold)],
              ]),
              const SizedBox(height: 4),
              Text('${c['lastMine'] == true ? '나: ' : ''}${(c['lastContent'] as String?) ?? ''}',
                  maxLines: 1,
                  overflow: TextOverflow.ellipsis,
                  style: const TextStyle(color: AppColors.muted, fontSize: 12, fontWeight: FontWeight.w600)),
            ]),
          ),
          const SizedBox(width: 10),
          Column(crossAxisAlignment: CrossAxisAlignment.end, children: [
            if (at != null)
              Text(shortDateTime(at),
                  style: const TextStyle(color: AppColors.navIdle, fontSize: 10.5, fontWeight: FontWeight.w600)),
            const SizedBox(height: 5),
            if (request == 'RECEIVED')
              ColorTag.of('요청', ColorTag.coral)
            else if (unread > 0)
              Container(
                constraints: const BoxConstraints(minWidth: 18, minHeight: 18),
                padding: const EdgeInsets.symmetric(horizontal: 5),
                alignment: Alignment.center,
                decoration: BoxDecoration(color: AppColors.primary, borderRadius: BorderRadius.circular(99)),
                child: Text(unread > 99 ? '99+' : '$unread',
                    style:
                        const TextStyle(color: Colors.white, fontSize: 10, fontWeight: FontWeight.w800, height: 1)),
              ),
          ]),
        ]),
      ),
    );
  }
}
