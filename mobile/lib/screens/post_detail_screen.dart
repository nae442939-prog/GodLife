import 'package:flutter/material.dart';
import 'package:provider/provider.dart';

import '../api/api_client.dart';
import '../api/models.dart';
import '../app_state.dart';
import '../theme.dart';
import '../widgets/async_view.dart';

/// 커뮤니티 글 상세: 본문 · 사진 · 좋아요 · 댓글(답글은 한 단계 들여 쓴다) · 댓글 쓰기
class PostDetailScreen extends StatefulWidget {
  const PostDetailScreen({super.key, required this.id});

  final int id;

  @override
  State<PostDetailScreen> createState() => _PostDetailScreenState();
}

class _PostDetailScreenState extends State<PostDetailScreen> {
  final _view = GlobalKey<AsyncViewState<PostDetail>>();
  final _comment = TextEditingController();
  bool _sending = false;

  ApiClient get _api => context.read<AppState>().api;

  @override
  void dispose() {
    _comment.dispose();
    super.dispose();
  }

  Future<PostDetail> _load() async {
    return PostDetail.fromJson(await _api.get('/api/posts/${widget.id}') as Map<String, dynamic>);
  }

  Future<void> _toggleLike(PostDetail p) async {
    try {
      final data = p.liked
          ? await _api.delete('/api/posts/${p.id}/like')
          : await _api.put('/api/posts/${p.id}/like');
      setState(() {
        p.liked = (data as Map)['liked'] as bool;
        p.likeCount = (data['likeCount'] as num).toInt();
      });
    } catch (e) {
      if (mounted) showMessage(context, '$e');
    }
  }

  Future<void> _sendComment() async {
    final text = _comment.text.trim();
    if (text.isEmpty || _sending) return;
    setState(() => _sending = true);
    try {
      await _api.post('/api/posts/${widget.id}/comments', body: {'content': text, 'parentId': null});
      _comment.clear();
      if (mounted) FocusScope.of(context).unfocus();
      await _view.currentState?.reload();
    } catch (e) {
      if (mounted) showMessage(context, '$e');
    } finally {
      if (mounted) setState(() => _sending = false);
    }
  }

  Future<void> _delete(PostDetail p) async {
    final ok = await showDialog<bool>(
      context: context,
      builder: (context) => AlertDialog(
        title: const Text('이 글을 지울까요?'),
        content: const Text('지운 글은 되돌릴 수 없어요.'),
        actions: [
          TextButton(onPressed: () => Navigator.pop(context, false), child: const Text('취소')),
          TextButton(onPressed: () => Navigator.pop(context, true), child: const Text('지우기')),
        ],
      ),
    );
    if (ok != true) return;
    try {
      await _api.delete('/api/posts/${p.id}');
      if (mounted) Navigator.of(context).pop();
    } catch (e) {
      if (mounted) showMessage(context, '$e');
    }
  }

  @override
  Widget build(BuildContext context) {
    return Scaffold(
      appBar: AppBar(title: const Text('커뮤니티')),
      body: SafeArea(
        child: AsyncView<PostDetail>(
          key: _view,
          load: _load,
          builder: (context, p, reload) => Column(children: [
            Expanded(
              child: ListView(padding: const EdgeInsets.fromLTRB(20, 8, 20, 20), children: [
                Row(children: [
                  CircleAvatar(
                    radius: 18,
                    backgroundColor: AppColors.surfaceAlt,
                    backgroundImage:
                        p.author.profileImageUrl != null ? NetworkImage('$serverBase${p.author.profileImageUrl}') : null,
                    child: p.author.profileImageUrl == null
                        ? const Icon(Icons.person, size: 20, color: AppColors.muted)
                        : null,
                  ),
                  const SizedBox(width: 10),
                  Expanded(
                    child: Column(crossAxisAlignment: CrossAxisAlignment.start, children: [
                      Text(p.author.nickname, style: const TextStyle(fontWeight: FontWeight.w700)),
                      Text(shortDateTime(p.createdAt), style: const TextStyle(fontSize: 12, color: AppColors.muted)),
                    ]),
                  ),
                  if (p.mine)
                    IconButton(
                      tooltip: '글 지우기',
                      onPressed: () => _delete(p),
                      icon: const Icon(Icons.delete_outline, color: AppColors.muted),
                    ),
                ]),
                const SizedBox(height: 16),
                Row(children: [Tag(p.topicLabel)]),
                const SizedBox(height: 8),
                Text(p.title, style: const TextStyle(fontSize: 21, fontWeight: FontWeight.w900, height: 1.3)),
                if (p.verify != null) ...[
                  const SizedBox(height: 12),
                  Container(
                    padding: const EdgeInsets.all(12),
                    decoration: BoxDecoration(color: AppColors.surfaceAlt, borderRadius: BorderRadius.circular(12)),
                    child: Row(children: [
                      Icon(p.verify!.success ? Icons.check_circle : Icons.radio_button_unchecked,
                          color: p.verify!.success ? AppColors.mint : AppColors.muted, size: 20),
                      const SizedBox(width: 8),
                      Expanded(
                        child: Text('${p.verify!.success ? '인증 완료' : '인증 전'} · ${p.verify!.challengeTitle}',
                            style: const TextStyle(fontWeight: FontWeight.w700)),
                      ),
                    ]),
                  ),
                ],
                const SizedBox(height: 16),
                Text(p.content, style: const TextStyle(fontSize: 15.5, height: 1.6)),
                for (final imageId in p.imageIds) ...[
                  const SizedBox(height: 12),
                  ClipRRect(
                    borderRadius: BorderRadius.circular(12),
                    child: Image.network('$serverBase/api/post-images/$imageId',
                        fit: BoxFit.cover, errorBuilder: (_, _, _) => const SizedBox.shrink()),
                  ),
                ],
                const SizedBox(height: 18),
                Align(
                  alignment: Alignment.centerLeft,
                  child: OutlinedButton.icon(
                    onPressed: () => _toggleLike(p),
                    icon: Icon(p.liked ? Icons.favorite : Icons.favorite_border,
                        color: p.liked ? AppColors.danger : AppColors.muted, size: 18),
                    label: Text('좋아요 ${p.likeCount}', style: const TextStyle(color: AppColors.text)),
                    style: OutlinedButton.styleFrom(side: const BorderSide(color: AppColors.border)),
                  ),
                ),
                const Divider(height: 36, color: AppColors.border),
                Text('댓글 ${p.comments.where((c) => !c.removed).length}',
                    style: const TextStyle(fontSize: 16, fontWeight: FontWeight.w800)),
                const SizedBox(height: 8),
                if (p.comments.isEmpty)
                  const Padding(
                    padding: EdgeInsets.symmetric(vertical: 16),
                    child: Text('아직 댓글이 없어요. 첫 댓글을 남겨 보세요.', style: TextStyle(color: AppColors.muted)),
                  ),
                for (final c in p.comments) _commentTile(c),
              ]),
            ),
            Container(
              decoration: const BoxDecoration(
                color: AppColors.surface,
                border: Border(top: BorderSide(color: AppColors.border)),
              ),
              padding: const EdgeInsets.fromLTRB(16, 8, 8, 8),
              child: Row(children: [
                Expanded(
                  child: TextField(
                    controller: _comment,
                    minLines: 1,
                    maxLines: 3,
                    maxLength: 500,
                    decoration: const InputDecoration(
                      hintText: '댓글을 남겨 보세요',
                      counterText: '',
                      isDense: true,
                    ),
                  ),
                ),
                IconButton(
                  onPressed: _sending ? null : _sendComment,
                  icon: const Icon(Icons.send, color: AppColors.primary),
                  tooltip: '등록',
                ),
              ]),
            ),
          ]),
        ),
      ),
    );
  }

  Widget _commentTile(PostComment c) {
    return Padding(
      padding: EdgeInsets.only(left: c.parentId == null ? 0 : 28, top: 10, bottom: 2),
      child: c.removed
          ? const Text('삭제된 댓글이에요', style: TextStyle(color: AppColors.muted))
          : Column(crossAxisAlignment: CrossAxisAlignment.start, children: [
              Row(children: [
                if (c.parentId != null)
                  const Padding(
                    padding: EdgeInsets.only(right: 4),
                    child: Icon(Icons.subdirectory_arrow_right, size: 16, color: AppColors.muted),
                  ),
                Text(c.author?.nickname ?? '', style: const TextStyle(fontWeight: FontWeight.w700, fontSize: 13.5)),
                const SizedBox(width: 8),
                Text(shortDateTime(c.createdAt), style: const TextStyle(fontSize: 12, color: AppColors.muted)),
              ]),
              const SizedBox(height: 3),
              Text(c.content, style: const TextStyle(height: 1.45)),
            ]),
    );
  }
}
