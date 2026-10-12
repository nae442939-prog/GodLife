import 'dart:typed_data';

import 'package:flutter/material.dart';
import 'package:image_picker/image_picker.dart';
import 'package:provider/provider.dart';

import '../api/api_client.dart';
import '../api/models.dart';
import '../app_state.dart';
import '../theme.dart';
import '../widgets/async_view.dart';

/// 오늘의 인증: 카메라로 바로 찍은 사진만 올린다 (앨범에서 고르기 없음 — 예전 사진 재사용 방지).
/// 서버의 AI 모델이 사진이 챌린지와 맞는지 판정하고, 맞지 않으면 저장하지 않고 바로 다시 찍게 한다.
class VerifyScreen extends StatefulWidget {
  const VerifyScreen({super.key, required this.challengeId, required this.title});

  final int challengeId;
  final String title;

  @override
  State<VerifyScreen> createState() => _VerifyScreenState();
}

class _VerifyScreenState extends State<VerifyScreen> {
  final _view = GlobalKey<AsyncViewState<_VerifyData>>();
  bool _sending = false;
  String? _notice;
  bool _noticeIsError = false;

  ApiClient get _api => context.read<AppState>().api;

  String get _base => '/api/challenges/${widget.challengeId}/verifications';

  Future<_VerifyData> _load() async {
    final mine = await _api.get('$_base/me') as Map<String, dynamic>;
    final list = await _api.get(_base) as List;
    return _VerifyData(
      state: mine['state'] as String,
      successDays: (mine['successDays'] as num).toInt(),
      targetCount: (mine['targetCount'] as num).toInt(),
      currentStreak: (mine['currentStreak'] as num).toInt(),
      today: list.map((v) => Verification.fromJson(v as Map<String, dynamic>)).toList(),
    );
  }

  Future<void> _shoot() async {
    final XFile? shot;
    try {
      shot = await ImagePicker().pickImage(source: ImageSource.camera, maxWidth: 1600, imageQuality: 85);
    } catch (_) {
      setState(() {
        _notice = '카메라를 열지 못했어요. 카메라 권한을 허용했는지 확인해 주세요.';
        _noticeIsError = true;
      });
      return;
    }
    if (shot == null) return; // 찍지 않고 닫음

    setState(() {
      _sending = true;
      _notice = null;
    });
    try {
      final result = await _api.upload(_base, await shot.readAsBytes(), 'verify.jpg') as Map<String, dynamic>;
      final inReview = result['status'] == 'IN_REVIEW';
      setState(() {
        _notice = inReview ? '인증을 받았어요. 사진은 관리자가 한 번 더 확인해요.' : '촬영이 확인됐습니다!';
        _noticeIsError = false;
      });
      await _view.currentState?.reload();
    } on ApiException catch (e) {
      setState(() {
        // VERIFICATION_REJECTED: AI 가 챌린지와 다른 사진이라고 본 것 → 저장되지 않았으니 바로 다시 찍는다
        _notice = e.code == 'VERIFICATION_REJECTED' ? '${e.message}\n다시 찍어 주세요.' : e.message;
        _noticeIsError = true;
      });
    } finally {
      if (mounted) setState(() => _sending = false);
    }
  }

  @override
  Widget build(BuildContext context) {
    return Scaffold(
      appBar: AppBar(title: Text(widget.title)),
      body: AsyncView<_VerifyData>(
        key: _view,
        load: _load,
        builder: (context, data, reload) => ListView(padding: const EdgeInsets.fromLTRB(20, 8, 20, 24), children: [
          Card(
            child: Padding(
              padding: const EdgeInsets.all(18),
              child: Column(crossAxisAlignment: CrossAxisAlignment.start, children: [
                const Text('오늘의 인증', style: TextStyle(fontSize: 18, fontWeight: FontWeight.w900)),
                const SizedBox(height: 6),
                Text('지금까지 ${data.successDays} / ${data.targetCount}회 · 연속 ${data.currentStreak}일',
                    style: const TextStyle(color: AppColors.muted)),
                const SizedBox(height: 16),
                if (data.state == 'OPEN')
                  FilledButton.icon(
                    onPressed: _sending ? null : _shoot,
                    icon: const Icon(Icons.photo_camera),
                    label: Text(_sending ? 'AI 가 사진을 확인하는 중…' : '카메라로 인증하기'),
                  )
                else
                  Container(
                    width: double.infinity,
                    padding: const EdgeInsets.all(14),
                    decoration: BoxDecoration(color: AppColors.surfaceAlt, borderRadius: BorderRadius.circular(12)),
                    child: Text(_closedLabel(data.state),
                        textAlign: TextAlign.center, style: const TextStyle(fontWeight: FontWeight.w700)),
                  ),
                if (_notice != null) ...[
                  const SizedBox(height: 12),
                  Text(_notice!,
                      style: TextStyle(
                          color: _noticeIsError ? AppColors.danger : AppColors.mint, fontWeight: FontWeight.w700)),
                ],
                const SizedBox(height: 10),
                const Text('지금 카메라로 찍은 사진만 올릴 수 있어요. 직접 학습한 AI 모델이 챌린지와 맞는 사진인지 확인해요.',
                    style: TextStyle(color: AppColors.muted, fontSize: 12.5, height: 1.4)),
              ]),
            ),
          ),
          const SizedBox(height: 24),
          Text('오늘 인증한 참가자 ${data.today.length}명', style: const TextStyle(fontSize: 16, fontWeight: FontWeight.w800)),
          const SizedBox(height: 10),
          if (data.today.isEmpty)
            const Padding(
              padding: EdgeInsets.symmetric(vertical: 24),
              child: Center(child: Text('아직 인증한 사람이 없어요. 첫 번째로 인증해 보세요!', style: TextStyle(color: AppColors.muted))),
            ),
          GridView.count(
            crossAxisCount: 2,
            shrinkWrap: true,
            physics: const NeverScrollableScrollPhysics(),
            mainAxisSpacing: 10,
            crossAxisSpacing: 10,
            childAspectRatio: 0.82,
            children: [
              for (final v in data.today) _VerificationTile(api: _api, path: '$_base/${v.id}/image', v: v),
            ],
          ),
        ]),
      ),
    );
  }

  String _closedLabel(String state) => switch (state) {
        'DONE_TODAY' => '오늘 인증을 마쳤어요 🎉',
        'WEEK_DONE' => '이번 주 인증 횟수를 다 채웠어요 🎉',
        'TIME_CLOSED' => '지금은 인증할 수 있는 시간이 아니에요.',
        'NOT_STARTED' => '아직 시작 전인 챌린지예요.',
        'ENDED' => '끝난 챌린지예요.',
        _ => '지금은 인증할 수 없어요.',
      };
}

class _VerifyData {
  _VerifyData({
    required this.state,
    required this.successDays,
    required this.targetCount,
    required this.currentStreak,
    required this.today,
  });

  final String state;
  final int successDays;
  final int targetCount;
  final int currentStreak;
  final List<Verification> today;
}

/// 참가자 인증 사진 한 칸. 참가자만 볼 수 있는 사진이라 토큰을 붙여 받는다
class _VerificationTile extends StatefulWidget {
  const _VerificationTile({required this.api, required this.path, required this.v});

  final ApiClient api;
  final String path;
  final Verification v;

  @override
  State<_VerificationTile> createState() => _VerificationTileState();
}

class _VerificationTileState extends State<_VerificationTile> {
  late final Future<Uint8List> _image = widget.api.bytes(widget.path);

  @override
  Widget build(BuildContext context) {
    final v = widget.v;
    return Card(
      clipBehavior: Clip.antiAlias,
      child: Column(crossAxisAlignment: CrossAxisAlignment.stretch, children: [
        Expanded(
          child: FutureBuilder<Uint8List>(
            future: _image,
            builder: (context, snap) {
              if (snap.hasData) return Image.memory(snap.data!, fit: BoxFit.cover);
              if (snap.hasError) {
                return const ColoredBox(
                  color: AppColors.surfaceAlt,
                  child: Icon(Icons.image_not_supported_outlined, color: AppColors.muted),
                );
              }
              return const ColoredBox(color: AppColors.surfaceAlt);
            },
          ),
        ),
        Padding(
          padding: const EdgeInsets.fromLTRB(10, 8, 10, 8),
          child: Row(children: [
            Expanded(
              child: Text(v.mine ? '나' : v.nickname,
                  overflow: TextOverflow.ellipsis, style: const TextStyle(fontWeight: FontWeight.w700)),
            ),
            if (v.status == 'IN_REVIEW') const Tag('확인 중', color: AppColors.muted),
            if (v.status != 'IN_REVIEW')
              Text('${two(v.receivedAt.hour)}:${two(v.receivedAt.minute)}',
                  style: const TextStyle(color: AppColors.muted, fontSize: 12)),
          ]),
        ),
      ]),
    );
  }
}
