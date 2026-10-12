import 'package:flutter/material.dart';
import 'package:provider/provider.dart';

import '../api/models.dart';
import '../app_state.dart';
import '../theme.dart';
import '../widgets/async_view.dart';
import '../widgets/category_icon.dart';
import 'challenge_create_screen.dart';
import 'challenge_detail_screen.dart';

/// 모집 중인 챌린지 둘러보기 (종류 · 카테고리로 거르기) + 챌린지 만들기
class ChallengeListScreen extends StatefulWidget {
  const ChallengeListScreen({super.key});

  @override
  State<ChallengeListScreen> createState() => _ChallengeListScreenState();
}

class _ChallengeListScreenState extends State<ChallengeListScreen> {
  String? _mode; // null = 전체, FREE, BET
  int? _categoryId;
  int _version = 0; // 챌린지를 만들고 돌아오면 목록을 새로 불러온다
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

  Future<void> _create() async {
    final created =
        await Navigator.of(context).push<int>(MaterialPageRoute(builder: (_) => const ChallengeCreateScreen()));
    if (created == null || !mounted) return;
    showMessage(context, '챌린지를 만들었어요!');
    await Navigator.of(context).push(MaterialPageRoute(builder: (_) => ChallengeDetailScreen(id: created)));
    if (mounted) setState(() => _version++);
  }

  @override
  Widget build(BuildContext context) {
    return Stack(children: [
      Column(crossAxisAlignment: CrossAxisAlignment.start, children: [
        SingleChildScrollView(
          scrollDirection: Axis.horizontal,
          padding: const EdgeInsets.fromLTRB(16, 4, 16, 10),
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
            key: ValueKey('$_mode/$_categoryId/$_version'),
            load: _load,
            builder: (context, items, reload) {
              if (items.isEmpty) {
                return ListView(children: const [
                  SizedBox(height: 120),
                  Center(child: Text('조건에 맞는 챌린지가 없어요.', style: TextStyle(color: AppColors.muted))),
                ]);
              }
              return ListView.separated(
                padding: const EdgeInsets.fromLTRB(16, 4, 16, 100),
                itemCount: items.length,
                separatorBuilder: (_, _) => const SizedBox(height: 12),
                itemBuilder: (context, i) => ChallengeCard(items[i], onBack: reload),
              );
            },
          ),
        ),
      ]),
      Positioned(
        right: 16,
        bottom: 16,
        child: FloatingActionButton.extended(
          onPressed: _create,
          backgroundColor: AppColors.primary,
          foregroundColor: Colors.white,
          icon: const Icon(Icons.add),
          label: const Text('챌린지 만들기', style: TextStyle(fontFamily: 'NotoSansKR', fontWeight: FontWeight.w700)),
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

/// 챌린지 한 줄: 카테고리 아이콘 · 제목 · '매일 · 14일 · 11명' · 무료/참가 포인트
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
          child: Row(children: [
            CategoryIcon(c.category.name, size: 52),
            const SizedBox(width: 14),
            Expanded(
              child: Column(crossAxisAlignment: CrossAxisAlignment.start, children: [
                Text(c.title,
                    maxLines: 1,
                    overflow: TextOverflow.ellipsis,
                    style: const TextStyle(fontSize: 16.5, fontWeight: FontWeight.w800)),
                const SizedBox(height: 5),
                Text('${c.frequencyLabel} · ${c.totalDays}일 · ${c.participantCount}명 참여',
                    style: const TextStyle(fontSize: 13, color: AppColors.muted, fontWeight: FontWeight.w500)),
              ]),
            ),
            const SizedBox(width: 10),
            c.isFree ? const Tag('무료', color: AppColors.mint) : Tag('${points(c.entryFee)}P', color: AppColors.primary),
          ]),
        ),
      ),
    );
  }
}
