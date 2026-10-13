import 'package:flutter/material.dart';
import 'package:provider/provider.dart';

import '../api/api_client.dart';
import '../app_state.dart';
import '../theme.dart';
import '../widgets/async_view.dart';
import '../widgets/design.dart';
import 'profile_screen.dart';

/// 팔로잉 · 팔로워 목록 (웹 마이페이지의 팔로우 목록 창과 같다). 한 줄을 누르면 프로필로 가서 팔로우하거나 끊는다.
class FollowListScreen extends StatefulWidget {
  const FollowListScreen({super.key, this.followers = false});

  /// 처음에 팔로워 쪽을 보여 줄지
  final bool followers;

  @override
  State<FollowListScreen> createState() => _FollowListScreenState();
}

class _FollowListScreenState extends State<FollowListScreen> {
  late bool _followers = widget.followers;

  Future<List<Map<String, dynamic>>> _load() async {
    final api = context.read<AppState>().api;
    final list = await api.get(_followers ? '/api/users/me/followers' : '/api/users/me/following') as List;
    return list.cast<Map<String, dynamic>>();
  }

  @override
  Widget build(BuildContext context) {
    return Scaffold(
      appBar: AppBar(title: const Text('팔로우')),
      body: SafeArea(
        child: Column(crossAxisAlignment: CrossAxisAlignment.start, children: [
          Padding(
            padding: const EdgeInsets.fromLTRB(20, 0, 20, 12),
            child: Row(children: [
              PillChip('팔로잉', selected: !_followers, onTap: () => setState(() => _followers = false)),
              const SizedBox(width: 8),
              PillChip('팔로워', selected: _followers, onTap: () => setState(() => _followers = true)),
            ]),
          ),
          Expanded(
            child: AsyncView<List<Map<String, dynamic>>>(
              key: ValueKey(_followers),
              load: _load,
              builder: (context, users, reload) {
                if (users.isEmpty) {
                  return ListView(padding: const EdgeInsets.symmetric(horizontal: 32), children: [
                    const SizedBox(height: 120),
                    Text(
                        _followers
                            ? '아직 나를 팔로우하는 사람이 없어요.'
                            : '아직 팔로우한 사람이 없어요.\n랭킹이나 챌린지 참가자에서 프로필을 눌러 팔로우해 보세요.',
                        textAlign: TextAlign.center,
                        style: const TextStyle(
                            color: AppColors.muted, fontSize: 12.5, fontWeight: FontWeight.w600, height: 1.6)),
                  ]);
                }
                return ListView(padding: const EdgeInsets.fromLTRB(20, 0, 20, 28), children: [
                  AppCard(
                    bordered: false,
                    padding: EdgeInsets.zero,
                    child: Column(children: [
                      for (final (i, u) in users.indexed)
                        InkWell(
                          onTap: () async {
                            await Navigator.of(context).push(MaterialPageRoute(
                                builder: (_) => ProfileScreen(userId: (u['userId'] as num).toInt())));
                            reload();
                          },
                          child: Container(
                            padding: const EdgeInsets.symmetric(horizontal: 16, vertical: 12),
                            decoration: BoxDecoration(
                              border: i == users.length - 1
                                  ? null
                                  : const Border(bottom: BorderSide(color: Color(0xFFF5F2EA))),
                            ),
                            child: Row(children: [
                              _avatar(u),
                              const SizedBox(width: 12),
                              Expanded(
                                child: Column(crossAxisAlignment: CrossAxisAlignment.start, children: [
                                  Text(u['nickname'] as String,
                                      overflow: TextOverflow.ellipsis,
                                      style: const TextStyle(fontSize: 13.5, fontWeight: FontWeight.w800)),
                                  if ((u['bio'] as String?)?.isNotEmpty ?? false) ...[
                                    const SizedBox(height: 3),
                                    Text(u['bio'] as String,
                                        maxLines: 1,
                                        overflow: TextOverflow.ellipsis,
                                        style: const TextStyle(
                                            color: AppColors.muted, fontSize: 11.5, fontWeight: FontWeight.w600)),
                                  ],
                                ]),
                              ),
                              if (u['mutual'] == true) ...[
                                const SizedBox(width: 8),
                                ColorTag.of('맞팔로우', ColorTag.mint),
                              ],
                              const Icon(Icons.chevron_right, color: Color(0xFFC7C0B0), size: 20),
                            ]),
                          ),
                        ),
                    ]),
                  ),
                ]);
              },
            ),
          ),
        ]),
      ),
    );
  }

  Widget _avatar(Map<String, dynamic> u) {
    final nickname = u['nickname'] as String;
    final image = u['profileImageUrl'] as String?;
    Widget initial() => Text(nickname.isEmpty ? '' : nickname.substring(0, 1),
        style: TextStyle(fontSize: 14, fontWeight: FontWeight.w800, color: ColorTag.mint.$2));
    return Container(
      width: 38,
      height: 38,
      alignment: Alignment.center,
      clipBehavior: Clip.antiAlias,
      decoration: BoxDecoration(color: ColorTag.mint.$1, shape: BoxShape.circle),
      child: image != null
          ? Image.network('$serverBase$image',
              width: 38, height: 38, fit: BoxFit.cover, errorBuilder: (_, _, _) => initial())
          : initial(),
    );
  }
}
