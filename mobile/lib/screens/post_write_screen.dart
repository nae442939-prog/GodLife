import 'package:flutter/material.dart';
import 'package:provider/provider.dart';

import '../api/models.dart';
import '../app_state.dart';
import '../theme.dart';

/// 글쓰기 (말머리 · 제목 · 내용). 올리면 새 글의 id 를 돌려준다. 사진 · 인증 결과 붙이기는 웹에서 한다
/// [editing] 을 주면 그 글을 수정한다 (수정하고 나면 같은 id 를 돌려준다)
class PostWriteScreen extends StatefulWidget {
  const PostWriteScreen({super.key, this.editing});

  final PostDetail? editing;

  @override
  State<PostWriteScreen> createState() => _PostWriteScreenState();
}

class _PostWriteScreenState extends State<PostWriteScreen> {
  final _title = TextEditingController();
  final _content = TextEditingController();
  String _topic = 'FREE';
  bool _busy = false;
  String? _error;

  bool get _editing => widget.editing != null;

  @override
  void initState() {
    super.initState();
    final p = widget.editing;
    if (p != null) {
      _title.text = p.title;
      _content.text = p.content;
      _topic = p.topic;
    }
  }

  @override
  void dispose() {
    _title.dispose();
    _content.dispose();
    super.dispose();
  }

  Future<void> _submit() async {
    if (_title.text.trim().isEmpty || _content.text.trim().isEmpty) {
      setState(() => _error = '제목과 내용을 입력해 주세요.');
      return;
    }
    setState(() {
      _busy = true;
      _error = null;
    });
    try {
      final api = context.read<AppState>().api;
      final body = {
        'topic': _topic,
        'title': _title.text.trim(),
        'content': _content.text.trim(),
        'challengeId': null,
      };
      if (_editing) {
        await api.put('/api/posts/${widget.editing!.id}', body: body);
        if (mounted) Navigator.of(context).pop(widget.editing!.id);
      } else {
        final data = await api.post('/api/posts', body: body);
        if (mounted) Navigator.of(context).pop(((data as Map)['id'] as num).toInt());
      }
    } catch (e) {
      if (mounted) setState(() => _error = '$e');
    } finally {
      if (mounted) setState(() => _busy = false);
    }
  }

  @override
  Widget build(BuildContext context) {
    return Scaffold(
      appBar: AppBar(title: Text(_editing ? '글 수정' : '글쓰기')),
      body: SafeArea(
        child: ListView(padding: const EdgeInsets.fromLTRB(20, 8, 20, 24), children: [
          const Text('말머리', style: TextStyle(fontWeight: FontWeight.w700)),
          const SizedBox(height: 8),
          Wrap(spacing: 8, children: [
            for (final e in topicLabels.entries)
              ChoiceChip(
                label: Text(e.value),
                selected: _topic == e.key,
                onSelected: (_) => setState(() => _topic = e.key),
                showCheckmark: false,
                selectedColor: AppColors.text,
                backgroundColor: AppColors.surface,
                side: const BorderSide(color: AppColors.border),
                labelStyle: TextStyle(
                  color: _topic == e.key ? Colors.white : AppColors.text,
                  fontWeight: FontWeight.w600,
                ),
              ),
          ]),
          const SizedBox(height: 18),
          TextField(
            controller: _title,
            maxLength: 100,
            decoration: const InputDecoration(labelText: '제목'),
          ),
          const SizedBox(height: 8),
          TextField(
            controller: _content,
            minLines: 8,
            maxLines: 16,
            maxLength: 2000,
            decoration: const InputDecoration(labelText: '내용', alignLabelWithHint: true),
          ),
          if (_error != null) ...[
            const SizedBox(height: 8),
            Text(_error!, style: const TextStyle(color: AppColors.danger)),
          ],
          const SizedBox(height: 16),
          FilledButton(onPressed: _busy ? null : _submit, child: Text(_busy ? (_editing ? '수정하는 중…' : '올리는 중…') : (_editing ? '수정 완료' : '글 올리기'))),
        ]),
      ),
    );
  }
}
