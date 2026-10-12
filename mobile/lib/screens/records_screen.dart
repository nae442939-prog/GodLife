import 'package:flutter/material.dart';
import 'package:provider/provider.dart';

import '../api/api_client.dart';
import '../app_state.dart';
import '../theme.dart';
import '../widgets/async_view.dart';

// 기분은 이모지 대신 아이콘으로 그린다 (이모지 글꼴은 따로 내려받아야 해서 처음에 네모로 보인다)
const _moods = {
  'GREAT': Icons.sentiment_very_satisfied,
  'GOOD': Icons.sentiment_satisfied,
  'OKAY': Icons.sentiment_neutral,
  'SAD': Icons.sentiment_dissatisfied,
  'HARD': Icons.sentiment_very_dissatisfied,
};

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
      builder: (_) => _DiarySheet(api: _api, date: date),
    );
    if (saved == true && mounted) setState(() => _version++);
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
        return ListView(padding: const EdgeInsets.fromLTRB(16, 8, 16, 28), children: [
          Row(children: [
            Expanded(child: _stat('연속 달성', '${summary['streak']}일')),
            const SizedBox(width: 10),
            Expanded(child: _stat('이번 달 성공률', rate == null ? '-' : '$rate%')),
            const SizedBox(width: 10),
            Expanded(child: _stat('이번 달 인증', '${summary['monthDone']}회')),
          ]),
          const SizedBox(height: 12),
          Card(
            child: Padding(
              padding: const EdgeInsets.fromLTRB(8, 6, 8, 12),
              child: Column(children: [
                Row(children: [
                  IconButton(onPressed: () => _move(-1), icon: const Icon(Icons.chevron_left)),
                  Expanded(
                    child: Text('${_month.year}년 ${_month.month}월',
                        textAlign: TextAlign.center, style: const TextStyle(fontSize: 17, fontWeight: FontWeight.w800)),
                  ),
                  IconButton(onPressed: () => _move(1), icon: const Icon(Icons.chevron_right)),
                ]),
                Row(children: [
                  for (final w in const ['일', '월', '화', '수', '목', '금', '토'])
                    Expanded(
                      child: Center(
                        child: Text(w, style: const TextStyle(fontSize: 12, color: AppColors.muted, fontWeight: FontWeight.w600)),
                      ),
                    ),
                ]),
                const SizedBox(height: 4),
                _calendar(days, today, data.selected),
                const SizedBox(height: 8),
                Row(mainAxisAlignment: MainAxisAlignment.center, children: [
                  _legend(AppColors.mint, '성공'),
                  const SizedBox(width: 14),
                  _legend(AppColors.danger, '실패'),
                  const SizedBox(width: 14),
                  _legend(AppColors.primary, '오늘 아직'),
                  const SizedBox(width: 14),
                  _legend(AppColors.brown, '일기', small: true),
                ]),
              ]),
            ),
          ),
          const SizedBox(height: 18),
          if (data.selected == null)
            const Padding(
              padding: EdgeInsets.symmetric(vertical: 24),
              child: Center(child: Text('날짜를 누르면 그날의 기록을 볼 수 있어요.', style: TextStyle(color: AppColors.muted))),
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
      childAspectRatio: 0.95,
      children: cells,
    );
  }

  Widget _cell(DateTime date, Map<String, dynamic> days, String today, String? selected) {
    final iso = _iso(date);
    final d = days[iso] as Map<String, dynamic>?;
    final total = (d?['total'] as num?)?.toInt() ?? 0;
    final done = (d?['done'] as num?)?.toInt() ?? 0;
    final pending = (d?['pending'] as num?)?.toInt() ?? 0;
    // 해야 할 인증이 있던 날만 색을 칠한다: 다 했으면 성공, 오늘 남았으면 '오늘 아직', 못 했으면 실패
    Color? mark;
    if (total > 0) {
      mark = done >= total ? AppColors.mint : (pending > 0 ? AppColors.primary : AppColors.danger);
    }
    final isSelected = iso == selected;
    return InkWell(
      borderRadius: BorderRadius.circular(10),
      onTap: () => setState(() => _selected = iso),
      child: Container(
        margin: const EdgeInsets.all(2),
        decoration: BoxDecoration(
          color: isSelected ? AppColors.surfaceAlt : null,
          borderRadius: BorderRadius.circular(10),
          border: Border.all(color: isSelected ? AppColors.brown : Colors.transparent, width: 1.5),
        ),
        child: Column(mainAxisAlignment: MainAxisAlignment.center, children: [
          Text('${date.day}',
              style: TextStyle(
                fontSize: 14,
                fontWeight: iso == today ? FontWeight.w900 : FontWeight.w600,
                color: iso == today ? AppColors.brown : AppColors.text,
              )),
          const SizedBox(height: 3),
          Row(mainAxisAlignment: MainAxisAlignment.center, children: [
            Container(
              width: 7,
              height: 7,
              decoration: BoxDecoration(color: mark ?? Colors.transparent, shape: BoxShape.circle),
            ),
            if (d?['diary'] == true) ...[
              const SizedBox(width: 3),
              Container(width: 4, height: 4, decoration: const BoxDecoration(color: AppColors.brown, shape: BoxShape.circle)),
            ],
          ]),
        ]),
      ),
    );
  }

  List<Widget> _dayDetail(String date, Map<String, dynamic> day, String today) {
    final items = (day['items'] as List).cast<Map<String, dynamic>>();
    final diaries = (day['diaries'] as List).cast<Map<String, dynamic>>();
    final parts = date.split('-');
    // 일기는 오늘과 지난 날짜에만 쓴다
    final canWrite = date.compareTo(today) <= 0;
    return [
      Padding(
        padding: const EdgeInsets.symmetric(horizontal: 4),
        child: Text('${int.parse(parts[1])}월 ${int.parse(parts[2])}일${date == today ? ' (오늘)' : ''}',
            style: const TextStyle(fontSize: 17, fontWeight: FontWeight.w800)),
      ),
      const SizedBox(height: 8),
      Card(
        child: Column(children: [
          if (items.isEmpty)
            const Padding(
              padding: EdgeInsets.all(18),
              child: Text('이날은 인증할 챌린지가 없었어요.', style: TextStyle(color: AppColors.muted)),
            ),
          for (final (i, item) in items.indexed) ...[
            if (i > 0) const Divider(height: 1, color: AppColors.border),
            ListTile(
              title: Text(item['title'] as String, style: const TextStyle(fontWeight: FontWeight.w700)),
              trailing: switch (item['result'] as String) {
                'DONE' => const Tag('성공', color: AppColors.mint),
                'FAIL' => const Tag('실패', color: AppColors.danger),
                'PENDING' => const Tag('오늘 아직', color: AppColors.primary),
                _ => const Tag('쉬는 날', color: AppColors.muted),
              },
            ),
          ],
        ]),
      ),
      const SizedBox(height: 18),
      Padding(
        padding: const EdgeInsets.symmetric(horizontal: 4),
        child: Row(children: [
          const Expanded(child: Text('일기', style: TextStyle(fontSize: 17, fontWeight: FontWeight.w800))),
          if (canWrite)
            TextButton.icon(
              onPressed: () => _write(date),
              icon: const Icon(Icons.edit, size: 18),
              label: const Text('일기 쓰기'),
            ),
        ]),
      ),
      if (diaries.isEmpty)
        const Padding(
          padding: EdgeInsets.fromLTRB(4, 8, 4, 0),
          child: Text('아직 쓴 일기가 없어요. 일기는 나만 볼 수 있어요.', style: TextStyle(color: AppColors.muted)),
        ),
      for (final diary in diaries) ...[
        const SizedBox(height: 8),
        Card(
          child: Padding(
            padding: const EdgeInsets.fromLTRB(16, 12, 6, 12),
            child: Row(crossAxisAlignment: CrossAxisAlignment.start, children: [
              if (diary['mood'] != null)
                Padding(
                  padding: const EdgeInsets.only(right: 10, top: 2),
                  child: Icon(_moods[diary['mood']], size: 24, color: AppColors.brown),
                ),
              Expanded(
                child: Text(
                  ((diary['content'] as String?) ?? '').isEmpty ? '(사진 일기)' : diary['content'] as String,
                  style: const TextStyle(height: 1.5),
                ),
              ),
              IconButton(
                tooltip: '일기 지우기',
                onPressed: () => _deleteDiary((diary['id'] as num).toInt()),
                icon: const Icon(Icons.delete_outline, size: 20, color: AppColors.muted),
              ),
            ]),
          ),
        ),
      ],
    ];
  }

  Widget _stat(String label, String value) {
    return Card(
      child: Padding(
        padding: const EdgeInsets.symmetric(vertical: 14, horizontal: 8),
        child: Column(children: [
          Text(value, style: const TextStyle(fontSize: 19, fontWeight: FontWeight.w900, color: AppColors.brown)),
          const SizedBox(height: 4),
          Text(label, style: const TextStyle(fontSize: 12, color: AppColors.muted, fontWeight: FontWeight.w600)),
        ]),
      ),
    );
  }

  Widget _legend(Color color, String label, {bool small = false}) {
    return Row(children: [
      Container(
        width: small ? 5 : 8,
        height: small ? 5 : 8,
        decoration: BoxDecoration(color: color, shape: BoxShape.circle),
      ),
      const SizedBox(width: 4),
      Text(label, style: const TextStyle(fontSize: 12, color: AppColors.muted)),
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
    return Padding(
      // 키보드가 올라오면 그만큼 위로 민다
      padding: EdgeInsets.fromLTRB(20, 20, 20, 20 + MediaQuery.of(context).viewInsets.bottom),
      child: Column(mainAxisSize: MainAxisSize.min, crossAxisAlignment: CrossAxisAlignment.stretch, children: [
        Text('${widget.date} 일기', style: const TextStyle(fontSize: 18, fontWeight: FontWeight.w900)),
        const SizedBox(height: 14),
        Row(mainAxisAlignment: MainAxisAlignment.spaceBetween, children: [
          for (final e in _moods.entries)
            InkWell(
              borderRadius: BorderRadius.circular(99),
              onTap: () => setState(() => _mood = _mood == e.key ? null : e.key),
              child: Container(
                padding: const EdgeInsets.all(10),
                decoration: BoxDecoration(
                  color: _mood == e.key ? AppColors.surfaceAlt : null,
                  shape: BoxShape.circle,
                  border: Border.all(color: _mood == e.key ? AppColors.brown : AppColors.border, width: 1.5),
                ),
                child: Icon(e.value, size: 28, color: _mood == e.key ? AppColors.brown : AppColors.muted),
              ),
            ),
        ]),
        const SizedBox(height: 14),
        TextField(
          controller: _content,
          minLines: 4,
          maxLines: 8,
          maxLength: 500,
          decoration: const InputDecoration(hintText: '오늘 하루는 어땠나요?'),
        ),
        if (_error != null) Text(_error!, style: const TextStyle(color: AppColors.danger)),
        const SizedBox(height: 8),
        FilledButton(onPressed: _busy ? null : _save, child: Text(_busy ? '저장하는 중…' : '기록 저장하기')),
      ]),
    );
  }
}
