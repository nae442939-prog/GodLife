import 'package:flutter/material.dart';
import 'package:provider/provider.dart';

import '../api/api_client.dart';
import '../app_state.dart';
import '../theme.dart';
import '../widgets/async_view.dart';
import '../widgets/category_icon.dart';
import '../widgets/design.dart';
import 'challenge_detail_screen.dart';
import 'verify_screen.dart';

// 기분은 이모지 대신 아이콘으로 그린다 (이모지 글꼴은 따로 내려받아야 해서 처음에 네모로 보인다)
const _moods = {
  'GREAT': Icons.sentiment_very_satisfied,
  'GOOD': Icons.sentiment_satisfied,
  'OKAY': Icons.sentiment_neutral,
  'SAD': Icons.sentiment_dissatisfied,
  'HARD': Icons.sentiment_very_dissatisfied,
};

// 달력 점 · 결과 꼬리표의 색 (앱 시안의 색 묶음)
const _failColors = (Color(0xFFF6E2DC), Color(0xFFC2574A));
const _restColors = (AppColors.surfaceAlt, AppColors.chipText);
const _idle = Color(0xFFC7C0B0);

// 그날 결과에는 카테고리 번호만 온다 (categories 기준 데이터)
const _categoryNames = {1: '운동', 2: '공부', 3: '독서', 4: '요리', 5: '기타'};

String _iso(DateTime d) => '${d.year}-${two(d.month)}-${two(d.day)}';

/// 갓생기록: 이번 달 요약 → 달력(성공 · 실패 · 일기 점) → 고른 날의 챌린지 결과와 일기
class RecordsScreen extends StatefulWidget {
  const RecordsScreen({super.key});

  @override
  State<RecordsScreen> createState() => _RecordsScreenState();
}

class _RecordsScreenState extends State<RecordsScreen> {
  late DateTime _month = DateTime(DateTime.now().year, DateTime.now().month);
  String? _selected; // 고른 날 (YYYY-MM-DD). 처음에는 서버가 알려 준 오늘
  int _version = 0;

  ApiClient get _api => context.read<AppState>().api;

  Future<_RecordData> _load() async {
    final month = await _api.get('/api/records?month=${_month.year}-${two(_month.month)}') as Map<String, dynamic>;
    final today = month['today'] as String;
    final inThisMonth = today.startsWith('${_month.year}-${two(_month.month)}');
    final selected = _selected ?? (inThisMonth ? today : null);
    final day = selected == null ? null : await _api.get('/api/records/days/$selected') as Map<String, dynamic>;
    return _RecordData(month, selected, day);
  }

  void _move(int delta) {
    setState(() {
      _month = DateTime(_month.year, _month.month + delta);
      _selected = null;
    });
  }

  Future<void> _write(String date) async {
    final saved = await showModalBottomSheet<bool>(
      context: context,
      isScrollControlled: true,
      backgroundColor: AppColors.bg,
      shape: const RoundedRectangleBorder(borderRadius: BorderRadius.vertical(top: Radius.circular(26))),
      builder: (_) => _DiarySheet(api: _api, date: date),
    );
    if (saved == true && mounted) setState(() => _version++);
  }

  /// 그날 결과 한 줄을 눌렀을 때: 오늘 아직 인증 전이면 인증 화면, 아니면 챌린지 상세. 돌아오면 다시 불러온다
  Future<void> _openItem(Map<String, dynamic> item) async {
    final id = (item['challengeId'] as num).toInt();
    final title = item['title'] as String;
    await Navigator.of(context).push(MaterialPageRoute(
      builder: (_) =>
          item['result'] == 'PENDING' ? VerifyScreen(challengeId: id, title: title) : ChallengeDetailScreen(id: id),
    ));
    if (mounted) setState(() => _version++);
  }

  /// 달력의 날짜를 누르면: 그날 챌린지마다 성공 · 실패 · 오늘 아직(누르면 인증)과 일기를 아래에서 올라오는 창으로 보여 준다.
  /// 창을 닫으면 그 날짜를 골라 둔다 (달력 아래에도 같은 내용이 보인다)
  Future<void> _showDay(String date, String today) async {
    final day = _api.get('/api/records/days/$date');
    await showModalBottomSheet<void>(
      context: context,
      isScrollControlled: true,
      backgroundColor: AppColors.bg,
      shape: const RoundedRectangleBorder(borderRadius: BorderRadius.vertical(top: Radius.circular(26))),
      builder: (sheetContext) => DraggableScrollableSheet(
        expand: false,
        initialChildSize: 0.6,
        minChildSize: 0.35,
        maxChildSize: 0.92,
        builder: (sheetContext, scroll) => FutureBuilder<dynamic>(
          future: day,
          builder: (sheetContext, snap) {
            if (snap.hasError) {
              return Center(
                  child: Text('${snap.error}', style: const TextStyle(color: AppColors.muted, fontWeight: FontWeight.w600)));
            }
            if (!snap.hasData) return const Center(child: CircularProgressIndicator());
            final data = snap.data as Map<String, dynamic>;
            final items = (data['items'] as List).cast<Map<String, dynamic>>();
            int count(String result) => items.where((i) => i['result'] == result).length;
            return ListView(controller: scroll, padding: const EdgeInsets.fromLTRB(20, 10, 20, 28), children: [
              Center(
                child: Container(
                  width: 38,
                  height: 4,
                  margin: const EdgeInsets.only(bottom: 14),
                  decoration: BoxDecoration(color: AppColors.border, borderRadius: BorderRadius.circular(99)),
                ),
              ),
              // 한눈에: 성공 · 실패 · 오늘 아직 개수
              if (items.isNotEmpty)
                Padding(
                  padding: const EdgeInsets.only(bottom: 12),
                  child: Wrap(spacing: 6, children: [
                    if (count('DONE') > 0) ColorTag.of('성공 ${count('DONE')}', ColorTag.mint),
                    if (count('FAIL') > 0) ColorTag.of('실패 ${count('FAIL')}', _failColors),
                    if (count('PENDING') > 0) ColorTag.of('오늘 아직 ${count('PENDING')}', ColorTag.gold),
                    if (count('REST') > 0) ColorTag.of('쉬는 날 ${count('REST')}', _restColors),
                  ]),
                ),
              ..._dayDetail(date, data, today, onLeave: () => Navigator.of(sheetContext).pop()),
            ]);
          },
        ),
      ),
    );
    if (mounted) setState(() => _selected = date);
  }

  Future<void> _deleteDiary(int id) async {
    try {
      await _api.delete('/api/records/diaries/$id');
      if (mounted) setState(() => _version++);
    } catch (e) {
      if (mounted) showMessage(context, '$e');
    }
  }

  @override
  Widget build(BuildContext context) {
    return AsyncView<_RecordData>(
      key: ValueKey('${_month.year}-${_month.month}/$_selected/$_version'),
      load: _load,
      builder: (context, data, reload) {
        final summary = data.month['summary'] as Map<String, dynamic>;
        final today = data.month['today'] as String;
        final days = {
          for (final d in data.month['days'] as List) (d as Map<String, dynamic>)['date'] as String: d,
        };
        final rate = summary['monthRate'];
        return ListView(padding: const EdgeInsets.fromLTRB(20, 4, 20, 28), children: [
          Row(children: [
            Expanded(child: _stat('연속 달성', '${summary['streak']}일', ColorTag.coral.$2)),
            const SizedBox(width: 10),
            Expanded(child: _stat('이번 달 성공률', rate == null ? '-' : '$rate%', AppColors.mint)),
            const SizedBox(width: 10),
            Expanded(child: _stat('이번 달 인증', '${summary['monthDone']}회', ColorTag.lav.$2)),
          ]),
          const SizedBox(height: 16),
          AppCard(
            bordered: false,
            padding: const EdgeInsets.fromLTRB(10, 8, 10, 14),
            child: Column(children: [
              Row(children: [
                IconButton(onPressed: () => _move(-1), icon: const Icon(Icons.chevron_left, color: _idle)),
                Expanded(
                  child: Text('${_month.year}년 ${_month.month}월',
                      textAlign: TextAlign.center,
                      style: const TextStyle(fontSize: 15, fontWeight: FontWeight.w800, letterSpacing: -0.1)),
                ),
                IconButton(onPressed: () => _move(1), icon: const Icon(Icons.chevron_right, color: _idle)),
              ]),
              const SizedBox(height: 2),
              Row(children: [
                for (final w in const ['일', '월', '화', '수', '목', '금', '토'])
                  Expanded(
                    child: Center(
                      child: Text(w,
                          style: const TextStyle(fontSize: 11, color: AppColors.navIdle, fontWeight: FontWeight.w700)),
                    ),
                  ),
              ]),
              const SizedBox(height: 6),
              _calendar(days, today, data.selected),
              const SizedBox(height: 10),
              Row(mainAxisAlignment: MainAxisAlignment.center, children: [
                _legend(AppColors.mint, '성공'),
                const SizedBox(width: 14),
                _legend(_failColors.$2, '실패'),
                const SizedBox(width: 14),
                _legend(ColorTag.gold.$2, '오늘 아직'),
                const SizedBox(width: 14),
                _legend(ColorTag.lav.$2, '일기', small: true),
              ]),
            ]),
          ),
          const SizedBox(height: 22),
          if (data.selected == null)
            const Padding(
              padding: EdgeInsets.symmetric(vertical: 24),
              child: Center(
                child: Text('날짜를 누르면 그날의 기록을 볼 수 있어요.',
                    style: TextStyle(color: AppColors.muted, fontSize: 12.5, fontWeight: FontWeight.w600)),
              ),
            )
          else
            ..._dayDetail(data.selected!, data.day!, today),
        ]);
      },
    );
  }

  Widget _calendar(Map<String, dynamic> days, String today, String? selected) {
    final first = DateTime(_month.year, _month.month, 1);
    final count = DateTime(_month.year, _month.month + 1, 0).day;
    final cells = <Widget>[
      for (var i = 0; i < first.weekday % 7; i++) const SizedBox.shrink(),
      for (var d = 1; d <= count; d++) _cell(DateTime(_month.year, _month.month, d), days, today, selected),
    ];
    return GridView.count(
      crossAxisCount: 7,
      shrinkWrap: true,
      physics: const NeverScrollableScrollPhysics(),
      childAspectRatio: 0.92,
      children: cells,
    );
  }

  /// 달력 한 칸: 고른 날은 먹색 동그라미, 아래 점은 그날 결과(성공 · 실패 · 오늘 아직)와 일기
  Widget _cell(DateTime date, Map<String, dynamic> days, String today, String? selected) {
    final iso = _iso(date);
    final d = days[iso] as Map<String, dynamic>?;
    final total = (d?['total'] as num?)?.toInt() ?? 0;
    final done = (d?['done'] as num?)?.toInt() ?? 0;
    final pending = (d?['pending'] as num?)?.toInt() ?? 0;
    // 해야 할 인증이 있던 날만 점을 찍는다: 다 했으면 성공, 오늘 남았으면 '오늘 아직', 못 했으면 실패
    Color? mark;
    if (total > 0) {
      mark = done >= total ? AppColors.mint : (pending > 0 ? ColorTag.gold.$2 : _failColors.$2);
    }
    final isSelected = iso == selected;
    final isToday = iso == today;
    return GestureDetector(
      behavior: HitTestBehavior.opaque,
      onTap: () => _showDay(iso, today),
      child: Column(mainAxisAlignment: MainAxisAlignment.center, children: [
        AnimatedContainer(
          duration: const Duration(milliseconds: 150),
          width: 30,
          height: 30,
          alignment: Alignment.center,
          decoration: BoxDecoration(
            color: isSelected ? AppColors.text : (isToday ? AppColors.surfaceAlt : Colors.transparent),
            shape: BoxShape.circle,
          ),
          child: Text('${date.day}',
              style: TextStyle(
                fontSize: 12.5,
                fontWeight: isSelected || isToday ? FontWeight.w800 : FontWeight.w600,
                color: isSelected ? Colors.white : AppColors.text,
              )),
        ),
        const SizedBox(height: 3),
        Row(mainAxisAlignment: MainAxisAlignment.center, children: [
          Container(
            width: 5,
            height: 5,
            decoration: BoxDecoration(color: mark ?? Colors.transparent, shape: BoxShape.circle),
          ),
          if (d?['diary'] == true) ...[
            const SizedBox(width: 3),
            Container(width: 4, height: 4, decoration: BoxDecoration(color: ColorTag.lav.$2, shape: BoxShape.circle)),
          ],
        ]),
      ]),
    );
  }

  /// [onLeave]: 창 안에서 그릴 때 다른 화면으로 넘어가기 전에 창을 닫는다
  List<Widget> _dayDetail(String date, Map<String, dynamic> day, String today, {VoidCallback? onLeave}) {
    final items = (day['items'] as List).cast<Map<String, dynamic>>();
    final diaries = (day['diaries'] as List).cast<Map<String, dynamic>>();
    final parts = date.split('-');
    // 일기는 오늘과 지난 날짜에만 쓴다
    final canWrite = date.compareTo(today) <= 0;
    return [
      Text('${int.parse(parts[1])}월 ${int.parse(parts[2])}일${date == today ? ' · 오늘' : ''}',
          style: const TextStyle(fontSize: 17, fontWeight: FontWeight.w800, letterSpacing: -0.2)),
      const SizedBox(height: 12),
      if (items.isEmpty)
        AppCard(
          bordered: false,
          radius: 18,
          padding: const EdgeInsets.all(16),
          child: const Row(children: [
            Text('이날은 인증할 챌린지가 없었어요.',
                style: TextStyle(color: AppColors.muted, fontSize: 12.5, fontWeight: FontWeight.w600)),
          ]),
        ),
      for (final item in items)
        Padding(
          padding: const EdgeInsets.only(bottom: 10),
          child: AppCard(
            bordered: false,
            radius: 18,
            // '오늘 아직'이면 바로 인증하러 가고, 그 밖에는 챌린지 상세를 연다
            onTap: () {
              onLeave?.call();
              _openItem(item);
            },
            child: Row(children: [
              CategoryIcon(_categoryNames[(item['categoryId'] as num).toInt()] ?? '기타',
                  title: item['title'] as String, size: 42),
              const SizedBox(width: 12),
              Expanded(
                child: Text(item['title'] as String,
                    maxLines: 1,
                    overflow: TextOverflow.ellipsis,
                    style: const TextStyle(fontSize: 13.5, fontWeight: FontWeight.w800, letterSpacing: -0.1)),
              ),
              const SizedBox(width: 10),
              switch (item['result'] as String) {
                'DONE' => ColorTag.of('성공', ColorTag.mint),
                'FAIL' => ColorTag.of('실패', _failColors),
                // 오늘 아직 인증 전: 다른 결과와 같은 크기의 꼬리표 + 화살표 (줄을 누르면 바로 인증하러 간다)
                'PENDING' => Row(mainAxisSize: MainAxisSize.min, children: [
                    ColorTag.of('인증하기', ColorTag.gold),
                    const Icon(Icons.chevron_right, size: 18, color: Color(0xFFC7C0B0)),
                  ]),
                _ => ColorTag.of('쉬는 날', _restColors),
              },
            ]),
          ),
        ),
      const SizedBox(height: 12),
      Row(children: [
        const Expanded(
          child: Text('일기', style: TextStyle(fontSize: 17, fontWeight: FontWeight.w800, letterSpacing: -0.2)),
        ),
        if (canWrite)
          GestureDetector(
            onTap: () {
              onLeave?.call();
              _write(date);
            },
            child: Container(
              padding: const EdgeInsets.symmetric(horizontal: 14, vertical: 8),
              decoration: BoxDecoration(color: AppColors.primary, borderRadius: BorderRadius.circular(999)),
              child: const Row(mainAxisSize: MainAxisSize.min, children: [
                Icon(Icons.edit_outlined, size: 13, color: Colors.white),
                SizedBox(width: 5),
                Text('일기 쓰기', style: TextStyle(color: Colors.white, fontSize: 12, fontWeight: FontWeight.w800)),
              ]),
            ),
          ),
      ]),
      const SizedBox(height: 12),
      if (diaries.isEmpty)
        const Padding(
          padding: EdgeInsets.symmetric(horizontal: 4),
          child: Text('아직 쓴 일기가 없어요. 일기는 나만 볼 수 있어요.',
              style: TextStyle(color: AppColors.muted, fontSize: 12.5, fontWeight: FontWeight.w600)),
        ),
      for (final diary in diaries)
        Padding(
          padding: const EdgeInsets.only(bottom: 10),
          child: AppCard(
            bordered: false,
            radius: 18,
            padding: const EdgeInsets.fromLTRB(14, 13, 6, 13),
            child: Row(crossAxisAlignment: CrossAxisAlignment.start, children: [
              Container(
                width: 36,
                height: 36,
                decoration: BoxDecoration(color: ColorTag.gold.$1, shape: BoxShape.circle),
                child: Icon(_moods[diary['mood']] ?? Icons.edit_note, size: 20, color: ColorTag.gold.$2),
              ),
              const SizedBox(width: 12),
              Expanded(
                child: Padding(
                  padding: const EdgeInsets.only(top: 7),
                  child: Text(
                    ((diary['content'] as String?) ?? '').isEmpty ? '(사진 일기)' : diary['content'] as String,
                    style: const TextStyle(fontSize: 12.5, fontWeight: FontWeight.w500, height: 1.55),
                  ),
                ),
              ),
              IconButton(
                tooltip: '일기 지우기',
                onPressed: () => _deleteDiary((diary['id'] as num).toInt()),
                icon: const Icon(Icons.delete_outline, size: 18, color: _idle),
              ),
            ]),
          ),
        ),
    ];
  }

  Widget _stat(String label, String value, Color color) {
    return AppCard(
      bordered: false,
      radius: 18,
      padding: const EdgeInsets.symmetric(vertical: 15, horizontal: 8),
      child: Column(children: [
        Text(value, style: TextStyle(fontSize: 17, fontWeight: FontWeight.w800, color: color, letterSpacing: -0.1)),
        const SizedBox(height: 5),
        Text(label, style: const TextStyle(fontSize: 10.5, color: AppColors.muted, fontWeight: FontWeight.w700)),
      ]),
    );
  }

  Widget _legend(Color color, String label, {bool small = false}) {
    return Row(children: [
      Container(
        width: small ? 5 : 7,
        height: small ? 5 : 7,
        decoration: BoxDecoration(color: color, shape: BoxShape.circle),
      ),
      const SizedBox(width: 4),
      Text(label, style: const TextStyle(fontSize: 10.5, color: AppColors.muted, fontWeight: FontWeight.w700)),
    ]);
  }
}

class _RecordData {
  _RecordData(this.month, this.selected, this.day);

  final Map<String, dynamic> month;
  final String? selected;
  final Map<String, dynamic>? day;
}

/// 일기 쓰기 (기분 + 글). 저장하면 true 를 돌려준다
class _DiarySheet extends StatefulWidget {
  const _DiarySheet({required this.api, required this.date});

  final ApiClient api;
  final String date;

  @override
  State<_DiarySheet> createState() => _DiarySheetState();
}

class _DiarySheetState extends State<_DiarySheet> {
  final _content = TextEditingController();
  String? _mood;
  bool _busy = false;
  String? _error;

  @override
  void dispose() {
    _content.dispose();
    super.dispose();
  }

  Future<void> _save() async {
    if (_content.text.trim().isEmpty && _mood == null) {
      setState(() => _error = '기분을 고르거나 글을 써 주세요.');
      return;
    }
    setState(() {
      _busy = true;
      _error = null;
    });
    try {
      await widget.api.post('/api/records/days/${widget.date}/diaries',
          body: {'content': _content.text.trim(), 'mood': _mood, 'challengeIds': <int>[]});
      if (mounted) Navigator.of(context).pop(true);
    } catch (e) {
      if (mounted) setState(() => _error = '$e');
    } finally {
      if (mounted) setState(() => _busy = false);
    }
  }

  @override
  Widget build(BuildContext context) {
    final parts = widget.date.split('-');
    return Padding(
      // 키보드가 올라오면 그만큼 위로 민다
      padding: EdgeInsets.fromLTRB(20, 22, 20, 20 + MediaQuery.of(context).viewInsets.bottom),
      child: Column(mainAxisSize: MainAxisSize.min, crossAxisAlignment: CrossAxisAlignment.stretch, children: [
        Text('${int.parse(parts[1])}월 ${int.parse(parts[2])}일 일기',
            style: const TextStyle(fontSize: 17, fontWeight: FontWeight.w800, letterSpacing: -0.2)),
        const SizedBox(height: 4),
        const Text('오늘 기분은 어땠나요?',
            style: TextStyle(color: AppColors.muted, fontSize: 12.5, fontWeight: FontWeight.w600)),
        const SizedBox(height: 14),
        Row(mainAxisAlignment: MainAxisAlignment.spaceBetween, children: [
          for (final e in _moods.entries)
            GestureDetector(
              onTap: () => setState(() => _mood = _mood == e.key ? null : e.key),
              child: AnimatedContainer(
                duration: const Duration(milliseconds: 150),
                width: 50,
                height: 50,
                decoration: BoxDecoration(
                  color: _mood == e.key ? AppColors.text : AppColors.surfaceAlt,
                  shape: BoxShape.circle,
                ),
                child: Icon(e.value, size: 26, color: _mood == e.key ? Colors.white : AppColors.chipText),
              ),
            ),
        ]),
        const SizedBox(height: 16),
        TextField(
          controller: _content,
          minLines: 4,
          maxLines: 8,
          maxLength: 500,
          decoration: const InputDecoration(hintText: '오늘 하루를 짧게 남겨 보세요'),
        ),
        if (_error != null)
          Text(_error!, style: const TextStyle(color: AppColors.danger, fontWeight: FontWeight.w600)),
        const SizedBox(height: 8),
        FilledButton(onPressed: _busy ? null : _save, child: Text(_busy ? '저장하는 중…' : '기록 저장하기')),
      ]),
    );
  }
}
