import 'package:flutter/material.dart';
import 'package:provider/provider.dart';

import '../api/api_client.dart';
import '../api/models.dart';
import '../app_state.dart';
import '../theme.dart';
import '../widgets/async_view.dart';
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
          padding: const EdgeInsets.fromLTRB(16, 4, 16, 8),
          child: Row(children: [
            for (final (value, label) in [(null, '전체'), ...topicLabels.entries.map((e) => (e.key, e.value))])
              Padding(
                padding: const EdgeInsets.symmetric(horizontal: 4),
                child: ChoiceChip(
                  label: Text(label),
                  selected: _topic == value,
                  onSelected: (_) => setState(() => _topic = value),
                  showCheckmark: false,
                  selectedColor: AppColors.text,
                  backgroundColor: AppColors.surface,
                  side: const BorderSide(color: AppColors.border),
                  labelStyle: TextStyle(
                    color: _topic == value ? Colors.white : AppColors.text,
                    fontWeight: FontWeight.w600,
                  ),
                ),
              ),
            Container(width: 1, height: 20, color: AppColors.border, margin: const EdgeInsets.symmetric(horizontal: 6)),
            TextButton(
              onPressed: () => setState(() => _sort = _sort == 'latest' ? 'popular' : 'latest'),
              child: Text(_sort == 'latest' ? '최신순' : '인기순'),
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
                padding: const EdgeInsets.fromLTRB(16, 4, 16, 96),
                itemCount: posts.length,
                separatorBuilder: (_, _) => const SizedBox(height: 10),
                itemBuilder: (context, i) => _PostCard(posts[i], onBack: reload),
              );
            },
          ),
        ),
      ]),
      Positioned(
        right: 16,
        bottom: 16,
        child: FloatingActionButton.extended(
          onPressed: _write,
          backgroundColor: AppColors.primary,
          foregroundColor: Colors.white,
          icon: const Icon(Icons.edit),
          label: const Text('글쓰기', style: TextStyle(fontWeight: FontWeight.w700)),
        ),
      ),
    ]);
  }
}

class _PostCard extends StatelessWidget {
  const _PostCard(this.p, {required this.onBack});

  final Post p;
  final Future<void> Function() onBack;

  @override
  Widget build(BuildContext context) {
    return Card(
      clipBehavior: Clip.antiAlias,
      child: InkWell(
        onTap: () async {
          await Navigator.of(context).push(MaterialPageRoute(builder: (_) => PostDetailScreen(id: p.id)));
          onBack();
        },
        child: Padding(
          padding: const EdgeInsets.all(16),
          child: Row(crossAxisAlignment: CrossAxisAlignment.start, children: [
            Expanded(
              child: Column(crossAxisAlignment: CrossAxisAlignment.start, children: [
                Row(children: [
                  Tag(p.topicLabel),
                  if (p.verify != null) ...[
                    const SizedBox(width: 6),
                    Tag(p.verify!.success ? '인증 완료' : '인증 전', color: p.verify!.success ? AppColors.mint : AppColors.muted),
                  ],
                ]),
                const SizedBox(height: 8),
                Text(p.title,
                    maxLines: 2,
                    overflow: TextOverflow.ellipsis,
                    style: const TextStyle(fontSize: 16, fontWeight: FontWeight.w800, height: 1.3)),
                if (p.excerpt.isNotEmpty) ...[
                  const SizedBox(height: 4),
                  Text(p.excerpt.replaceAll('\n', ' '),
                      maxLines: 2,
                      overflow: TextOverflow.ellipsis,
                      style: const TextStyle(color: AppColors.muted, height: 1.4)),
                ],
                const SizedBox(height: 10),
                Row(children: [
                  Flexible(
                    child: Text('${p.author.nickname} · ${shortDateTime(p.createdAt)}',
                        overflow: TextOverflow.ellipsis, style: const TextStyle(fontSize: 12.5, color: AppColors.muted)),
                  ),
                  const SizedBox(width: 10),
                  const Icon(Icons.favorite_border, size: 15, color: AppColors.muted),
                  Text(' ${p.likeCount}', style: const TextStyle(fontSize: 12.5, color: AppColors.muted)),
                  const SizedBox(width: 8),
                  const Icon(Icons.chat_bubble_outline, size: 14, color: AppColors.muted),
                  Text(' ${p.commentCount}', style: const TextStyle(fontSize: 12.5, color: AppColors.muted)),
                ]),
              ]),
            ),
            if (p.thumbnailId != null) ...[
              const SizedBox(width: 12),
              ClipRRect(
                borderRadius: BorderRadius.circular(10),
                // 글 사진은 공개 주소라 토큰 없이 받는다
                child: Image.network('$serverBase/api/post-images/${p.thumbnailId}',
                    width: 72, height: 72, fit: BoxFit.cover, errorBuilder: (_, _, _) => const SizedBox.shrink()),
              ),
            ],
          ]),
        ),
      ),
    );
  }
}
