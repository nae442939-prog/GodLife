import 'package:flutter/material.dart';
import 'package:provider/provider.dart';

import '../api/api_client.dart';
import '../api/models.dart';
import '../app_state.dart';
import '../theme.dart';
import '../widgets/async_view.dart';
import '../widgets/design.dart';
import 'post_detail_screen.dart';
import 'post_write_screen.dart';

/// 커뮤니티: 말머리로 거르는 글 목록 + 글쓰기
class CommunityScreen extends StatefulWidget {
  const CommunityScreen({super.key});

  @override
  State<CommunityScreen> createState() => _CommunityScreenState();
}

class _CommunityScreenState extends State<CommunityScreen> {
  String? _topic; // null = 전체
  String _sort = 'latest';
  int _version = 0; // 글을 쓰고 돌아오면 목록을 새로 불러온다

  Future<List<Post>> _load() async {
    final params = ['sort=$_sort', if (_topic != null) 'topic=$_topic'];
    final data = await context.read<AppState>().api.get('/api/posts?${params.join('&')}');
    return ((data as Map)['items'] as List).map((p) => Post.fromJson(p as Map<String, dynamic>)).toList();
  }

  Future<void> _write() async {
    final created = await Navigator.of(context).push<int>(MaterialPageRoute(builder: (_) => const PostWriteScreen()));
    if (created == null || !mounted) return;
    await Navigator.of(context).push(MaterialPageRoute(builder: (_) => PostDetailScreen(id: created)));
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
            for (final (value, label) in [(null, '전체'), ...topicLabels.entries.map((e) => (e.key, e.value))])
              Padding(
                padding: const EdgeInsets.symmetric(horizontal: 4),
                child: PillChip(label, selected: _topic == value, onTap: () => setState(() => _topic = value)),
              ),
            // 맨 끝: 인기순으로 보기 (켜면 좋아요 많은 글부터)
            Padding(
              padding: const EdgeInsets.symmetric(horizontal: 4),
              child: PillChip('인기순',
                  selected: _sort == 'popular',
                  onTap: () => setState(() => _sort = _sort == 'latest' ? 'popular' : 'latest')),
            ),
          ]),
        ),
        Expanded(
          child: AsyncView<List<Post>>(
            // 거르는 조건이 바뀌면 새로 불러온다
            key: ValueKey('$_topic/$_sort/$_version'),
            load: _load,
            builder: (context, posts, reload) {
              if (posts.isEmpty) {
                return ListView(children: const [
                  SizedBox(height: 120),
                  Center(child: Text('아직 글이 없어요. 첫 글을 남겨 보세요.', style: TextStyle(color: AppColors.muted))),
                ]);
              }
              return ListView.separated(
                padding: const EdgeInsets.fromLTRB(20, 0, 20, 96),
                itemCount: posts.length,
                separatorBuilder: (_, _) => const SizedBox(height: 11),
                itemBuilder: (context, i) => _PostCard(posts[i], onBack: reload),
              );
            },
          ),
        ),
      ]),
      Positioned(
        right: 20,
        bottom: 18,
        child: PillFab(label: '글쓰기', icon: Icons.edit_outlined, onTap: _write),
      ),
    ]);
  }
}

/// 글 한 장 (시안): 말머리 꼬리표 → 제목 → 본문 두 줄 → 글쓴이 · 날짜 | 좋아요 · 댓글 수
class _PostCard extends StatelessWidget {
  const _PostCard(this.p, {required this.onBack});

  final Post p;
  final Future<void> Function() onBack;

  /// 말머리마다 다른 색
  static (Color, Color) _topicColors(String topic) => switch (topic) {
        'FREE' => ColorTag.gold,
        'REVIEW' => ColorTag.lav,
        'TIP' => ColorTag.mint,
        _ => ColorTag.coral,
      };

  @override
  Widget build(BuildContext context) {
    const idle = Color(0xFFC7C0B0);
    return AppCard(
      bordered: false,
      radius: 18,
      padding: const EdgeInsets.fromLTRB(16, 15, 16, 14),
      onTap: () async {
        await Navigator.of(context).push(MaterialPageRoute(builder: (_) => PostDetailScreen(id: p.id)));
        onBack();
      },
      child: Row(crossAxisAlignment: CrossAxisAlignment.start, children: [
        Expanded(
          child: Column(crossAxisAlignment: CrossAxisAlignment.start, children: [
            Row(children: [
              ColorTag.of(p.topicLabel, _topicColors(p.topic)),
              if (p.verify != null && p.verify!.success) ...[
                const SizedBox(width: 6),
                ColorTag.of('인증 완료', ColorTag.mint),
              ],
            ]),
            const SizedBox(height: 9),
            Text(p.title,
                maxLines: 2,
                overflow: TextOverflow.ellipsis,
                style: const TextStyle(fontSize: 14.5, fontWeight: FontWeight.w800, letterSpacing: -0.1)),
            if (p.excerpt.isNotEmpty) ...[
              const SizedBox(height: 5),
              Text(p.excerpt.replaceAll('\n', ' '),
                  maxLines: 2,
                  overflow: TextOverflow.ellipsis,
                  style: const TextStyle(
                      fontSize: 12, fontWeight: FontWeight.w500, color: Color(0xFF8A8071), height: 1.55)),
            ],
            const SizedBox(height: 10),
            Row(children: [
              Expanded(
                child: Text('${p.author.nickname} · ${shortDateTime(p.createdAt)}',
                    overflow: TextOverflow.ellipsis,
                    style: const TextStyle(fontSize: 11, fontWeight: FontWeight.w700, color: AppColors.muted)),
              ),
              // 좋아요가 있으면 코랄로 채운 하트
              Icon(p.likeCount > 0 ? Icons.favorite : Icons.favorite_border,
                  size: 13, color: p.likeCount > 0 ? AppColors.primary : idle),
              const SizedBox(width: 4),
              Text('${p.likeCount}',
                  style: const TextStyle(fontSize: 11, fontWeight: FontWeight.w700, color: AppColors.muted)),
              const SizedBox(width: 12),
              const Icon(Icons.chat_bubble_outline, size: 13, color: idle),
              const SizedBox(width: 4),
              Text('${p.commentCount}',
                  style: const TextStyle(fontSize: 11, fontWeight: FontWeight.w700, color: AppColors.muted)),
            ]),
          ]),
        ),
        if (p.thumbnailId != null) ...[
          const SizedBox(width: 12),
          ClipRRect(
            borderRadius: BorderRadius.circular(12),
            // 글 사진은 공개 주소라 토큰 없이 받는다
            child: Image.network('$serverBase/api/post-images/${p.thumbnailId}',
                width: 64, height: 64, fit: BoxFit.cover, errorBuilder: (_, _, _) => const SizedBox.shrink()),
          ),
        ],
      ]),
    );
  }
}
