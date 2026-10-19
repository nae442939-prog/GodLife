import 'package:flutter/material.dart';
import 'package:provider/provider.dart';

import '../api/api_client.dart';
import '../api/models.dart';
import '../app_state.dart';
import '../theme.dart';
import '../widgets/async_view.dart';
import '../widgets/category_icon.dart';
import '../widgets/design.dart';
import 'challenge_detail_screen.dart';
import 'ranking_screen.dart';
import 'verify_screen.dart';

/// 홈 (시안): 소개 띠 → 포인트 상점 바로가기 → 오늘 인증할 챌린지 → 모집 중인 챌린지
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
          // 상점 바로가기에 보여 줄 보상 포인트 (못 받아도 홈은 그대로 뜬다)
          state.api.get('/api/wallet').catchError((_) => null),
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
          rewardBalance: results[2] == null
              ? null
              : Wallet.fromJson(results[2] as Map<String, dynamic>).rewardBalance,
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
            // 포인트 상점이 있는 줄 모르는 사람이 많아 소개 띠 바로 아래에 크게 둔다
            const SizedBox(height: 12),
            Padding(
              padding: const EdgeInsets.symmetric(horizontal: 20),
              child: _ShopBanner(rewardBalance: data.rewardBalance),
            ),
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
            const SizedBox(height: 12),
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
              height: 186,
              child: ListView.separated(
                scrollDirection: Axis.horizontal,
                padding: const EdgeInsets.fromLTRB(20, 0, 20, 16),
                itemCount: data.recruiting.length,
                separatorBuilder: (_, _) => const SizedBox(width: 12),
                itemBuilder: (context, i) =>
                    SizedBox(width: cardWidth, child: _RecoCard(data.recruiting[i], reload: reload)),
              ),
            ),
            // 처음 온 사람을 위한 사용법 (웹 홈의 '이렇게 진행돼요')
            const SizedBox(height: 10),
            const Padding(
              padding: EdgeInsets.symmetric(horizontal: 20),
              child: Text('이렇게 진행돼요', style: TextStyle(fontSize: 17, fontWeight: FontWeight.w800, letterSpacing: -0.2)),
            ),
            const SizedBox(height: 12),
            const Padding(padding: EdgeInsets.symmetric(horizontal: 20), child: _Steps()),
            const SizedBox(height: 22),
            // 이번 달 랭킹: 홈을 내리면 시상대가 바로 보인다 (전체 보기 → 랭킹 화면)
            const SizedBox(height: 10),
            Padding(
              padding: const EdgeInsets.symmetric(horizontal: 20),
              child: Row(children: [
                const Expanded(
                  child: Text('이번 달 랭킹',
                      style: TextStyle(fontSize: 17, fontWeight: FontWeight.w800, letterSpacing: -0.2)),
                ),
                GestureDetector(
                  onTap: () =>
                      Navigator.of(context).push(MaterialPageRoute(builder: (_) => const RankingScreen())),
                  child: const Padding(
                    padding: EdgeInsets.symmetric(vertical: 6),
                    child: Text('전체 보기',
                        style: TextStyle(fontSize: 12, fontWeight: FontWeight.w700, color: AppColors.muted)),
                  ),
                ),
              ]),
            ),
            const SizedBox(height: 12),
            const Padding(padding: EdgeInsets.symmetric(horizontal: 20), child: RankingPreview()),
          ]);
        });
      },
    );
  }
}

class _HomeData {
  _HomeData({required this.mine, required this.recruiting, required this.rewardBalance});

  final List<MyChallenge> mine;
  final List<Challenge> recruiting;
  final int? rewardBalance;
}

/// 포인트 상점 바로가기: 민트 띠에 내 보상 포인트와 [상점 가기] 버튼 (상점 화면은 웹으로 연다)
class _ShopBanner extends StatelessWidget {
  const _ShopBanner({required this.rewardBalance});

  final int? rewardBalance;

  @override
  Widget build(BuildContext context) {
    final has = rewardBalance != null && rewardBalance! > 0;
    return Material(
      color: Colors.transparent,
      borderRadius: BorderRadius.circular(20),
      clipBehavior: Clip.antiAlias,
      child: Ink(
        decoration: const BoxDecoration(
          gradient: LinearGradient(
            begin: Alignment.topLeft,
            end: Alignment.bottomRight,
            colors: [Color(0xFFCDEEE5), Color(0xFFE8F6F1)],
          ),
        ),
        child: InkWell(
          onTap: () => openWeb('/shop'),
          child: Padding(
            padding: const EdgeInsets.fromLTRB(16, 16, 16, 16),
            child: Row(children: [
              Container(
                width: 52,
                height: 52,
                decoration: const BoxDecoration(color: Colors.white, shape: BoxShape.circle),
                child: const Icon(Icons.shopping_bag, size: 26, color: AppColors.mint),
              ),
              const SizedBox(width: 14),
              Expanded(
                child: Column(crossAxisAlignment: CrossAxisAlignment.start, children: [
                  const Text('포인트 상점',
                      style: TextStyle(fontSize: 12, fontWeight: FontWeight.w800, color: AppColors.mint)),
                  const SizedBox(height: 3),
                  Text(has ? '내 보상 포인트 ${points(rewardBalance!)}P' : '보상 포인트로 상품을 받아요',
                      style: const TextStyle(fontSize: 16, fontWeight: FontWeight.w800, letterSpacing: -0.3)),
                  const SizedBox(height: 2),
                  Text(has ? '상점에서 바로 써 보세요' : '챌린지에 성공하면 보상 포인트가 쌓여요',
                      style: const TextStyle(fontSize: 11.5, color: Color(0xFF5F7A72), fontWeight: FontWeight.w600)),
                ]),
              ),
              const SizedBox(width: 8),
              Container(
                padding: const EdgeInsets.symmetric(horizontal: 13, vertical: 9),
                decoration: BoxDecoration(color: AppColors.text, borderRadius: BorderRadius.circular(999)),
                child: const Text('상점 가기',
                    style: TextStyle(color: Colors.white, fontSize: 12, fontWeight: FontWeight.w800)),
              ),
            ]),
          ),
        ),
      ),
    );
  }
}

/// (제목, 설명, 색, 아이콘) — 웹 홈의 STEPS 와 같은 흐름
const _steps = [
  ('챌린지에 참여', '운동 · 공부 · 독서처럼 하고 싶은 습관을 골라 참여해요. 포인트를 걸지 않는 무료 챌린지도 있어요.', ColorTag.coral,
      Icons.flag),
  ('시작일부터 매일 사진 인증', '챌린지가 시작하는 날부터 카메라로 찍어 올려요. 직접 학습한 AI 가 챌린지와 맞는 사진인지 확인해요.',
      ColorTag.gold, Icons.photo_camera),
  ('인증한 만큼 돌려받아요', '건 포인트는 인증한 만큼 그대로 돌려받아요. 무료 챌린지는 기록과 뱃지가 쌓여요.', ColorTag.mint, Icons.savings),
  ('보상은 포인트 상점에서', '인증하지 못한 몫은 성공한 참가자들에게 보상 포인트로 나눠져요. 보상 포인트는 포인트 상점에서 써요.', ColorTag.lav,
      Icons.shopping_bag),
];

/// 사용법 4단계: 번호가 붙은 색 동그라미 + 제목 + 설명
class _Steps extends StatelessWidget {
  const _Steps();

  @override
  Widget build(BuildContext context) {
    return AppCard(
      bordered: false,
      radius: 18,
      padding: const EdgeInsets.fromLTRB(16, 6, 16, 6),
      child: Column(children: [
        for (final (i, (title, body, colors, icon)) in _steps.indexed)
          Container(
            padding: const EdgeInsets.symmetric(vertical: 12),
            decoration: BoxDecoration(
              border: i == _steps.length - 1 ? null : const Border(bottom: BorderSide(color: Color(0xFFF5F2EA))),
            ),
            child: Row(crossAxisAlignment: CrossAxisAlignment.start, children: [
              Container(
                width: 38,
                height: 38,
                decoration: BoxDecoration(color: colors.$1, shape: BoxShape.circle),
                child: Icon(icon, size: 18, color: colors.$2),
              ),
              const SizedBox(width: 12),
              Expanded(
                child: Column(crossAxisAlignment: CrossAxisAlignment.start, children: [
                  Text('${i + 1}. $title', style: const TextStyle(fontSize: 13.5, fontWeight: FontWeight.w800)),
                  const SizedBox(height: 3),
                  Text(body,
                      style: const TextStyle(
                          color: AppColors.muted, fontSize: 12, fontWeight: FontWeight.w600, height: 1.5)),
                ]),
              ),
            ]),
          ),
      ]),
    );
  }
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
        // 대표 사진이 있으면 사진을, 없거나 못 받으면 종류 아이콘을 보여 준다
        SizedBox(
          height: 94,
          child: c.coverUrl == null
              ? ColoredBox(color: background, child: Icon(icon, color: foreground, size: 28))
              : Image.network('$serverBase${c.coverUrl}',
                  fit: BoxFit.cover,
                  errorBuilder: (_, _, _) =>
                      ColoredBox(color: background, child: Icon(icon, color: foreground, size: 28))),
        ),
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
