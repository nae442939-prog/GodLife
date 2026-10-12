import 'package:flutter/material.dart';
import 'package:provider/provider.dart';

import '../api/models.dart';
import '../app_state.dart';
import '../theme.dart';
import '../widgets/async_view.dart';
import '../widgets/category_icon.dart';
import '../widgets/design.dart';
import 'challenge_detail_screen.dart';
import 'post_detail_screen.dart';
import 'verify_screen.dart';

/// 홈: 오늘 인증할 챌린지 → 모집 중인 챌린지 → 커뮤니티 인기글
class HomeScreen extends StatelessWidget {
  const HomeScreen({super.key, required this.onBrowse, required this.onCommunity});

  /// '전체 보기'를 누르면 아래 탭을 그쪽으로 옮긴다
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
          state.api.get('/api/posts?sort=popular'),
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
          posts: (((results[2] as Map)['items']) as List)
              .map((p) => Post.fromJson(p as Map<String, dynamic>))
              .take(3)
              .toList(),
        );
      },
      builder: (context, data, reload) {
        final todo = data.mine.where((c) => c.canVerify).length;
        return ListView(padding: const EdgeInsets.only(bottom: 28), children: [
          _Hero(onBrowse: onBrowse),
          Padding(
            padding: const EdgeInsets.fromLTRB(20, 22, 20, 14),
            child: Column(crossAxisAlignment: CrossAxisAlignment.start, children: [
              Text('${state.user?.nickname ?? ''}님,',
                  style: const TextStyle(fontSize: 15, color: AppColors.muted, fontWeight: FontWeight.w600)),
              const SizedBox(height: 2),
              Text(
                data.mine.isEmpty
                    ? '첫 챌린지에 도전해 볼까요?'
                    : todo > 0
                        ? '오늘 인증할 챌린지가 $todo개 있어요'
                        : '오늘 인증은 다 했어요!',
                style: const TextStyle(fontSize: 22, fontWeight: FontWeight.w900),
              ),
            ]),
          ),
          if (data.mine.isEmpty)
            Padding(
              padding: const EdgeInsets.symmetric(horizontal: 16),
              child: Card(
                child: Padding(
                  padding: const EdgeInsets.all(18),
                  child: Column(crossAxisAlignment: CrossAxisAlignment.start, children: [
                    const Text('참여 중인 챌린지가 없어요', style: TextStyle(fontWeight: FontWeight.w800, fontSize: 16)),
                    const SizedBox(height: 4),
                    const Text('습관 하나를 골라 오늘부터 인증해 보세요.', style: TextStyle(color: AppColors.muted)),
                    const SizedBox(height: 14),
                    FilledButton(onPressed: onBrowse, child: const Text('챌린지 둘러보기')),
                  ]),
                ),
              ),
            ),
          // 오늘 할 일: 챌린지마다 한 줄 (아이콘 · 제목 · 진행 · [인증] 또는 완료 표시)
          if (data.mine.isNotEmpty)
            Padding(
              padding: const EdgeInsets.symmetric(horizontal: 16),
              child: Card(
                clipBehavior: Clip.antiAlias,
                child: Column(children: [
                  for (final (i, c) in data.mine.indexed) ...[
                    if (i > 0) const Divider(height: 1, indent: 76, color: AppColors.border),
                    _TodayRow(c, reload: reload),
                  ],
                ]),
              ),
            ),
          _SectionHead('모집 중인 챌린지', onMore: onBrowse),
          SizedBox(
            height: 166,
            child: ListView.separated(
              scrollDirection: Axis.horizontal,
              padding: const EdgeInsets.symmetric(horizontal: 16),
              itemCount: data.recruiting.length,
              separatorBuilder: (_, _) => const SizedBox(width: 12),
              itemBuilder: (context, i) => _RecruitingCard(data.recruiting[i], reload: reload),
            ),
          ),
          _SectionHead('커뮤니티 인기글', onMore: onCommunity),
          Padding(
            padding: const EdgeInsets.symmetric(horizontal: 16),
            child: Card(
              clipBehavior: Clip.antiAlias,
              child: Column(children: [
                for (final (i, p) in data.posts.indexed) ...[
                  if (i > 0) const Divider(height: 1, color: AppColors.border),
                  ListTile(
                    onTap: () => Navigator.of(context)
                        .push(MaterialPageRoute(builder: (_) => PostDetailScreen(id: p.id))),
                    title: Text(p.title,
                        maxLines: 1,
                        overflow: TextOverflow.ellipsis,
                        style: const TextStyle(fontWeight: FontWeight.w700)),
                    subtitle: Text('${p.topicLabel} · ${p.author.nickname} · 좋아요 ${p.likeCount} · 댓글 ${p.commentCount}',
                        style: const TextStyle(fontSize: 12.5)),
                    trailing: const Icon(Icons.chevron_right, color: AppColors.muted),
                  ),
                ],
                if (data.posts.isEmpty)
                  const Padding(
                    padding: EdgeInsets.all(24),
                    child: Text('아직 글이 없어요.', style: TextStyle(color: AppColors.muted)),
                  ),
              ]),
            ),
          ),
        ]);
      },
    );
  }
}

class _HomeData {
  _HomeData({required this.mine, required this.recruiting, required this.posts});

  final List<MyChallenge> mine;
  final List<Challenge> recruiting;
  final List<Post> posts;
}

class _SectionHead extends StatelessWidget {
  const _SectionHead(this.title, {required this.onMore});

  final String title;
  final VoidCallback onMore;

  @override
  Widget build(BuildContext context) {
    return Padding(
      padding: const EdgeInsets.fromLTRB(20, 18, 8, 6),
      child: Row(children: [
        Expanded(child: Text(title, style: const TextStyle(fontSize: 17, fontWeight: FontWeight.w800))),
        TextButton(onPressed: onMore, child: const Text('전체 보기')),
      ]),
    );
  }
}

/// 가로로 넘기는 모집 중 챌린지 카드
class _RecruitingCard extends StatelessWidget {
  const _RecruitingCard(this.c, {required this.reload});

  final Challenge c;
  final Future<void> Function() reload;

  @override
  Widget build(BuildContext context) {
    return SizedBox(
      width: 230,
      child: Card(
        clipBehavior: Clip.antiAlias,
        child: InkWell(
          onTap: () async {
            await Navigator.of(context).push(MaterialPageRoute(builder: (_) => ChallengeDetailScreen(id: c.id)));
            reload();
          },
          child: Padding(
            padding: const EdgeInsets.all(14),
            child: Column(crossAxisAlignment: CrossAxisAlignment.start, children: [
              Row(children: [
                CategoryIcon(c.category.name, title: c.title, subType: c.subTypeName, size: 40),
                const Spacer(),
                PriceBadge(free: c.isFree, fee: c.entryFee),
              ]),
              const SizedBox(height: 12),
              Text(c.title,
                  maxLines: 2,
                  overflow: TextOverflow.ellipsis,
                  style: const TextStyle(fontSize: 15.5, fontWeight: FontWeight.w800, height: 1.25)),
              const Spacer(),
              Text('${c.totalDays}일 · ${c.frequencyLabel} · ${c.participantCount}명',
                  style: const TextStyle(fontSize: 12.5, color: AppColors.muted, fontWeight: FontWeight.w600)),
            ]),
          ),
        ),
      ),
    );
  }
}

/// 맨 위 소개 띠 (웹 첫 화면과 같은 문구)
class _Hero extends StatelessWidget {
  const _Hero({required this.onBrowse});

  final VoidCallback onBrowse;

  @override
  Widget build(BuildContext context) {
    return Container(
      margin: const EdgeInsets.fromLTRB(16, 4, 16, 0),
      padding: const EdgeInsets.fromLTRB(20, 22, 20, 20),
      decoration: BoxDecoration(
        borderRadius: BorderRadius.circular(20),
        gradient: const LinearGradient(
          begin: Alignment.topLeft,
          end: Alignment.bottomRight,
          colors: [Color(0xFFFFE9DC), Color(0xFFFFF4E4), Color(0xFFE3F6F1)],
        ),
        border: Border.all(color: AppColors.border),
      ),
      child: Column(crossAxisAlignment: CrossAxisAlignment.start, children: [
        const Tag('습관 챌린지 서비스', color: AppColors.primary),
        const SizedBox(height: 12),
        const Text('오늘의 습관에 도전하고,\n성공하면 돌려받으세요',
            style: TextStyle(fontSize: 24, fontWeight: FontWeight.w900, height: 1.3)),
        const SizedBox(height: 8),
        const Text('함께 도전하고 사진으로 인증하는 갓생 챌린지',
            style: TextStyle(color: AppColors.muted, fontSize: 14, height: 1.4)),
        const SizedBox(height: 16),
        FilledButton(
          onPressed: onBrowse,
          style: FilledButton.styleFrom(minimumSize: const Size(0, 46), padding: const EdgeInsets.symmetric(horizontal: 22)),
          child: const Text('챌린지 둘러보기'),
        ),
      ]),
    );
  }
}

/// 오늘 할 일 한 줄. 인증할 수 있으면 [인증] 버튼, 끝났으면 완료 표시
class _TodayRow extends StatelessWidget {
  const _TodayRow(this.c, {required this.reload});

  final MyChallenge c;
  final Future<void> Function() reload;

  @override
  Widget build(BuildContext context) {
    final done = c.verifyState == 'DONE_TODAY' || c.verifyState == 'WEEK_DONE';
    return InkWell(
      onTap: () async {
        await Navigator.of(context).push(MaterialPageRoute(builder: (_) => ChallengeDetailScreen(id: c.id)));
        reload();
      },
      child: Padding(
        padding: const EdgeInsets.fromLTRB(16, 14, 14, 14),
        child: Row(children: [
          CategoryIcon(c.category.name, title: c.title),
          const SizedBox(width: 14),
          Expanded(
            child: Column(crossAxisAlignment: CrossAxisAlignment.start, children: [
              Text(c.title,
                  maxLines: 1,
                  overflow: TextOverflow.ellipsis,
                  style: const TextStyle(fontSize: 16, fontWeight: FontWeight.w800)),
              const SizedBox(height: 6),
              Row(children: [
                Expanded(
                  child: ClipRRect(
                    borderRadius: BorderRadius.circular(99),
                    child: LinearProgressIndicator(
                      value: c.progress,
                      minHeight: 6,
                      backgroundColor: AppColors.border,
                      color: AppColors.mint,
                    ),
                  ),
                ),
                const SizedBox(width: 10),
                Text('${c.successDays}/${c.targetCount}',
                    style: const TextStyle(fontSize: 12.5, color: AppColors.muted, fontWeight: FontWeight.w600)),
              ]),
            ]),
          ),
          const SizedBox(width: 12),
          if (c.canVerify)
            FilledButton(
              onPressed: () async {
                await Navigator.of(context)
                    .push(MaterialPageRoute(builder: (_) => VerifyScreen(challengeId: c.id, title: c.title)));
                reload();
              },
              style: FilledButton.styleFrom(
                minimumSize: const Size(64, 40),
                padding: const EdgeInsets.symmetric(horizontal: 16),
                shape: RoundedRectangleBorder(borderRadius: BorderRadius.circular(99)),
              ),
              child: const Text('인증'),
            )
          else if (done)
            const Icon(Icons.check_circle, color: AppColors.mint, size: 28)
          else
            const Icon(Icons.schedule, color: AppColors.muted, size: 24),
        ]),
      ),
    );
  }
}
