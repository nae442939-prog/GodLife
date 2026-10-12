import 'package:flutter/material.dart';
import 'package:provider/provider.dart';

import '../api/models.dart';
import '../app_state.dart';
import '../theme.dart';
import '../widgets/async_view.dart';
import '../widgets/category_icon.dart';
import '../widgets/design.dart';
import 'challenge_detail_screen.dart';
import 'ranking_screen.dart';
import 'verify_screen.dart';

/// 홈 (시안): 소개 띠 → 오늘 인증할 챌린지 → 모집 중인 챌린지
class HomeScreen extends StatelessWidget {
  const HomeScreen({super.key, required this.onBrowse, required this.onCommunity});

  /// '전체 보기' · '챌린지 둘러보기'를 누르면 아래 탭을 챌린지로 옮긴다
  final VoidCallback onBrowse;
  final VoidCallback onCommunity;

  @override
  Widget build(BuildContext context) {
    final state = context.read<AppState>();
    return AsyncView<_HomeData>(
      load: () async {
        final results = await Future.wait([
          state.api.get('/api/me/challenges'),
          state.api.get('/api/challenges?sort=popular'),
        ]);
        final mine = (results[0] as List).map((c) => MyChallenge.fromJson(c as Map<String, dynamic>)).toList();
        int order(MyChallenge c) => c.canVerify ? 0 : (c.joined ? 1 : 2);
        mine.sort((a, b) => order(a) - order(b));
        return _HomeData(
          mine: mine.where((c) => c.joined).toList(),
          recruiting: (((results[1] as Map)['items']) as List)
              .map((c) => Challenge.fromJson(c as Map<String, dynamic>))
              .take(6)
              .toList(),
        );
      },
      builder: (context, data, reload) {
        final todo = data.mine.where((c) => c.canVerify).length;
        final nickname = state.user?.nickname ?? '';
        final headline = data.mine.isEmpty
            ? '$nickname님, 첫 챌린지에 도전해 볼까요?'
            : todo > 0
                ? '$nickname님, 오늘 인증할 챌린지가 $todo개 있어요'
                : '$nickname님, 오늘 인증은 다 했어요!';
        return LayoutBuilder(builder: (context, box) {
          // 모집 카드 두 장이 한 화면에 나란히 보이는 폭
          final cardWidth = (box.maxWidth - 40 - 12) / 2;
          return ListView(padding: const EdgeInsets.fromLTRB(0, 6, 0, 28), children: [
            Padding(padding: const EdgeInsets.symmetric(horizontal: 20), child: _Hero(onBrowse: onBrowse)),
            const SizedBox(height: 22),
            Padding(
              padding: const EdgeInsets.symmetric(horizontal: 20),
              child: Text(headline,
                  style: const TextStyle(fontSize: 17, fontWeight: FontWeight.w800, letterSpacing: -0.2)),
            ),
            const SizedBox(height: 12),
            if (data.mine.isEmpty)
              Padding(
                padding: const EdgeInsets.symmetric(horizontal: 20),
                child: AppCard(
                  bordered: false,
                  radius: 18,
                  padding: const EdgeInsets.all(18),
                  child: Column(crossAxisAlignment: CrossAxisAlignment.start, children: [
                    const Text('참여 중인 챌린지가 없어요', style: TextStyle(fontWeight: FontWeight.w800, fontSize: 14.5)),
                    const SizedBox(height: 4),
                    const Text('습관 하나를 골라 오늘부터 인증해 보세요.',
                        style: TextStyle(color: AppColors.muted, fontSize: 12.5, fontWeight: FontWeight.w600)),
                  ]),
                ),
              ),
            for (final c in data.mine)
              Padding(
                padding: const EdgeInsets.fromLTRB(20, 0, 20, 10),
                child: _TodayItem(c, reload: reload),
              ),
            // 랭킹으로 가는 띠
            Padding(
              padding: const EdgeInsets.fromLTRB(20, 2, 20, 0),
              child: AppCard(
                bordered: false,
                radius: 18,
                onTap: () =>
                    Navigator.of(context).push(MaterialPageRoute(builder: (_) => const RankingScreen())),
                child: Row(children: [
                  Container(
                    width: 42,
                    height: 42,
                    decoration: BoxDecoration(color: ColorTag.gold.$1, shape: BoxShape.circle),
                    child: Icon(Icons.emoji_events, color: ColorTag.gold.$2, size: 22),
                  ),
                  const SizedBox(width: 12),
                  const Expanded(
                    child: Column(crossAxisAlignment: CrossAxisAlignment.start, children: [
                      Text('랭킹', style: TextStyle(fontSize: 13.5, fontWeight: FontWeight.w800, letterSpacing: -0.1)),
                      SizedBox(height: 3),
                      Text('이번 달 인증왕과 내 순위 보기',
                          style: TextStyle(fontSize: 11, fontWeight: FontWeight.w700, color: AppColors.muted)),
                    ]),
                  ),
                  const Icon(Icons.chevron_right, color: Color(0xFFC7C0B0), size: 20),
                ]),
              ),
            ),
            const SizedBox(height: 22),
            Padding(
              padding: const EdgeInsets.symmetric(horizontal: 20),
              child: Row(children: [
                const Expanded(
                  child: Text('모집 중인 챌린지',
                      style: TextStyle(fontSize: 17, fontWeight: FontWeight.w800, letterSpacing: -0.2)),
                ),
                GestureDetector(
                  onTap: onBrowse,
                  child: const Padding(
                    padding: EdgeInsets.symmetric(vertical: 6),
                    child: Text('전체 보기',
                        style: TextStyle(fontSize: 12, fontWeight: FontWeight.w700, color: AppColors.muted)),
                  ),
                ),
              ]),
            ),
            const SizedBox(height: 12),
            SizedBox(
              height: 150,
              child: ListView.separated(
                scrollDirection: Axis.horizontal,
                padding: const EdgeInsets.fromLTRB(20, 0, 20, 16),
                itemCount: data.recruiting.length,
                separatorBuilder: (_, _) => const SizedBox(width: 12),
                itemBuilder: (context, i) =>
                    SizedBox(width: cardWidth, child: _RecoCard(data.recruiting[i], reload: reload)),
              ),
            ),
          ]);
        });
      },
    );
  }
}

class _HomeData {
  _HomeData({required this.mine, required this.recruiting});

  final List<MyChallenge> mine;
  final List<Challenge> recruiting;
}

/// 맨 위 소개 띠 (복숭아색 → 민트색으로 번지는 바탕)
class _Hero extends StatelessWidget {
  const _Hero({required this.onBrowse});

  final VoidCallback onBrowse;

  @override
  Widget build(BuildContext context) {
    return Container(
      padding: const EdgeInsets.fromLTRB(22, 22, 22, 24),
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
          child: const Text('습관 챌린지 서비스',
              style: TextStyle(color: Color(0xFFD9693F), fontSize: 11.5, fontWeight: FontWeight.w800)),
        ),
        const SizedBox(height: 13),
        const Text('오늘의 습관에 도전하고,\n성공하면 돌려받으세요',
            style: TextStyle(fontSize: 20, fontWeight: FontWeight.w800, height: 1.35, letterSpacing: -0.4)),
        const SizedBox(height: 8),
        const Text('함께 도전하고 사진으로 인증하는 갓생 챌린지',
            style: TextStyle(color: Color(0xFF6B6558), fontSize: 12.5, fontWeight: FontWeight.w600)),
        const SizedBox(height: 17),
        Container(
          decoration: BoxDecoration(
            borderRadius: BorderRadius.circular(11),
            boxShadow: const [
              BoxShadow(color: Color(0x80EE6A4D), blurRadius: 22, offset: Offset(0, 14), spreadRadius: -12),
            ],
          ),
          child: Material(
            color: AppColors.primary,
            borderRadius: BorderRadius.circular(11),
            clipBehavior: Clip.antiAlias,
            child: InkWell(
              onTap: onBrowse,
              child: const Padding(
                padding: EdgeInsets.symmetric(horizontal: 20, vertical: 11),
                child: Text('챌린지 둘러보기',
                    style: TextStyle(color: Colors.white, fontSize: 13, fontWeight: FontWeight.w800)),
              ),
            ),
          ),
        ),
      ]),
    );
  }
}

/// 오늘 할 일 한 장: 아이콘 · 제목 · 진행 막대(아이콘 색) · 완료 체크 / 시계 / [인증]
class _TodayItem extends StatelessWidget {
  const _TodayItem(this.c, {required this.reload});

  final MyChallenge c;
  final Future<void> Function() reload;

  @override
  Widget build(BuildContext context) {
    final done = c.verifyState == 'DONE_TODAY' || c.verifyState == 'WEEK_DONE';
    final (_, (_, color)) = CategoryIcon.styleOf(c.category.name, title: c.title);
    return AppCard(
      bordered: false,
      radius: 18,
      onTap: () async {
        await Navigator.of(context).push(MaterialPageRoute(builder: (_) => ChallengeDetailScreen(id: c.id)));
        reload();
      },
      child: Row(children: [
        CategoryIcon(c.category.name, title: c.title, size: 42),
        const SizedBox(width: 12),
        Expanded(
          child: Column(crossAxisAlignment: CrossAxisAlignment.start, children: [
            Text(c.title,
                maxLines: 1,
                overflow: TextOverflow.ellipsis,
                style: const TextStyle(fontSize: 13.5, fontWeight: FontWeight.w800, letterSpacing: -0.1)),
            const SizedBox(height: 7),
            Row(children: [
              Expanded(
                child: ClipRRect(
                  borderRadius: BorderRadius.circular(999),
                  child: LinearProgressIndicator(
                    value: c.progress,
                    minHeight: 6,
                    backgroundColor: AppColors.surfaceAlt,
                    color: color,
                  ),
                ),
              ),
              const SizedBox(width: 8),
              Text('${c.successDays}/${c.targetCount}',
                  style: const TextStyle(fontSize: 10.5, fontWeight: FontWeight.w700, color: AppColors.muted)),
            ]),
          ]),
        ),
        const SizedBox(width: 12),
        if (c.canVerify)
          // 오늘 아직 인증 전이면 바로 인증하러 간다
          Material(
            color: AppColors.primary,
            borderRadius: BorderRadius.circular(999),
            clipBehavior: Clip.antiAlias,
            child: InkWell(
              onTap: () async {
                await Navigator.of(context)
                    .push(MaterialPageRoute(builder: (_) => VerifyScreen(challengeId: c.id, title: c.title)));
                reload();
              },
              child: const Padding(
                padding: EdgeInsets.symmetric(horizontal: 14, vertical: 7),
                child: Text('인증', style: TextStyle(color: Colors.white, fontSize: 12, fontWeight: FontWeight.w800)),
              ),
            ),
          )
        else if (done)
          const Icon(Icons.check_circle, color: AppColors.mint, size: 24)
        else
          const Icon(Icons.schedule, color: Color(0xFFC7C0B0), size: 24),
      ]),
    );
  }
}

/// 모집 중 챌린지 카드: 위쪽 색 띠에 아이콘, 아래에 제목과 '무료 · 7명 참여'
class _RecoCard extends StatelessWidget {
  const _RecoCard(this.c, {required this.reload});

  final Challenge c;
  final Future<void> Function() reload;

  @override
  Widget build(BuildContext context) {
    final (icon, (background, foreground)) =
        CategoryIcon.styleOf(c.category.name, title: c.title, subType: c.subTypeName);
    return AppCard(
      bordered: false,
      radius: 18,
      padding: EdgeInsets.zero,
      onTap: () async {
        await Navigator.of(context).push(MaterialPageRoute(builder: (_) => ChallengeDetailScreen(id: c.id)));
        reload();
      },
      child: Column(crossAxisAlignment: CrossAxisAlignment.stretch, children: [
        Container(height: 58, color: background, child: Icon(icon, color: foreground, size: 24)),
        Padding(
          padding: const EdgeInsets.fromLTRB(12, 11, 12, 13),
          child: Column(crossAxisAlignment: CrossAxisAlignment.start, children: [
            Text(c.title,
                maxLines: 1,
                overflow: TextOverflow.ellipsis,
                style: const TextStyle(fontSize: 12.5, fontWeight: FontWeight.w800, height: 1.3)),
            const SizedBox(height: 5),
            Text('${c.isFree ? '무료' : '${points(c.entryFee)}P'} · ${c.participantCount}명 참여',
                style: const TextStyle(fontSize: 10.5, fontWeight: FontWeight.w700, color: AppColors.muted)),
          ]),
        ),
      ]),
    );
  }
}
