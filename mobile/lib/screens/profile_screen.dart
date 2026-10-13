import 'package:flutter/material.dart';
import 'package:provider/provider.dart';

import '../api/api_client.dart';
import '../app_state.dart';
import '../theme.dart';
import '../widgets/async_view.dart';
import '../widgets/category_icon.dart';
import '../widgets/design.dart';
import 'challenge_detail_screen.dart';
import 'message_room_screen.dart';

const _tierNames = {'BRONZE': '브론즈', 'SILVER': '실버', 'GOLD': '골드', 'PLATINUM': '플래티넘', 'DIAMOND': '다이아'};

/// 다른 회원의 프로필 (웹의 프로필과 같다): 사진 · 닉네임 · 기록과 [팔로우] [메시지] 버튼.
/// 팔로우는 요청 · 수락 없이 바로 되고, 팔로우한 사람은 랭킹의 '친구'에서 함께 순위를 겨룬다.
/// [메시지]는 늘 대화방으로 간다 (맞팔로우 · 같은 챌린지가 아니면 대화방에서 메시지 요청으로 보내진다).
class ProfileScreen extends StatefulWidget {
  const ProfileScreen({super.key, required this.userId});

  final int userId;

  @override
  State<ProfileScreen> createState() => _ProfileScreenState();
}

class _ProfileScreenState extends State<ProfileScreen> {
  bool _busy = false;

  ApiClient get _api => context.read<AppState>().api;

  Future<Map<String, dynamic>> _load() async =>
      await _api.get('/api/users/${widget.userId}/profile') as Map<String, dynamic>;

  /// 팔로우 / 언팔로우. 끝나면 프로필을 다시 불러와 버튼과 팔로워 수를 맞춘다
  Future<void> _toggle(Map<String, dynamic> p, Future<void> Function() reload) async {
    final following = p['following'] == true;
    setState(() => _busy = true);
    try {
      if (following) {
        await _api.delete('/api/users/${widget.userId}/follow');
      } else {
        await _api.post('/api/users/${widget.userId}/follow');
      }
      await reload();
      if (mounted) showMessage(context, following ? '팔로우를 취소했어요.' : '${p['nickname']}님을 팔로우했어요.');
    } catch (e) {
      if (mounted) showMessage(context, '$e');
    } finally {
      if (mounted) setState(() => _busy = false);
    }
  }

  @override
  Widget build(BuildContext context) {
    return Scaffold(
      appBar: AppBar(title: const Text('프로필')),
      body: SafeArea(
        child: AsyncView<Map<String, dynamic>>(
          load: _load,
          builder: (context, p, reload) {
            final nickname = p['nickname'] as String;
            final image = p['profileImageUrl'] as String?;
            final bio = p['bio'] as String?;
            final mine = p['mine'] == true;
            final following = p['following'] == true;
            final followsMe = p['followsMe'] == true;
            final blocked = p['blocked'] == true;
            final rate = (p['successRate'] as num?)?.round();
            final challenges = ((p['challenges'] as List?) ?? []).cast<Map<String, dynamic>>();
            return ListView(padding: const EdgeInsets.fromLTRB(20, 8, 20, 28), children: [
              AppCard(
                bordered: false,
                radius: 22,
                padding: const EdgeInsets.fromLTRB(18, 22, 18, 18),
                child: Column(children: [
                  Container(
                    width: 76,
                    height: 76,
                    alignment: Alignment.center,
                    clipBehavior: Clip.antiAlias,
                    decoration: BoxDecoration(color: ColorTag.mint.$1, shape: BoxShape.circle),
                    child: image != null
                        ? Image.network('$serverBase$image',
                            width: 76, height: 76, fit: BoxFit.cover, errorBuilder: (_, _, _) => _initial(nickname))
                        : _initial(nickname),
                  ),
                  const SizedBox(height: 12),
                  Row(mainAxisAlignment: MainAxisAlignment.center, children: [
                    Flexible(
                      child: Text(nickname,
                          overflow: TextOverflow.ellipsis,
                          style: const TextStyle(fontSize: 18, fontWeight: FontWeight.w800, letterSpacing: -0.2)),
                    ),
                    if (_tierNames[p['tier']] != null) ...[
                      const SizedBox(width: 7),
                      ColorTag.of(_tierNames[p['tier']]!, ColorTag.gold),
                    ],
                  ]),
                  if (bio != null && bio.isNotEmpty) ...[
                    const SizedBox(height: 6),
                    Text(bio,
                        textAlign: TextAlign.center,
                        style: const TextStyle(fontSize: 12.5, fontWeight: FontWeight.w600, height: 1.5)),
                  ],
                  const SizedBox(height: 14),
                  Row(mainAxisAlignment: MainAxisAlignment.center, children: [
                    _count('팔로워', (p['followerCount'] as num).toInt()),
                    Container(
                        width: 1,
                        height: 26,
                        color: AppColors.border,
                        margin: const EdgeInsets.symmetric(horizontal: 26)),
                    _count('팔로잉', (p['followingCount'] as num).toInt()),
                  ]),
                  if (!mine && !blocked) ...[
                    const SizedBox(height: 16),
                    // 팔로우는 주요 버튼(코랄), 이미 팔로우 중이면 조용한 버튼. 옆에 메시지
                    Row(children: [
                      Expanded(
                        child: following
                            ? OutlinedButton.icon(
                                onPressed: _busy ? null : () => _toggle(p, reload),
                                icon: const Icon(Icons.check, size: 18, color: AppColors.text),
                                label: const Text('팔로잉',
                                    style: TextStyle(color: AppColors.text, fontWeight: FontWeight.w800)),
                                style: OutlinedButton.styleFrom(
                                  minimumSize: const Size.fromHeight(48),
                                  backgroundColor: AppColors.surfaceAlt,
                                  side: BorderSide.none,
                                  shape: RoundedRectangleBorder(borderRadius: BorderRadius.circular(999)),
                                ),
                              )
                            : FilledButton.icon(
                                onPressed: _busy ? null : () => _toggle(p, reload),
                                icon: const Icon(Icons.add, size: 18),
                                label: Text(followsMe ? '맞팔로우' : '팔로우'),
                              ),
                      ),
                      const SizedBox(width: 8),
                      Expanded(
                        child: OutlinedButton.icon(
                          onPressed: () => Navigator.of(context).push(
                              MaterialPageRoute(builder: (_) => MessageRoomScreen(userId: widget.userId))),
                          icon: const Icon(Icons.chat_bubble_outline, size: 17, color: AppColors.text),
                          label: const Text('메시지',
                              style: TextStyle(color: AppColors.text, fontWeight: FontWeight.w800)),
                          style: OutlinedButton.styleFrom(
                            minimumSize: const Size.fromHeight(48),
                            backgroundColor: AppColors.surface,
                            side: const BorderSide(color: AppColors.border, width: 1.4),
                            shape: RoundedRectangleBorder(borderRadius: BorderRadius.circular(999)),
                          ),
                        ),
                      ),
                    ]),
                    if (following && followsMe)
                      const Padding(
                        padding: EdgeInsets.only(top: 8),
                        child: Text('서로 팔로우하는 사이예요.',
                            style: TextStyle(color: AppColors.muted, fontSize: 11.5, fontWeight: FontWeight.w600)),
                      ),
                    if (followsMe && !following)
                      const Padding(
                        padding: EdgeInsets.only(top: 8),
                        child: Text('나를 팔로우하는 회원이에요.',
                            style: TextStyle(color: AppColors.muted, fontSize: 11.5, fontWeight: FontWeight.w600)),
                      ),
                  ],
                  if (blocked)
                    const Padding(
                      padding: EdgeInsets.only(top: 14),
                      child: Text('차단한 회원이에요. 설정에서 차단을 풀 수 있어요.',
                          style: TextStyle(color: AppColors.muted, fontSize: 12, fontWeight: FontWeight.w600)),
                    ),
                ]),
              ),
              const SizedBox(height: 14),
              AppCard(
                bordered: false,
                padding: const EdgeInsets.symmetric(horizontal: 8, vertical: 16),
                child: Row(children: [
                  Expanded(child: _stat('이번 달 인증', '${(p['monthVerify'] as num).toInt()}회')),
                  Expanded(child: _stat('최장 연속', '${(p['maxStreak'] as num).toInt()}일')),
                  Expanded(child: _stat('성공률', rate == null ? '-' : '$rate%')),
                ]),
              ),
              const SizedBox(height: 22),
              const Text('참여 중인 챌린지',
                  style: TextStyle(fontSize: 17, fontWeight: FontWeight.w800, letterSpacing: -0.2)),
              const SizedBox(height: 12),
              if (challenges.isEmpty)
                AppCard(
                  bordered: false,
                  padding: const EdgeInsets.all(16),
                  child: const Text('지금 참여 중인 공개 챌린지가 없어요.',
                      style: TextStyle(color: AppColors.muted, fontSize: 12.5, fontWeight: FontWeight.w600)),
                ),
              for (final c in challenges)
                Padding(
                  padding: const EdgeInsets.only(bottom: 10),
                  child: AppCard(
                    bordered: false,
                    radius: 18,
                    onTap: () => Navigator.of(context).push(
                        MaterialPageRoute(builder: (_) => ChallengeDetailScreen(id: (c['id'] as num).toInt()))),
                    child: Row(children: [
                      CategoryIcon((c['category'] as Map)['name'] as String, title: c['title'] as String, size: 42),
                      const SizedBox(width: 12),
                      Expanded(
                        child: Text(c['title'] as String,
                            maxLines: 1,
                            overflow: TextOverflow.ellipsis,
                            style:
                                const TextStyle(fontSize: 13.5, fontWeight: FontWeight.w800, letterSpacing: -0.1)),
                      ),
                      const SizedBox(width: 10),
                      c['inProgress'] == true
                          ? ColorTag.of('진행 중', ColorTag.mint)
                          : ColorTag.of('시작 전', ColorTag.gold),
                    ]),
                  ),
                ),
            ]);
          },
        ),
      ),
    );
  }

  Widget _initial(String nickname) => Text(nickname.isEmpty ? '' : nickname.substring(0, 1),
      style: TextStyle(fontSize: 28, fontWeight: FontWeight.w800, color: ColorTag.mint.$2));

  Widget _count(String label, int value) {
    return Column(children: [
      Text('$value', style: const TextStyle(fontSize: 16, fontWeight: FontWeight.w800)),
      const SizedBox(height: 3),
      Text(label, style: const TextStyle(color: AppColors.muted, fontSize: 11.5, fontWeight: FontWeight.w700)),
    ]);
  }

  Widget _stat(String label, String value) {
    return Column(children: [
      Text(value, style: const TextStyle(fontSize: 16, fontWeight: FontWeight.w800, letterSpacing: -0.1)),
      const SizedBox(height: 5),
      Text(label, style: const TextStyle(color: AppColors.muted, fontSize: 11.5, fontWeight: FontWeight.w700)),
    ]);
  }
}
