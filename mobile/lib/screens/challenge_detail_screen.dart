import 'package:flutter/material.dart';
import 'package:provider/provider.dart';

import '../api/models.dart';
import '../app_state.dart';
import '../theme.dart';
import '../widgets/async_view.dart';
import '../widgets/category_icon.dart';
import '../widgets/design.dart';
import 'verify_screen.dart';

/// 챌린지 상세: 색 띠(아이콘) → 제목 · 설명 → 규칙 카드 → 공지 → 참가자 → 참여하기 / 참여 취소 / 인증하러 가기
class ChallengeDetailScreen extends StatefulWidget {
  const ChallengeDetailScreen({super.key, required this.id});

  final int id;

  @override
  State<ChallengeDetailScreen> createState() => _ChallengeDetailScreenState();
}

class _ChallengeDetailScreenState extends State<ChallengeDetailScreen> {
  final _view = GlobalKey<AsyncViewState<ChallengeDetail>>();
  bool _busy = false;

  Future<ChallengeDetail> _load() async {
    final data = await context.read<AppState>().api.get('/api/challenges/${widget.id}');
    return ChallengeDetail.fromJson(data as Map<String, dynamic>);
  }

  Future<void> _join(ChallengeDetail c) async {
    if (!c.isFree) {
      final ok = await showDialog<bool>(
        context: context,
        builder: (context) => AlertDialog(
          title: const Text('포인트 챌린지에 참여할까요?'),
          content: Text('충전 포인트 ${points(c.entryFee)}P 를 걸어요.\n'
              '인증한 날의 몫은 그대로 돌려받고, 인증하지 못한 날의 몫은 그날 성공한 참가자들에게 보상 포인트로 나눠져요.'),
          actions: [
            TextButton(onPressed: () => Navigator.pop(context, false), child: const Text('취소')),
            TextButton(onPressed: () => Navigator.pop(context, true), child: const Text('참여하기')),
          ],
        ),
      );
      if (ok != true) return;
    }
    await _act(() => context.read<AppState>().api.post('/api/challenges/${c.id}/participants'), '참여했어요!');
  }

  Future<void> _leave(ChallengeDetail c) async {
    await _act(() => context.read<AppState>().api.delete('/api/challenges/${c.id}/participants/me'),
        c.isFree ? '참여를 취소했어요.' : '참여를 취소하고 포인트를 돌려받았어요.');
  }

  Future<void> _act(Future<dynamic> Function() call, String done) async {
    setState(() => _busy = true);
    try {
      await call();
      if (mounted) showMessage(context, done);
      await _view.currentState?.reload();
    } catch (e) {
      if (mounted) showMessage(context, '$e');
    } finally {
      if (mounted) setState(() => _busy = false);
    }
  }

  @override
  Widget build(BuildContext context) {
    return Scaffold(
      appBar: AppBar(title: const Text('챌린지')),
      body: AsyncView<ChallengeDetail>(
        key: _view,
        load: _load,
        builder: (context, c, reload) {
          final (icon, (background, foreground)) =
              CategoryIcon.styleOf(c.category.name, title: c.title, subType: c.subTypeName);
          return Column(children: [
            Expanded(
              child: ListView(padding: const EdgeInsets.fromLTRB(20, 4, 20, 24), children: [
                // 맨 위 색 띠: 챌린지 아이콘 + 상태
                Container(
                  height: 120,
                  decoration: BoxDecoration(color: background, borderRadius: BorderRadius.circular(22)),
                  child: Stack(children: [
                    Center(
                      child: Container(
                        width: 64,
                        height: 64,
                        decoration: const BoxDecoration(color: Color(0xBFFFFFFF), shape: BoxShape.circle),
                        child: Icon(icon, color: foreground, size: 32),
                      ),
                    ),
                    Positioned(
                      left: 14,
                      top: 14,
                      child: Container(
                        padding: const EdgeInsets.symmetric(horizontal: 11, vertical: 5),
                        decoration:
                            BoxDecoration(color: const Color(0xBFFFFFFF), borderRadius: BorderRadius.circular(999)),
                        child: Text(_statusLabel(c.status),
                            style: TextStyle(fontSize: 11, fontWeight: FontWeight.w800, color: foreground)),
                      ),
                    ),
                  ]),
                ),
                const SizedBox(height: 18),
                Row(children: [
                  ColorTag.of(c.subTypeName ?? c.category.name, (background, foreground)),
                  const SizedBox(width: 6),
                  PriceBadge(free: c.isFree, fee: c.entryFee),
                ]),
                const SizedBox(height: 10),
                Text(c.title,
                    style: const TextStyle(fontSize: 21, fontWeight: FontWeight.w800, height: 1.3, letterSpacing: -0.4)),
                if (c.hostNickname != null) ...[
                  const SizedBox(height: 5),
                  Text('개설자 ${c.hostNickname}',
                      style: const TextStyle(color: AppColors.muted, fontSize: 12, fontWeight: FontWeight.w700)),
                ],
                if (c.description.isNotEmpty) ...[
                  const SizedBox(height: 14),
                  Text(c.description,
                      style: const TextStyle(
                          fontSize: 13.5, fontWeight: FontWeight.w500, height: 1.6, color: Color(0xFF6B6558))),
                ],
                const SizedBox(height: 18),
                AppCard(
                  bordered: false,
                  padding: EdgeInsets.zero,
                  child: Column(children: [
                    _info(Icons.calendar_today, ColorTag.gold, '기간', '${c.startDate} ~ ${c.endDate}',
                        note: '${c.totalDays}일'),
                    _info(Icons.repeat, ColorTag.lav, '인증', c.frequencyLabel),
                    _info(Icons.savings, ColorTag.coral, '참가 포인트', c.isFree ? '없음' : '${points(c.entryFee)}P'),
                    _info(Icons.group, ColorTag.mint, '참가자',
                        '${c.participantCount}명${c.maxParticipants != null ? ' / ${c.maxParticipants}명' : ''}',
                        last: true),
                  ]),
                ),
                if (c.notice != null && c.notice!.isNotEmpty) ...[
                  const SizedBox(height: 12),
                  Container(
                    padding: const EdgeInsets.all(16),
                    decoration: BoxDecoration(color: ColorTag.gold.$1, borderRadius: BorderRadius.circular(18)),
                    child: Row(crossAxisAlignment: CrossAxisAlignment.start, children: [
                      Icon(Icons.campaign, size: 20, color: ColorTag.gold.$2),
                      const SizedBox(width: 10),
                      Expanded(
                        child: Text(c.notice!,
                            style: const TextStyle(fontSize: 12.5, fontWeight: FontWeight.w600, height: 1.55)),
                      ),
                    ]),
                  ),
                ],
                const SizedBox(height: 22),
                Text('참가자 ${c.participants.length}명',
                    style: const TextStyle(fontSize: 17, fontWeight: FontWeight.w800, letterSpacing: -0.2)),
                const SizedBox(height: 12),
                if (c.participants.isEmpty)
                  const Text('아직 참가자가 없어요. 첫 번째로 참여해 보세요!',
                      style: TextStyle(color: AppColors.muted, fontSize: 12.5, fontWeight: FontWeight.w600)),
                Wrap(spacing: 8, runSpacing: 8, children: [
                  for (final (i, p) in c.participants.indexed) _person(p.nickname, i),
                ]),
              ]),
            ),
            Container(
              decoration: const BoxDecoration(
                color: AppColors.bg,
                border: Border(top: BorderSide(color: AppColors.border)),
              ),
              child: SafeArea(
                top: false,
                child: Padding(padding: const EdgeInsets.fromLTRB(20, 10, 20, 12), child: _action(c)),
              ),
            ),
          ]);
        },
      ),
    );
  }

  Widget _action(ChallengeDetail c) {
    if (c.joined && c.status == 'ONGOING') {
      return FilledButton.icon(
        onPressed: () async {
          await Navigator.of(context)
              .push(MaterialPageRoute(builder: (_) => VerifyScreen(challengeId: c.id, title: c.title)));
          _view.currentState?.reload();
        },
        icon: const Icon(Icons.photo_camera_outlined, size: 18),
        label: const Text('인증하러 가기'),
      );
    }
    if (c.joined && c.canLeave) {
      return OutlinedButton(
        style: OutlinedButton.styleFrom(
          minimumSize: const Size.fromHeight(50),
          backgroundColor: AppColors.surface,
          side: const BorderSide(color: AppColors.cardBorder),
          shape: RoundedRectangleBorder(borderRadius: BorderRadius.circular(999)),
        ),
        onPressed: _busy ? null : () => _leave(c),
        child: const Text('참여 취소', style: TextStyle(color: AppColors.text, fontWeight: FontWeight.w800)),
      );
    }
    if (c.joined) {
      return const FilledButton(onPressed: null, child: Text('참여 중이에요'));
    }
    if (c.recruiting) {
      return FilledButton(onPressed: _busy ? null : () => _join(c), child: Text(_busy ? '처리하는 중…' : '참여하기'));
    }
    return const FilledButton(onPressed: null, child: Text('모집이 끝났어요'));
  }

  /// 규칙 한 줄: 동그란 색 아이콘 · 이름 · 값
  Widget _info(IconData icon, (Color, Color) colors, String label, String value, {String? note, bool last = false}) {
    return Container(
      padding: const EdgeInsets.symmetric(horizontal: 16, vertical: 13),
      decoration: BoxDecoration(
        border: last ? null : const Border(bottom: BorderSide(color: Color(0xFFF5F2EA))),
      ),
      child: Row(children: [
        Container(
          width: 34,
          height: 34,
          decoration: BoxDecoration(color: colors.$1, shape: BoxShape.circle),
          child: Icon(icon, size: 16, color: colors.$2),
        ),
        const SizedBox(width: 12),
        Text(label, style: const TextStyle(fontSize: 12.5, fontWeight: FontWeight.w700, color: AppColors.muted)),
        const SizedBox(width: 12),
        Expanded(
          child: Text(value,
              textAlign: TextAlign.right,
              style: const TextStyle(fontSize: 13.5, fontWeight: FontWeight.w800, letterSpacing: -0.1)),
        ),
        if (note != null) ...[
          const SizedBox(width: 8),
          ColorTag.of(note, colors),
        ],
      ]),
    );
  }

  /// 참가자 한 명: 색 동그라미(첫 글자) + 닉네임
  Widget _person(String nickname, int index) {
    const palette = [ColorTag.coral, ColorTag.mint, ColorTag.gold, ColorTag.lav];
    final colors = palette[index % palette.length];
    return Container(
      padding: const EdgeInsets.fromLTRB(5, 5, 12, 5),
      decoration: BoxDecoration(
        color: AppColors.surface,
        borderRadius: BorderRadius.circular(999),
        boxShadow: cardShadow,
      ),
      child: Row(mainAxisSize: MainAxisSize.min, children: [
        Container(
          width: 26,
          height: 26,
          alignment: Alignment.center,
          decoration: BoxDecoration(color: colors.$1, shape: BoxShape.circle),
          child: Text(nickname.isEmpty ? '' : nickname.substring(0, 1),
              style: TextStyle(fontSize: 11.5, fontWeight: FontWeight.w800, color: colors.$2)),
        ),
        const SizedBox(width: 7),
        Text(nickname, style: const TextStyle(fontSize: 12, fontWeight: FontWeight.w700)),
      ]),
    );
  }

  String _statusLabel(String status) => switch (status) {
        'RECRUITING' => '모집 중',
        'ONGOING' => '진행 중',
        'ENDED' => '종료',
        'SETTLED' => '정산 완료',
        _ => status,
      };
}
