import 'dart:typed_data';

import 'package:flutter/material.dart';
import 'package:provider/provider.dart';

import '../app_state.dart';
import '../theme.dart';
import '../widgets/async_view.dart';
import '../widgets/design.dart';

const _moodIcons = {
  'GREAT': Icons.sentiment_very_satisfied,
  'GOOD': Icons.sentiment_satisfied,
  'OKAY': Icons.sentiment_neutral,
  'SAD': Icons.sentiment_dissatisfied,
  'HARD': Icons.sentiment_very_dissatisfied,
};
const _moodNames = {'GREAT': '최고예요', 'GOOD': '좋아요', 'OKAY': '보통이에요', 'SAD': '아쉬워요', 'HARD': '힘들었어요'};

/// 일기 목록: 언제 무슨 일기를 썼는지 날짜별로 모아 본다 (최근 날짜부터).
/// [focusDate] 를 주면 그 날짜로 바로 내려가고 그날 일기를 강조한다 (갓생기록 달력에서 일기를 눌렀을 때)
class DiaryListScreen extends StatefulWidget {
  const DiaryListScreen({super.key, this.focusDate, this.focusId});

  final String? focusDate;
  final int? focusId;

  @override
  State<DiaryListScreen> createState() => _DiaryListScreenState();
}

class _DiaryListScreenState extends State<DiaryListScreen> {
  final _focusKey = GlobalKey();
  bool _scrolled = false;

  /// 처음 그린 뒤 한 번만 고른 날짜로 내려간다
  void _scrollToFocus() {
    if (_scrolled || widget.focusDate == null) return;
    _scrolled = true;
    WidgetsBinding.instance.addPostFrameCallback((_) {
      final target = _focusKey.currentContext;
      if (target != null) {
        Scrollable.ensureVisible(target, duration: const Duration(milliseconds: 300), alignment: 0.05);
      }
    });
  }

  @override
  Widget build(BuildContext context) {
    final api = context.read<AppState>().api;
    return Scaffold(
      appBar: AppBar(title: const Text('일기 목록')),
      body: SafeArea(
        child: AsyncView<List<Map<String, dynamic>>>(
          load: () async => ((await api.get('/api/records/diaries')) as List).cast<Map<String, dynamic>>(),
          builder: (context, diaries, reload) {
            if (diaries.isEmpty) {
              return ListView(padding: const EdgeInsets.all(28), children: const [
                Text('아직 쓴 일기가 없어요.\n갓생기록에서 오늘의 일기를 써 보세요.',
                    textAlign: TextAlign.center,
                    style: TextStyle(color: AppColors.muted, fontSize: 13, fontWeight: FontWeight.w600, height: 1.6)),
              ]);
            }
            // 날짜별로 묶는다 (서버가 최근 날짜부터, 같은 날은 쓴 순서로 준다)
            final byDate = <String, List<Map<String, dynamic>>>{};
            for (final d in diaries) {
              byDate.putIfAbsent(d['date'] as String, () => []).add(d);
            }
            _scrollToFocus();
            return ListView(padding: const EdgeInsets.fromLTRB(20, 4, 20, 28), children: [
              Text('지금까지 ${byDate.length}일 동안 일기 ${diaries.length}개를 썼어요',
                  style: const TextStyle(color: AppColors.muted, fontSize: 12.5, fontWeight: FontWeight.w600)),
              const SizedBox(height: 14),
              for (final entry in byDate.entries) ...[
                Padding(
                  key: entry.key == widget.focusDate ? _focusKey : null,
                  padding: const EdgeInsets.only(top: 6, bottom: 10),
                  child: Row(children: [
                    Text(koreanDate(entry.key),
                        style: const TextStyle(fontSize: 15.5, fontWeight: FontWeight.w800, letterSpacing: -0.2)),
                    if (entry.key == widget.focusDate) ...[
                      const SizedBox(width: 8),
                      ColorTag.of('고른 날', ColorTag.gold),
                    ],
                  ]),
                ),
                for (final d in entry.value)
                  _DiaryCard(d, highlighted: entry.key == widget.focusDate || d['id'] == widget.focusId),
                const SizedBox(height: 8),
              ],
            ]);
          },
        ),
      ),
    );
  }
}

/// 일기 한 개: 기분 · 쓴 시각 · 글 · 사진
class _DiaryCard extends StatefulWidget {
  const _DiaryCard(this.diary, {required this.highlighted});

  final Map<String, dynamic> diary;
  final bool highlighted;

  @override
  State<_DiaryCard> createState() => _DiaryCardState();
}

class _DiaryCardState extends State<_DiaryCard> {
  // 일기 사진은 본인만 볼 수 있어 토큰을 붙여 받는다
  late final Future<Uint8List>? _photo = widget.diary['photo'] == true
      ? context.read<AppState>().api.bytes('/api/records/diaries/${widget.diary['id']}/photo')
      : null;

  @override
  Widget build(BuildContext context) {
    final d = widget.diary;
    final mood = d['mood'] as String?;
    final content = (d['content'] as String?) ?? '';
    final written = DateTime.tryParse('${d['createdAt']}');
    final photo = _photo;
    return Container(
      margin: const EdgeInsets.only(bottom: 10),
      decoration: BoxDecoration(
        borderRadius: BorderRadius.circular(18),
        border: widget.highlighted ? Border.all(color: ColorTag.gold.$2, width: 1.6) : null,
      ),
      child: AppCard(
        bordered: false,
        radius: 18,
        padding: const EdgeInsets.all(14),
        child: Column(crossAxisAlignment: CrossAxisAlignment.start, children: [
          Row(children: [
            Container(
              width: 34,
              height: 34,
              decoration: BoxDecoration(color: ColorTag.gold.$1, shape: BoxShape.circle),
              child: Icon(_moodIcons[mood] ?? Icons.edit_note, size: 19, color: ColorTag.gold.$2),
            ),
            const SizedBox(width: 10),
            Expanded(
              child: Text(
                  [if (mood != null) _moodNames[mood], if (written != null) '${two(written.hour)}:${two(written.minute)}에 씀']
                      .whereType<String>()
                      .join(' · '),
                  style: const TextStyle(color: AppColors.muted, fontSize: 12, fontWeight: FontWeight.w700)),
            ),
          ]),
          if (content.trim().isNotEmpty) ...[
            const SizedBox(height: 10),
            Text(content, style: const TextStyle(fontSize: 13.5, fontWeight: FontWeight.w500, height: 1.6)),
          ],
          if (photo != null) ...[
            const SizedBox(height: 10),
            ClipRRect(
              borderRadius: BorderRadius.circular(12),
              child: FutureBuilder<Uint8List>(
                future: photo,
                builder: (context, snap) => snap.hasData
                    ? Image.memory(snap.data!, height: 180, width: double.infinity, fit: BoxFit.cover)
                    : const SizedBox(height: 180, child: ColoredBox(color: AppColors.surfaceAlt)),
              ),
            ),
          ],
        ]),
      ),
    );
  }
}
