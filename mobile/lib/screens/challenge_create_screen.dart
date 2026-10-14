import 'dart:typed_data';

import 'package:flutter/material.dart';
import 'package:image_picker/image_picker.dart';
import 'package:provider/provider.dart';

import '../api/models.dart';
import '../app_state.dart';
import '../theme.dart';
import '../widgets/async_view.dart';
import '../widgets/category_icon.dart';

String _iso(DateTime d) => '${d.year}-${two(d.month)}-${two(d.day)}';

/// 챌린지 만들기. 고를 것은 대부분 버튼으로 두어 손가락으로 몇 번만 누르면 끝나게 했다.
/// 만들면 새 챌린지의 id 를 돌려준다. (비공개 · 인증 시간대 같은 자세한 설정은 웹에서 한다)
class ChallengeCreateScreen extends StatefulWidget {
  const ChallengeCreateScreen({super.key});

  @override
  State<ChallengeCreateScreen> createState() => _ChallengeCreateScreenState();
}

class _ChallengeCreateScreenState extends State<ChallengeCreateScreen> {
  final _title = TextEditingController();
  final _description = TextEditingController();

  /// 대표 사진 (고르지 않으면 종류 아이콘이 대신 나온다)
  Uint8List? _cover;

  List<Category> _categories = [];
  String _mode = 'FREE';
  Category? _category;
  int? _subTypeId;
  // 기본은 오늘 시작: 만들자마자 인증해 볼 수 있다 (내일로 두면 오늘은 인증이 막혀 고장으로 오해하기 쉽다)
  late DateTime _start = DateTime.now();
  int _days = 14;
  int? _weekly; // null = 매일
  int _fee = 3000;
  int _max = 20;
  bool _busy = false;
  String? _error;

  @override
  void initState() {
    super.initState();
    _loadCategories();
  }

  @override
  void dispose() {
    _title.dispose();
    _description.dispose();
    super.dispose();
  }

  Future<void> _loadCategories() async {
    try {
      final data = await context.read<AppState>().api.get('/api/categories') as List;
      if (mounted) {
        setState(() => _categories = data.map((c) => Category.fromJson(c as Map<String, dynamic>)).toList());
      }
    } catch (e) {
      if (mounted) setState(() => _error = '$e');
    }
  }

  Future<void> _pickStart() async {
    final today = DateTime.now();
    final picked = await showDatePicker(
      context: context,
      initialDate: _start,
      firstDate: DateTime(today.year, today.month, today.day),
      lastDate: today.add(const Duration(days: 60)),
      helpText: '시작일',
    );
    if (picked != null) setState(() => _start = picked);
  }

  Future<void> _pickCover() async {
    final XFile? picked;
    try {
      picked = await ImagePicker().pickImage(source: ImageSource.gallery, maxWidth: 1600, imageQuality: 85);
    } catch (_) {
      if (mounted) setState(() => _error = '사진을 불러오지 못했어요.');
      return;
    }
    if (picked == null) return;
    final bytes = await picked.readAsBytes();
    if (mounted) setState(() => _cover = bytes);
  }

  Future<void> _submit() async {
    final String? problem = _category == null
        ? '카테고리를 골라 주세요.'
        : _title.text.trim().isEmpty
            ? '제목을 입력해 주세요.'
            : _description.text.trim().isEmpty
                ? '어떻게 인증하는지 설명을 적어 주세요.'
                : null;
    if (problem != null) {
      setState(() => _error = problem);
      return;
    }
    setState(() {
      _busy = true;
      _error = null;
    });
    try {
      final api = context.read<AppState>().api;
      final data = await api.post('/api/challenges', body: {
        'categoryId': _category!.id,
        'subTypeId': _subTypeId,
        'title': _title.text.trim(),
        'description': _description.text.trim(),
        'mode': _mode,
        'startDate': _iso(_start),
        'endDate': _iso(_start.add(Duration(days: _days - 1))),
        'frequencyType': _weekly == null ? 'DAILY' : 'WEEKLY_N',
        'weeklyCount': _weekly,
        'entryFee': _mode == 'BET' ? _fee : null,
        'maxParticipants': _max,
      });
      final id = ((data as Map)['id'] as num).toInt();
      if (_cover != null) {
        // 챌린지는 이미 만들어졌다: 사진만 실패하면 알려 주고 넘어간다
        try {
          await api.upload('/api/challenges/$id/cover', _cover!, 'cover.jpg');
        } catch (e) {
          if (mounted) showMessage(context, '챌린지는 만들었지만 대표 사진을 올리지 못했어요. ($e)');
        }
      }
      if (mounted) Navigator.of(context).pop(id);
    } catch (e) {
      if (mounted) setState(() => _error = '$e');
    } finally {
      if (mounted) setState(() => _busy = false);
    }
  }

  @override
  Widget build(BuildContext context) {
    final end = _start.add(Duration(days: _days - 1));
    return Scaffold(
      appBar: AppBar(title: const Text('챌린지 만들기')),
      body: SafeArea(
        child: Column(children: [
          Expanded(
            child: ListView(padding: const EdgeInsets.fromLTRB(20, 4, 20, 24), children: [
              _label('어떤 챌린지인가요?'),
              Row(children: [
                Expanded(child: _modeCard('FREE', '무료 챌린지', '포인트 없이 가볍게', Icons.emoji_people)),
                const SizedBox(width: 12),
                Expanded(child: _modeCard('BET', '포인트 챌린지', '포인트를 걸고 진지하게', Icons.savings_outlined)),
              ]),
              _label('카테고리'),
              Wrap(spacing: 10, runSpacing: 10, children: [
                for (final c in _categories)
                  InkWell(
                    borderRadius: BorderRadius.circular(16),
                    onTap: () => setState(() {
                      _category = c;
                      _subTypeId = null;
                    }),
                    child: Container(
                      width: 62,
                      padding: const EdgeInsets.symmetric(vertical: 8),
                      decoration: BoxDecoration(
                        borderRadius: BorderRadius.circular(16),
                        border: Border.all(color: _category?.id == c.id ? AppColors.brown : Colors.transparent, width: 2),
                        color: _category?.id == c.id ? AppColors.surface : null,
                      ),
                      child: Column(children: [
                        CategoryIcon(c.name, size: 42),
                        const SizedBox(height: 6),
                        Text(c.name, style: const TextStyle(fontSize: 12.5, fontWeight: FontWeight.w700)),
                      ]),
                    ),
                  ),
              ]),
              if (_category != null && _category!.subTypes.isNotEmpty) ...[
                const SizedBox(height: 12),
                const Text('세부 종류를 고르면 AI 가 그 종류의 사진인지 확인해요.',
                    style: TextStyle(color: AppColors.muted, fontSize: 12.5)),
                const SizedBox(height: 6),
                Wrap(spacing: 8, runSpacing: 4, children: [
                  _choice('그 밖', _subTypeId == null, () => setState(() => _subTypeId = null)),
                  for (final s in _category!.subTypes)
                    _choice(s.name, _subTypeId == s.id, () => setState(() => _subTypeId = s.id)),
                ]),
              ],
              _label('제목'),
              TextField(
                controller: _title,
                maxLength: 50,
                decoration: const InputDecoration(hintText: '예: 아침 6시 기상', counterText: ''),
              ),
              _label('어떻게 인증하나요?'),
              TextField(
                controller: _description,
                minLines: 3,
                maxLines: 6,
                maxLength: 1000,
                decoration: const InputDecoration(hintText: '예: 일어나서 시계가 보이게 사진을 찍어 주세요.', counterText: ''),
              ),
              _label('기간'),
              Wrap(spacing: 8, runSpacing: 4, children: [
                for (final d in const [7, 14, 21, 30]) _choice('$d일', _days == d, () => setState(() => _days = d)),
              ]),
              const SizedBox(height: 8),
              InkWell(
                borderRadius: BorderRadius.circular(12),
                onTap: _pickStart,
                child: Container(
                  padding: const EdgeInsets.symmetric(horizontal: 14, vertical: 14),
                  decoration: BoxDecoration(
                    color: AppColors.surface,
                    borderRadius: BorderRadius.circular(12),
                    border: Border.all(color: AppColors.border),
                  ),
                  child: Row(children: [
                    const Icon(Icons.calendar_today, size: 18, color: AppColors.brown),
                    const SizedBox(width: 10),
                    Expanded(
                      child: Text('${_iso(_start)} 시작 → ${_iso(end)} 종료',
                          style: const TextStyle(fontWeight: FontWeight.w700)),
                    ),
                    const Text('시작일 바꾸기', style: TextStyle(color: AppColors.muted, fontSize: 12.5)),
                  ]),
                ),
              ),
              Padding(
                padding: const EdgeInsets.fromLTRB(4, 8, 4, 0),
                child: Text(
                    daysUntil(_iso(_start)) > 0
                        ? '인증은 시작일(${koreanDate(_iso(_start))})부터 할 수 있어요. 오늘 바로 인증하려면 시작일을 오늘로 골라 주세요.'
                        : '오늘 시작해서 오늘부터 바로 인증할 수 있어요.',
                    style: const TextStyle(color: AppColors.muted, fontSize: 12, fontWeight: FontWeight.w600, height: 1.5)),
              ),
              _label('대표 사진 (선택)'),
              GestureDetector(
                onTap: _pickCover,
                child: ClipRRect(
                  borderRadius: BorderRadius.circular(18),
                  child: Container(
                    height: 150,
                    color: AppColors.surfaceAlt,
                    alignment: Alignment.center,
                    child: _cover == null
                        ? const Column(mainAxisSize: MainAxisSize.min, children: [
                            Icon(Icons.add_photo_alternate_outlined, color: AppColors.chipText, size: 28),
                            SizedBox(height: 6),
                            Text('목록 카드에 보일 사진을 골라 주세요',
                                style: TextStyle(color: AppColors.chipText, fontSize: 12, fontWeight: FontWeight.w700)),
                          ])
                        : Stack(fit: StackFit.expand, children: [
                            Image.memory(_cover!, fit: BoxFit.cover),
                            Positioned(
                              right: 10,
                              top: 10,
                              child: GestureDetector(
                                onTap: () => setState(() => _cover = null),
                                child: Container(
                                  padding: const EdgeInsets.all(6),
                                  decoration: const BoxDecoration(color: Color(0x99000000), shape: BoxShape.circle),
                                  child: const Icon(Icons.close, color: Colors.white, size: 16),
                                ),
                              ),
                            ),
                          ]),
                  ),
                ),
              ),
              _label('얼마나 자주 인증하나요?'),
              Wrap(spacing: 8, runSpacing: 4, children: [
                _choice('매일', _weekly == null, () => setState(() => _weekly = null)),
                for (final n in const [2, 3, 5]) _choice('주 $n회', _weekly == n, () => setState(() => _weekly = n)),
              ]),
              if (_mode == 'BET') ...[
                _label('참가 포인트'),
                Wrap(spacing: 8, runSpacing: 4, children: [
                  for (final f in const [1000, 3000, 5000, 10000])
                    _choice('${points(f)}P', _fee == f, () => setState(() => _fee = f)),
                ]),
                const SizedBox(height: 6),
                const Text('인증한 날의 몫은 그대로 돌려받고, 인증하지 못한 날의 몫은 그날 성공한 참가자들에게 보상 포인트로 나눠져요.',
                    style: TextStyle(color: AppColors.muted, fontSize: 12.5, height: 1.4)),
              ],
              _label('최대 인원'),
              Wrap(spacing: 8, runSpacing: 4, children: [
                for (final n in const [10, 20, 50, 100]) _choice('$n명', _max == n, () => setState(() => _max = n)),
              ]),
            ]),
          ),
          Container(
            padding: const EdgeInsets.fromLTRB(20, 10, 20, 12),
            decoration: const BoxDecoration(
              color: AppColors.bg,
              border: Border(top: BorderSide(color: AppColors.border)),
            ),
            child: Column(mainAxisSize: MainAxisSize.min, children: [
              if (_error != null)
                Padding(
                  padding: const EdgeInsets.only(bottom: 8),
                  child: Text(_error!, style: const TextStyle(color: AppColors.danger, fontWeight: FontWeight.w600)),
                ),
              FilledButton(onPressed: _busy ? null : _submit, child: Text(_busy ? '만드는 중…' : '챌린지 만들기')),
            ]),
          ),
        ]),
      ),
    );
  }

  Widget _label(String text) {
    return Padding(
      padding: const EdgeInsets.only(top: 24, bottom: 10),
      child: Text(text, style: const TextStyle(fontSize: 16, fontWeight: FontWeight.w800)),
    );
  }

  Widget _modeCard(String mode, String title, String hint, IconData icon) {
    final selected = _mode == mode;
    return InkWell(
      borderRadius: BorderRadius.circular(18),
      onTap: () => setState(() => _mode = mode),
      child: Container(
        padding: const EdgeInsets.all(14),
        decoration: BoxDecoration(
          color: AppColors.surface,
          borderRadius: BorderRadius.circular(18),
          border: Border.all(color: selected ? AppColors.brown : AppColors.border, width: selected ? 2 : 1),
        ),
        child: Column(crossAxisAlignment: CrossAxisAlignment.start, children: [
          Icon(icon, color: selected ? AppColors.brown : AppColors.muted, size: 26),
          const SizedBox(height: 8),
          Text(title, style: const TextStyle(fontWeight: FontWeight.w800)),
          const SizedBox(height: 2),
          Text(hint, style: const TextStyle(color: AppColors.muted, fontSize: 12.5)),
        ]),
      ),
    );
  }

  Widget _choice(String label, bool selected, VoidCallback onTap) {
    return ChoiceChip(
      label: Text(label),
      selected: selected,
      onSelected: (_) => onTap(),
      showCheckmark: false,
      selectedColor: AppColors.text,
      backgroundColor: AppColors.surface,
      side: const BorderSide(color: AppColors.border),
      labelStyle: TextStyle(color: selected ? Colors.white : AppColors.text, fontWeight: FontWeight.w600),
    );
  }
}
