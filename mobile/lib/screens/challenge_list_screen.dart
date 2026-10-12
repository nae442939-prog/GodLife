import 'package:flutter/material.dart';
import 'package:provider/provider.dart';

import '../api/models.dart';
import '../app_state.dart';
import '../theme.dart';
import '../widgets/async_view.dart';
import 'challenge_detail_screen.dart';

/// 모집 중인 챌린지 둘러보기 (종류 · 카테고리로 거르기)
class ChallengeListScreen extends StatefulWidget {
  const ChallengeListScreen({super.key});

  @override
  State<ChallengeListScreen> createState() => _ChallengeListScreenState();
}

class _ChallengeListScreenState extends State<ChallengeListScreen> {
  String? _mode; // null = 전체, FREE, BET
  int? _categoryId;
  List<Category> _categories = [];

  @override
  void initState() {
    super.initState();
    _loadCategories();
  }

  Future<void> _loadCategories() async {
    try {
      final data = await context.read<AppState>().api.get('/api/categories') as List;
      if (mounted) {
        setState(() => _categories = data.map((c) => Category.fromJson(c as Map<String, dynamic>)).toList());
      }
    } catch (_) {
      // 카테고리 칩만 빠진다
    }
  }

  Future<List<Challenge>> _load() async {
    final params = <String>[
      if (_mode != null) 'mode=$_mode',
      if (_categoryId != null) 'categoryId=$_categoryId',
    ];
    final data = await context.read<AppState>().api.get('/api/challenges${params.isEmpty ? '' : '?${params.join('&')}'}');
    return ((data as Map)['items'] as List).map((c) => Challenge.fromJson(c as Map<String, dynamic>)).toList();
  }

  @override
  Widget build(BuildContext context) {
    return Column(crossAxisAlignment: CrossAxisAlignment.start, children: [
      SingleChildScrollView(
        scrollDirection: Axis.horizontal,
        padding: const EdgeInsets.fromLTRB(16, 4, 16, 8),
        child: Row(children: [
          for (final (value, label) in [(null, '전체'), ('FREE', '무료'), ('BET', '포인트')])
            _chip(label, _mode == value, () => setState(() => _mode = value)),
          Container(width: 1, height: 20, color: AppColors.border, margin: const EdgeInsets.symmetric(horizontal: 6)),
          for (final c in _categories)
            _chip(c.name, _categoryId == c.id, () => setState(() => _categoryId = _categoryId == c.id ? null : c.id)),
        ]),
      ),
      Expanded(
        child: AsyncView<List<Challenge>>(
          // 거르는 조건이 바뀌면 새로 불러온다
          key: ValueKey('$_mode/$_categoryId'),
          load: _load,
          builder: (context, items, reload) {
            if (items.isEmpty) {
              return ListView(children: const [
                SizedBox(height: 120),
                Center(child: Text('조건에 맞는 챌린지가 없어요.', style: TextStyle(color: AppColors.muted))),
              ]);
            }
            return ListView.separated(
              padding: const EdgeInsets.fromLTRB(16, 4, 16, 24),
              itemCount: items.length,
              separatorBuilder: (_, _) => const SizedBox(height: 12),
              itemBuilder: (context, i) => ChallengeCard(items[i], onBack: reload),
            );
          },
        ),
      ),
    ]);
  }

  Widget _chip(String label, bool selected, VoidCallback onTap) {
    return Padding(
      padding: const EdgeInsets.symmetric(horizontal: 4),
      child: ChoiceChip(
        label: Text(label),
        selected: selected,
        onSelected: (_) => onTap(),
        showCheckmark: false,
        selectedColor: AppColors.text,
        backgroundColor: AppColors.surface,
        side: const BorderSide(color: AppColors.border),
        labelStyle: TextStyle(
          color: selected ? Colors.white : AppColors.text,
          fontWeight: FontWeight.w600,
        ),
      ),
    );
  }
}

class ChallengeCard extends StatelessWidget {
  const ChallengeCard(this.challenge, {super.key, this.onBack});

  final Challenge challenge;
  final Future<void> Function()? onBack;

  @override
  Widget build(BuildContext context) {
    final c = challenge;
    return Card(
      clipBehavior: Clip.antiAlias,
      child: InkWell(
        onTap: () async {
          await Navigator.of(context).push(MaterialPageRoute(builder: (_) => ChallengeDetailScreen(id: c.id)));
          onBack?.call();
        },
        child: Padding(
          padding: const EdgeInsets.all(16),
          child: Column(crossAxisAlignment: CrossAxisAlignment.start, children: [
            Row(children: [
              Tag(c.category.name),
              const SizedBox(width: 6),
              c.isFree ? const Tag('무료', color: AppColors.mint) : Tag('${points(c.entryFee)}P', color: AppColors.primary),
              const Spacer(),
              Text('${c.participantCount}명 참여', style: const TextStyle(color: AppColors.muted, fontSize: 13)),
            ]),
            const SizedBox(height: 10),
            Text(c.title, style: const TextStyle(fontSize: 17, fontWeight: FontWeight.w800)),
            if (c.description.isNotEmpty) ...[
              const SizedBox(height: 4),
              Text(c.description,
                  maxLines: 2, overflow: TextOverflow.ellipsis, style: const TextStyle(color: AppColors.muted)),
            ],
            const SizedBox(height: 10),
            Text('${c.startDate} ~ ${c.endDate} · ${c.totalDays}일 · ${c.frequencyLabel}',
                style: const TextStyle(fontSize: 13, color: AppColors.brown, fontWeight: FontWeight.w600)),
          ]),
        ),
      ),
    );
  }
}
