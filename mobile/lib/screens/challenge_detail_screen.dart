import 'package:flutter/material.dart';
import 'package:provider/provider.dart';

import '../api/models.dart';
import '../app_state.dart';
import '../theme.dart';
import '../widgets/async_view.dart';
import 'verify_screen.dart';

/// 챌린지 상세: 규칙 · 참가자 · 참여하기 / 참여 취소 / 인증하러 가기
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
        builder: (context, c, reload) => Column(children: [
          Expanded(
            child: ListView(padding: const EdgeInsets.fromLTRB(20, 8, 20, 24), children: [
              Row(children: [
                Tag(c.category.name),
                const SizedBox(width: 6),
                c.isFree ? const Tag('무료', color: AppColors.mint) : Tag('${points(c.entryFee)}P', color: AppColors.primary),
                const SizedBox(width: 6),
                Tag(_statusLabel(c.status), color: AppColors.muted),
              ]),
              const SizedBox(height: 12),
              Text(c.title, style: const TextStyle(fontSize: 24, fontWeight: FontWeight.w900)),
              if (c.hostNickname != null) ...[
                const SizedBox(height: 4),
                Text('개설자 ${c.hostNickname}', style: const TextStyle(color: AppColors.muted)),
              ],
              const SizedBox(height: 16),
              if (c.description.isNotEmpty) Text(c.description, style: const TextStyle(fontSize: 15, height: 1.5)),
              const SizedBox(height: 20),
              Card(
                child: Padding(
                  padding: const EdgeInsets.all(16),
                  child: Column(children: [
                    _row('기간', '${c.startDate} ~ ${c.endDate} (${c.totalDays}일)'),
                    _row('인증', c.frequencyLabel),
                    _row('참가 포인트', c.isFree ? '없음' : '${points(c.entryFee)}P'),
                    _row('참가자', '${c.participantCount}명${c.maxParticipants != null ? ' / ${c.maxParticipants}명' : ''}',
                        last: true),
                  ]),
                ),
              ),
              if (c.notice != null && c.notice!.isNotEmpty) ...[
                const SizedBox(height: 12),
                Card(
                  color: AppColors.surfaceAlt,
                  child: Padding(
                    padding: const EdgeInsets.all(16),
                    child: Column(crossAxisAlignment: CrossAxisAlignment.start, children: [
                      const Text('공지', style: TextStyle(fontWeight: FontWeight.w800, color: AppColors.brown)),
                      const SizedBox(height: 4),
                      Text(c.notice!),
                    ]),
                  ),
                ),
              ],
              const SizedBox(height: 20),
              Text('참가자 ${c.participants.length}명', style: const TextStyle(fontWeight: FontWeight.w800, fontSize: 16)),
              const SizedBox(height: 8),
              Wrap(spacing: 8, runSpacing: 8, children: [
                for (final p in c.participants)
                  Chip(
                    label: Text(p.nickname),
                    backgroundColor: AppColors.surface,
                    side: const BorderSide(color: AppColors.border),
                  ),
              ]),
            ]),
          ),
          SafeArea(
            top: false,
            child: Padding(padding: const EdgeInsets.fromLTRB(20, 8, 20, 12), child: _action(c)),
          ),
        ]),
      ),
    );
  }

  Widget _action(ChallengeDetail c) {
    if (c.joined && c.status == 'ONGOING') {
      return FilledButton(
        onPressed: () async {
          await Navigator.of(context)
              .push(MaterialPageRoute(builder: (_) => VerifyScreen(challengeId: c.id, title: c.title)));
          _view.currentState?.reload();
        },
        child: const Text('인증하러 가기'),
      );
    }
    if (c.joined && c.canLeave) {
      return OutlinedButton(
        style: OutlinedButton.styleFrom(minimumSize: const Size.fromHeight(50)),
        onPressed: _busy ? null : () => _leave(c),
        child: const Text('참여 취소'),
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

  Widget _row(String label, String value, {bool last = false}) {
    return Padding(
      padding: EdgeInsets.only(bottom: last ? 0 : 10),
      child: Row(crossAxisAlignment: CrossAxisAlignment.start, children: [
        SizedBox(width: 92, child: Text(label, style: const TextStyle(color: AppColors.muted))),
        Expanded(child: Text(value, style: const TextStyle(fontWeight: FontWeight.w600))),
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
