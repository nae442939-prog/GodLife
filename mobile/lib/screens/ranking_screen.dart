import 'package:flutter/material.dart';
import 'package:provider/provider.dart';

import '../api/api_client.dart';
import '../app_state.dart';
import '../theme.dart';
import '../widgets/async_view.dart';
import '../widgets/design.dart';

/// 랭킹: 1 · 2 · 3등 시상대 + 그 아래 순위 + 내 순위.
/// 이번 달(인증 횟수) · 전체(누적 성공) · 최장 연속 · 주간 시즌(상위 3명 보너스) 네 가지를 칩으로 고른다.
class RankingScreen extends StatefulWidget {
  const RankingScreen({super.key});

  @override
  State<RankingScreen> createState() => _RankingScreenState();
}

/// (칩 이름, 설명, 값 뒤에 붙는 단위)
const _kinds = {
  'month_verify': ('이번 달', '이번 달에 인증한 횟수', '회'),
  'total_success': ('전체', '지금까지 인증에 성공한 횟수', '회'),
  'max_streak': ('최장 연속', '가장 길게 이어 간 연속 인증', '일'),
  'season': ('주간 시즌', '이번 주 인증 횟수 · 상위 3명에게 보너스', '회'),
};

// 시상대 색: 1등 골드 · 2등 라벤더 · 3등 코랄
const _medal = [ColorTag.gold, ColorTag.lav, ColorTag.coral];

class _RankingScreenState extends State<RankingScreen> {
  String _kind = 'month_verify';

  Future<_RankData> _load() async {
    final api = context.read<AppState>().api;
    if (_kind == 'season') {
      final s = await api.get('/api/seasons/current?type=weekly') as Map<String, dynamic>;
      _Entry entry(Map<String, dynamic> e) => _Entry(
            rank: (e['rank'] as num).toInt(),
            nickname: e['nickname'] as String,
            image: e['profileImageUrl'] as String?,
            value: (e['score'] as num).toInt(),
            mine: e['mine'] == true,
            bonus: (e['bonusPoints'] as num?)?.toInt() ?? 0,
          );
      return _RankData(
        top: ((s['top'] as List?) ?? []).map((e) => entry(e as Map<String, dynamic>)).toList(),
        me: s['me'] == null ? null : entry(s['me'] as Map<String, dynamic>),
        note: '${s['start']} ~ ${s['end']} · ${s['daysLeft']}일 남음',
      );
    }
    final r = await api.get('/api/rankings/users?metric=$_kind') as Map<String, dynamic>;
    _Entry entry(Map<String, dynamic> e) => _Entry(
          rank: (e['rank'] as num).toInt(),
          nickname: e['nickname'] as String,
          image: e['profileImageUrl'] as String?,
          value: (e['value'] as num).round(),
          mine: e['mine'] == true,
        );
    return _RankData(
      top: ((r['top'] as List?) ?? []).map((e) => entry(e as Map<String, dynamic>)).toList(),
      me: r['me'] == null ? null : entry(r['me'] as Map<String, dynamic>),
    );
  }

  @override
  Widget build(BuildContext context) {
    final (_, hint, unit) = _kinds[_kind]!;
    return Scaffold(
      appBar: AppBar(title: const Text('랭킹')),
      body: SafeArea(
        child: Column(crossAxisAlignment: CrossAxisAlignment.start, children: [
          SingleChildScrollView(
            scrollDirection: Axis.horizontal,
            padding: const EdgeInsets.fromLTRB(16, 0, 16, 12),
            child: Row(children: [
              for (final e in _kinds.entries)
                Padding(
                  padding: const EdgeInsets.symmetric(horizontal: 4),
                  child: PillChip(e.value.$1, selected: _kind == e.key, onTap: () => setState(() => _kind = e.key)),
                ),
            ]),
          ),
          Expanded(
            child: AsyncView<_RankData>(
              key: ValueKey(_kind),
              load: _load,
              builder: (context, data, reload) {
                if (data.top.isEmpty) {
                  return ListView(children: const [
                    SizedBox(height: 120),
                    Center(
                      child: Text('아직 순위가 없어요. 첫 인증의 주인공이 되어 보세요!',
                          style: TextStyle(color: AppColors.muted, fontSize: 12.5, fontWeight: FontWeight.w600)),
                    ),
                  ]);
                }
                final podium = data.top.take(3).toList();
                final rest = data.top.skip(3).toList();
                return ListView(padding: const EdgeInsets.fromLTRB(20, 0, 20, 28), children: [
                  Padding(
                    padding: const EdgeInsets.symmetric(horizontal: 4),
                    child: Text(data.note == null ? hint : '$hint\n${data.note}',
                        style: const TextStyle(
                            fontSize: 11.5, fontWeight: FontWeight.w600, color: AppColors.muted, height: 1.6)),
                  ),
                  const SizedBox(height: 14),
                  // 시상대: 2등 · 1등 · 3등 순서로 세우고 1등이 가장 높다
                  Container(
                    padding: const EdgeInsets.fromLTRB(12, 22, 12, 0),
                    decoration: BoxDecoration(
                      borderRadius: BorderRadius.circular(22),
                      gradient: const LinearGradient(
                        begin: Alignment.topLeft,
                        end: Alignment.bottomRight,
                        colors: [Color(0xFFFDE7D8), Color(0xFFD9F1EA)],
                      ),
                    ),
                    child: Row(crossAxisAlignment: CrossAxisAlignment.end, children: [
                      Expanded(child: podium.length > 1 ? _Step(podium[1], 1, 64, unit) : const SizedBox()),
                      Expanded(child: _Step(podium[0], 0, 92, unit)),
                      Expanded(child: podium.length > 2 ? _Step(podium[2], 2, 46, unit) : const SizedBox()),
                    ]),
                  ),
                  if (data.me != null) ...[
                    const SizedBox(height: 14),
                    // 내 순위
                    Container(
                      padding: const EdgeInsets.fromLTRB(16, 14, 16, 14),
                      decoration: BoxDecoration(color: AppColors.text, borderRadius: BorderRadius.circular(18)),
                      child: Row(children: [
                        const Text('내 순위',
                            style: TextStyle(color: Color(0xFFB3ADA0), fontSize: 12, fontWeight: FontWeight.w700)),
                        const SizedBox(width: 10),
                        Text('${data.me!.rank}위',
                            style: const TextStyle(color: Colors.white, fontSize: 16, fontWeight: FontWeight.w800)),
                        const Spacer(),
                        Text('${data.me!.value}$unit',
                            style: const TextStyle(color: Colors.white, fontSize: 14, fontWeight: FontWeight.w800)),
                      ]),
                    ),
                  ],
                  const SizedBox(height: 14),
                  if (rest.isNotEmpty)
                    AppCard(
                      bordered: false,
                      padding: EdgeInsets.zero,
                      child: Column(children: [
                        for (final (i, e) in rest.indexed)
                          Container(
                            padding: const EdgeInsets.symmetric(horizontal: 16, vertical: 12),
                            decoration: BoxDecoration(
                              color: e.mine ? const Color(0xFFE3F5F1) : null,
                              border: i == rest.length - 1
                                  ? null
                                  : const Border(bottom: BorderSide(color: Color(0xFFF5F2EA))),
                            ),
                            child: Row(children: [
                              SizedBox(
                                width: 26,
                                child: Text('${e.rank}',
                                    style: const TextStyle(
                                        fontSize: 13.5, fontWeight: FontWeight.w800, color: AppColors.chipText)),
                              ),
                              _Avatar(e, 34, ColorTag.mint),
                              const SizedBox(width: 12),
                              Expanded(
                                child: Text(e.mine ? '${e.nickname} (나)' : e.nickname,
                                    overflow: TextOverflow.ellipsis,
                                    style: const TextStyle(fontSize: 13.5, fontWeight: FontWeight.w700)),
                              ),
                              Text('${e.value}$unit',
                                  style: const TextStyle(fontSize: 13, fontWeight: FontWeight.w800)),
                            ]),
                          ),
                      ]),
                    ),
                ]);
              },
            ),
          ),
        ]),
      ),
    );
  }
}

class _Entry {
  _Entry({
    required this.rank,
    required this.nickname,
    required this.image,
    required this.value,
    required this.mine,
    this.bonus = 0,
  });

  final int rank;
  final String nickname;
  final String? image;
  final int value;
  final bool mine;

  /// 시즌 랭킹의 (예상) 보너스 포인트
  final int bonus;
}

class _RankData {
  _RankData({required this.top, required this.me, this.note});

  final List<_Entry> top;
  final _Entry? me;
  final String? note;
}

/// 프로필 동그라미: 사진이 있으면 사진, 없으면 닉네임 첫 글자
class _Avatar extends StatelessWidget {
  const _Avatar(this.e, this.size, this.colors);

  final _Entry e;
  final double size;
  final (Color, Color) colors;

  @override
  Widget build(BuildContext context) {
    return Container(
      width: size,
      height: size,
      alignment: Alignment.center,
      clipBehavior: Clip.antiAlias,
      decoration: BoxDecoration(color: colors.$1, shape: BoxShape.circle),
      child: e.image != null
          ? Image.network('$serverBase${e.image}', width: size, height: size, fit: BoxFit.cover,
              errorBuilder: (_, _, _) => _initial())
          : _initial(),
    );
  }

  Widget _initial() => Text(e.nickname.isEmpty ? '' : e.nickname.substring(0, 1),
      style: TextStyle(fontSize: size * 0.38, fontWeight: FontWeight.w800, color: colors.$2));
}

/// 시상대 한 칸: 프로필 · 닉네임 · 기록 · 단(순위 숫자)
class _Step extends StatelessWidget {
  const _Step(this.e, this.place, this.height, this.unit);

  final _Entry e;
  final int place; // 0 = 가운데(1등 자리), 1 = 왼쪽, 2 = 오른쪽
  final double height;
  final String unit;

  @override
  Widget build(BuildContext context) {
    final colors = _medal[place];
    final first = place == 0;
    return Column(mainAxisSize: MainAxisSize.min, children: [
      if (first) Icon(Icons.emoji_events, color: colors.$2, size: 24),
      const SizedBox(height: 4),
      Container(
        padding: const EdgeInsets.all(3),
        decoration: const BoxDecoration(color: Colors.white, shape: BoxShape.circle),
        child: _Avatar(e, first ? 58 : 46, colors),
      ),
      const SizedBox(height: 7),
      Text(e.mine ? '${e.nickname} (나)' : e.nickname,
          maxLines: 1,
          overflow: TextOverflow.ellipsis,
          style: const TextStyle(fontSize: 12.5, fontWeight: FontWeight.w800)),
      const SizedBox(height: 2),
      Text('${e.value}$unit',
          style: const TextStyle(fontSize: 11, fontWeight: FontWeight.w700, color: Color(0xFF6B6558))),
      if (e.bonus > 0)
        Padding(
          padding: const EdgeInsets.only(top: 4),
          child: ColorTag.of('+${points(e.bonus)}P', colors),
        ),
      const SizedBox(height: 8),
      Container(
        height: height,
        margin: const EdgeInsets.symmetric(horizontal: 5),
        alignment: Alignment.topCenter,
        padding: const EdgeInsets.only(top: 10),
        decoration: const BoxDecoration(
          color: Color(0xD9FFFFFF),
          borderRadius: BorderRadius.vertical(top: Radius.circular(14)),
        ),
        // 같은 기록이면 같은 순위가 온다 (공동 2위 등)
        child: Text('${e.rank}', style: TextStyle(fontSize: 22, fontWeight: FontWeight.w800, color: colors.$2)),
      ),
    ]);
  }
}

/// 홈에 넣는 이번 달 랭킹 미리보기: 시상대(1 · 2 · 3등)와 내 순위. 누르면 랭킹 화면으로 간다
class RankingPreview extends StatefulWidget {
  const RankingPreview({super.key});

  @override
  State<RankingPreview> createState() => _RankingPreviewState();
}

class _RankingPreviewState extends State<RankingPreview> {
  late final Future<_RankData> _data = _load();

  Future<_RankData> _load() async {
    final r = await context.read<AppState>().api.get('/api/rankings/users?metric=month_verify') as Map<String, dynamic>;
    _Entry entry(Map<String, dynamic> e) => _Entry(
          rank: (e['rank'] as num).toInt(),
          nickname: e['nickname'] as String,
          image: e['profileImageUrl'] as String?,
          value: (e['value'] as num).round(),
          mine: e['mine'] == true,
        );
    return _RankData(
      top: ((r['top'] as List?) ?? []).map((e) => entry(e as Map<String, dynamic>)).toList(),
      me: r['me'] == null ? null : entry(r['me'] as Map<String, dynamic>),
    );
  }

  @override
  Widget build(BuildContext context) {
    return FutureBuilder<_RankData>(
      future: _data,
      builder: (context, snap) {
        final data = snap.data;
        // 불러오는 중이거나 실패하면 자리를 비워 둔다 (홈의 다른 내용은 그대로 보인다)
        if (data == null || data.top.isEmpty) return const SizedBox.shrink();
        final podium = data.top.take(3).toList();
        return GestureDetector(
          onTap: () => Navigator.of(context).push(MaterialPageRoute(builder: (_) => const RankingScreen())),
          child: Column(children: [
            Container(
              padding: const EdgeInsets.fromLTRB(12, 20, 12, 0),
              decoration: BoxDecoration(
                borderRadius: BorderRadius.circular(22),
                gradient: const LinearGradient(
                  begin: Alignment.topLeft,
                  end: Alignment.bottomRight,
                  colors: [Color(0xFFFDE7D8), Color(0xFFD9F1EA)],
                ),
              ),
              child: Row(crossAxisAlignment: CrossAxisAlignment.end, children: [
                Expanded(child: podium.length > 1 ? _Step(podium[1], 1, 52, '회') : const SizedBox()),
                Expanded(child: _Step(podium[0], 0, 76, '회')),
                Expanded(child: podium.length > 2 ? _Step(podium[2], 2, 38, '회') : const SizedBox()),
              ]),
            ),
            if (data.me != null) ...[
              const SizedBox(height: 10),
              Container(
                padding: const EdgeInsets.fromLTRB(16, 13, 12, 13),
                decoration: BoxDecoration(color: AppColors.text, borderRadius: BorderRadius.circular(18)),
                child: Row(children: [
                  const Text('내 순위',
                      style: TextStyle(color: Color(0xFFB3ADA0), fontSize: 12, fontWeight: FontWeight.w700)),
                  const SizedBox(width: 10),
                  Text('${data.me!.rank}위',
                      style: const TextStyle(color: Colors.white, fontSize: 15, fontWeight: FontWeight.w800)),
                  const Spacer(),
                  Text('이번 달 ${data.me!.value}회',
                      style: const TextStyle(color: Colors.white, fontSize: 12.5, fontWeight: FontWeight.w700)),
                  const Icon(Icons.chevron_right, color: Color(0xFFB3ADA0), size: 18),
                ]),
              ),
            ],
          ]),
        );
      },
    );
  }
}
