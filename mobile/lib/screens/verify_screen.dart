import 'dart:typed_data';

import 'package:flutter/material.dart';
import 'package:image_picker/image_picker.dart';
import 'package:provider/provider.dart';

import '../api/api_client.dart';
import '../api/models.dart';
import '../app_state.dart';
import '../theme.dart';
import '../widgets/async_view.dart';
import '../widgets/design.dart';

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
        builder: (context, data, reload) {
          final open = data.state == 'OPEN';
          final done = data.state == 'DONE_TODAY' || data.state == 'WEEK_DONE';
          final progress = data.targetCount == 0 ? 0.0 : (data.successDays / data.targetCount).clamp(0, 1).toDouble();
          return ListView(padding: const EdgeInsets.fromLTRB(20, 4, 20, 28), children: [
            // 오늘의 인증: 복숭아 → 민트 띠 안에 진행과 촬영 버튼
            Container(
              padding: const EdgeInsets.fromLTRB(22, 22, 22, 22),
              decoration: BoxDecoration(
                borderRadius: BorderRadius.circular(22),
                gradient: const LinearGradient(
                  begin: Alignment.topLeft,
                  end: Alignment.bottomRight,
                  colors: [Color(0xFFFDE7D8), Color(0xFFD9F1EA)],
                ),
              ),
              child: Column(crossAxisAlignment: CrossAxisAlignment.start, children: [
                Container(
                  padding: const EdgeInsets.symmetric(horizontal: 13, vertical: 6),
                  decoration: BoxDecoration(color: const Color(0xBFFFFFFF), borderRadius: BorderRadius.circular(999)),
                  child: Text(done ? '오늘 인증 완료' : '오늘의 인증',
                      style: TextStyle(
                          color: done ? AppColors.mint : const Color(0xFFD9693F),
                          fontSize: 11.5,
                          fontWeight: FontWeight.w800)),
                ),
                const SizedBox(height: 13),
                Text(_headline(data.state),
                    style: const TextStyle(fontSize: 20, fontWeight: FontWeight.w800, height: 1.35, letterSpacing: -0.4)),
                const SizedBox(height: 14),
                Row(children: [
                  Expanded(
                    child: ClipRRect(
                      borderRadius: BorderRadius.circular(999),
                      child: LinearProgressIndicator(
                        value: progress,
                        minHeight: 6,
                        backgroundColor: const Color(0x99FFFFFF),
                        color: AppColors.mint,
                      ),
                    ),
                  ),
                  const SizedBox(width: 10),
                  Text('${data.successDays}/${data.targetCount} · 연속 ${data.currentStreak}일',
                      style: const TextStyle(fontSize: 11.5, fontWeight: FontWeight.w700, color: Color(0xFF6B6558))),
                ]),
                if (open) ...[
                  const SizedBox(height: 18),
                  // 촬영 버튼
                  Container(
                    decoration: BoxDecoration(
                      borderRadius: BorderRadius.circular(999),
                      boxShadow: const [
                        BoxShadow(color: Color(0x80EE6A4D), blurRadius: 22, offset: Offset(0, 14), spreadRadius: -12),
                      ],
                    ),
                    child: Material(
                      color: _sending ? const Color(0xFFF2A893) : AppColors.primary,
                      borderRadius: BorderRadius.circular(999),
                      clipBehavior: Clip.antiAlias,
                      child: InkWell(
                        onTap: _sending ? null : _shoot,
                        child: Padding(
                          padding: const EdgeInsets.symmetric(vertical: 14),
                          child: Row(mainAxisAlignment: MainAxisAlignment.center, children: [
                            if (_sending)
                              const SizedBox(
                                width: 16,
                                height: 16,
                                child: CircularProgressIndicator(strokeWidth: 2, color: Colors.white),
                              )
                            else
                              const Icon(Icons.photo_camera, color: Colors.white, size: 18),
                            const SizedBox(width: 8),
                            Text(_sending ? 'AI 가 사진을 확인하는 중…' : '카메라로 인증하기',
                                style: const TextStyle(color: Colors.white, fontSize: 14, fontWeight: FontWeight.w800)),
                          ]),
                        ),
                      ),
                    ),
                  ),
                ],
              ]),
            ),
            if (_notice != null) ...[
              const SizedBox(height: 12),
              Container(
                padding: const EdgeInsets.all(14),
                decoration: BoxDecoration(
                  color: _noticeIsError ? const Color(0xFFF6E2DC) : ColorTag.mint.$1,
                  borderRadius: BorderRadius.circular(16),
                ),
                child: Row(crossAxisAlignment: CrossAxisAlignment.start, children: [
                  Icon(_noticeIsError ? Icons.error_outline : Icons.check_circle,
                      size: 18, color: _noticeIsError ? const Color(0xFFC2574A) : AppColors.mint),
                  const SizedBox(width: 8),
                  Expanded(
                    child: Text(_notice!,
                        style: TextStyle(
                            fontSize: 12.5,
                            fontWeight: FontWeight.w700,
                            height: 1.5,
                            color: _noticeIsError ? const Color(0xFFC2574A) : AppColors.mint)),
                  ),
                ]),
              ),
            ],
            const SizedBox(height: 12),
            const Padding(
              padding: EdgeInsets.symmetric(horizontal: 4),
              child: Text('지금 카메라로 찍은 사진만 올릴 수 있어요. 직접 학습한 AI 모델이 챌린지와 맞는 사진인지 확인해요.',
                  style: TextStyle(color: AppColors.navIdle, fontSize: 11, fontWeight: FontWeight.w600, height: 1.7)),
            ),
            const SizedBox(height: 22),
            Text('오늘 인증한 참가자 ${data.today.length}명',
                style: const TextStyle(fontSize: 17, fontWeight: FontWeight.w800, letterSpacing: -0.2)),
            const SizedBox(height: 12),
            if (data.today.isEmpty)
              AppCard(
                bordered: false,
                radius: 18,
                padding: const EdgeInsets.all(18),
                child: const Row(children: [
                  Expanded(
                    child: Text('아직 인증한 사람이 없어요. 첫 번째로 인증해 보세요!',
                        style: TextStyle(color: AppColors.muted, fontSize: 12.5, fontWeight: FontWeight.w600)),
                  ),
                ]),
              ),
            GridView.count(
              crossAxisCount: 2,
              shrinkWrap: true,
              physics: const NeverScrollableScrollPhysics(),
              mainAxisSpacing: 12,
              crossAxisSpacing: 12,
              childAspectRatio: 0.84,
              children: [
                for (final v in data.today) _VerificationTile(api: _api, path: '$_base/${v.id}/image', v: v),
              ],
            ),
          ]);
        },
      ),
    );
  }

  String _headline(String state) => switch (state) {
        'OPEN' => '오늘 인증을\n아직 안 했어요',
        'DONE_TODAY' => '오늘 인증을 마쳤어요!\n내일도 이어가요',
        'WEEK_DONE' => '이번 주 인증 횟수를\n다 채웠어요!',
        'TIME_CLOSED' => '지금은 인증할 수 있는\n시간이 아니에요',
        'NOT_STARTED' => '아직 시작 전인\n챌린지예요',
        'ENDED' => '끝난 챌린지예요',
        _ => '지금은 인증할 수 없어요',
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
    return AppCard(
      bordered: false,
      radius: 18,
      padding: EdgeInsets.zero,
      child: Column(crossAxisAlignment: CrossAxisAlignment.stretch, children: [
        Expanded(
          child: FutureBuilder<Uint8List>(
            future: _image,
            builder: (context, snap) {
              if (snap.hasData) return Image.memory(snap.data!, fit: BoxFit.cover);
              if (snap.hasError) {
                return const ColoredBox(
                  color: AppColors.surfaceAlt,
                  child: Icon(Icons.image_not_supported_outlined, color: AppColors.navIdle),
                );
              }
              return const ColoredBox(color: AppColors.surfaceAlt);
            },
          ),
        ),
        Padding(
          padding: const EdgeInsets.fromLTRB(12, 9, 12, 10),
          child: Row(children: [
            Expanded(
              child: Text(v.mine ? '나' : v.nickname,
                  overflow: TextOverflow.ellipsis,
                  style: const TextStyle(fontSize: 12.5, fontWeight: FontWeight.w800)),
            ),
            if (v.status == 'IN_REVIEW')
              ColorTag.of('확인 중', ColorTag.gold)
            else
              Text('${two(v.receivedAt.hour)}:${two(v.receivedAt.minute)}',
                  style: const TextStyle(color: AppColors.muted, fontSize: 10.5, fontWeight: FontWeight.w700)),
          ]),
        ),
      ]),
    );
  }
}
