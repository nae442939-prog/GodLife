import 'package:flutter/material.dart';
import 'package:provider/provider.dart';

import '../api/api_client.dart';
import '../api/models.dart';
import '../app_state.dart';
import '../theme.dart';
import '../widgets/async_view.dart';
import '../widgets/category_icon.dart';
import '../widgets/design.dart';
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
          padding: const EdgeInsets.fromLTRB(16, 0, 16, 16),
          child: Row(children: [
            // '전체'는 종류 · 카테고리를 모두 푼다
            _chip('전체', _mode == null && _categoryId == null, () => setState(() {
                  _mode = null;
                  _categoryId = null;
                })),
            _chip('무료', _mode == 'FREE', () => setState(() => _mode = _mode == 'FREE' ? null : 'FREE')),
            _chip('포인트', _mode == 'BET', () => setState(() => _mode = _mode == 'BET' ? null : 'BET')),
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
                padding: const EdgeInsets.fromLTRB(20, 0, 20, 96),
                itemCount: items.length,
                separatorBuilder: (_, _) => const SizedBox(height: 11),
                itemBuilder: (context, i) => ChallengeCard(items[i], onBack: reload),
              );
            },
          ),
        ),
      ]),
      // 아래 가운데에 뜨는 알약 버튼
      Positioned(
        left: 0,
        right: 0,
        bottom: 18,
        child: Center(child: PillFab(label: '챌린지 만들기', icon: Icons.add, onTap: _create)),
      ),
    ]);
  }

  Widget _chip(String label, bool selected, VoidCallback onTap) {
    return Padding(
      padding: const EdgeInsets.symmetric(horizontal: 4),
      child: PillChip(label, selected: selected, onTap: onTap),
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
    return AppCard(
      onTap: () async {
        await Navigator.of(context).push(MaterialPageRoute(builder: (_) => ChallengeDetailScreen(id: c.id)));
        onBack?.call();
      },
      child: Row(children: [
        _thumbnail(c),
        const SizedBox(width: 13),
        Expanded(
          child: Column(crossAxisAlignment: CrossAxisAlignment.start, children: [
            Text(c.title,
                maxLines: 1,
                overflow: TextOverflow.ellipsis,
                style: const TextStyle(fontSize: 14.5, fontWeight: FontWeight.w800, letterSpacing: -0.1)),
            const SizedBox(height: 4),
            Text('${c.frequencyLabel} · ${c.totalDays}일 · ${c.participantCount}명 참여',
                style: const TextStyle(fontSize: 11.5, color: AppColors.muted, fontWeight: FontWeight.w600)),
          ]),
        ),
        const SizedBox(width: 10),
        PriceBadge(free: c.isFree, fee: c.entryFee),
      ]),
    );
  }

  /// 왼쪽 네모 칸: 대표 사진이 있으면 사진을, 없거나 못 받으면 종류 색 바탕에 아이콘을 보여 준다
  Widget _thumbnail(Challenge c) {
    const size = 58.0;
    final (icon, (background, foreground)) =
        CategoryIcon.styleOf(c.category.name, title: c.title, subType: c.subTypeName);
    final fallback = ColoredBox(color: background, child: Icon(icon, color: foreground, size: 26));
    return ClipRRect(
      borderRadius: BorderRadius.circular(14),
      child: SizedBox(
        width: size,
        height: size,
        child: c.coverUrl == null
            ? fallback
            : Image.network('$serverBase${c.coverUrl}', fit: BoxFit.cover, errorBuilder: (_, _, _) => fallback),
      ),
    );
  }
}
