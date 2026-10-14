import 'dart:async';
import 'dart:typed_data';

import 'package:flutter/material.dart';
import 'package:image_picker/image_picker.dart';
import 'package:provider/provider.dart';

import '../api/api_client.dart';
import '../app_state.dart';
import '../theme.dart';
import '../widgets/async_view.dart';
import '../widgets/user_avatar.dart';
import 'profile_screen.dart';

const _page = 50; // 서버가 한 번에 주는 메시지 수

/// (서버 값, 화면 글자) — 웹의 신고 사유와 같다
const _reportReasons = [
  ('ABUSE', '욕설 · 비방'),
  ('SPAM', '스팸 · 광고'),
  ('INAPPROPRIATE', '부적절한 내용'),
  ('OTHER', '기타'),
];

/// 챌린지 오픈채팅 (개설자 · 참가자만, 웹의 오픈채팅과 같은 규칙).
/// 3초마다 새 메시지를 불러오고, 새 메시지가 오면 맨 아래로 내린다. 사진도 보낼 수 있다.
/// 남의 메시지를 길게 누르면 프로필 · 신고 · 차단이, 방장에게는 내보내기가 나온다. 방장은 공지를 올리고 내린다.
class ChallengeChatScreen extends StatefulWidget {
  const ChallengeChatScreen({super.key, required this.challengeId, required this.title});

  final int challengeId;
  final String title;

  @override
  State<ChallengeChatScreen> createState() => _ChallengeChatScreenState();
}

class _ChallengeChatScreenState extends State<ChallengeChatScreen> {
  final _input = TextEditingController();
  final _scroll = ScrollController();

  /// 받아 둔 사진 (메시지 id → 사진). 목록을 다시 그려도 다시 받지 않는다
  final _images = <int, Future<Uint8List>>{};

  List<Map<String, dynamic>>? _messages;
  String? _notice;
  bool _host = false;
  bool _hasOlder = false;
  bool _busy = false;
  Object? _error;
  Timer? _timer;

  ApiClient get _api => context.read<AppState>().api;
  String get _challenge => '/api/challenges/${widget.challengeId}';
  String get _base => '$_challenge/messages';

  @override
  void initState() {
    super.initState();
    _open();
  }

  @override
  void dispose() {
    _timer?.cancel();
    _input.dispose();
    _scroll.dispose();
    super.dispose();
  }

  Future<void> _open() async {
    try {
      final detail = await _api.get(_challenge) as Map<String, dynamic>;
      final list = ((await _api.get(_base)) as List).cast<Map<String, dynamic>>();
      if (!mounted) return;
      setState(() {
        _notice = detail['notice'] as String?;
        _host = detail['host'] == true;
        _messages = list;
        _hasOlder = list.length >= _page;
        _error = null;
      });
      _toBottom();
      _timer ??= Timer.periodic(const Duration(seconds: 3), (_) => _poll());
    } catch (e) {
      if (mounted) setState(() => _error = e);
    }
  }

  Future<void> _poll() async {
    final messages = _messages;
    if (messages == null) return;
    try {
      final after = messages.isEmpty ? null : messages.last['id'];
      final fresh =
          ((await _api.get(after == null ? _base : '$_base?after=$after')) as List).cast<Map<String, dynamic>>();
      if (!mounted || fresh.isEmpty) return;
      _append(fresh);
      // 공지 · 내보내기 안내가 오면 공지도 바뀌었을 수 있다
      if (fresh.any((m) => m['type'] == 'SYSTEM')) _refreshNotice();
    } catch (_) {
      // 잠깐 끊겨도 다음 차례에 다시 받는다
    }
  }

  void _append(List<Map<String, dynamic>> fresh) {
    setState(() {
      final known = {for (final m in _messages!) m['id']};
      _messages = [..._messages!, ...fresh.where((m) => !known.contains(m['id']))];
    });
    _toBottom();
  }

  Future<void> _refreshNotice() async {
    try {
      final detail = await _api.get(_challenge) as Map<String, dynamic>;
      if (mounted) setState(() => _notice = detail['notice'] as String?);
    } catch (_) {
      // 공지만 예전 것으로 남는다
    }
  }

  Future<void> _loadOlder() async {
    final messages = _messages;
    if (messages == null || messages.isEmpty) return;
    try {
      final older = ((await _api.get('$_base?before=${messages.first['id']}')) as List).cast<Map<String, dynamic>>();
      if (mounted) {
        setState(() {
          _messages = [...older, ..._messages!];
          _hasOlder = older.length >= _page;
        });
      }
    } catch (e) {
      if (mounted) showMessage(context, '$e');
    }
  }

  Future<void> _send() async {
    final text = _input.text.trim();
    if (text.isEmpty || _busy) return;
    setState(() => _busy = true);
    try {
      final sent = await _api.post(_base, body: {'content': text}) as Map<String, dynamic>;
      if (!mounted) return;
      _input.clear();
      _append([sent]);
    } catch (e) {
      if (mounted) showMessage(context, '$e');
    } finally {
      if (mounted) setState(() => _busy = false);
    }
  }

  Future<void> _sendPhoto() async {
    if (_busy) return;
    final XFile? picked;
    try {
      picked = await ImagePicker().pickImage(source: ImageSource.gallery, maxWidth: 1600, imageQuality: 85);
    } catch (_) {
      if (mounted) showMessage(context, '사진을 불러오지 못했어요.');
      return;
    }
    if (picked == null || !mounted) return;
    setState(() => _busy = true);
    try {
      final sent = await _api.upload('$_base/image', await picked.readAsBytes(), 'chat.jpg') as Map<String, dynamic>;
      if (mounted) _append([sent]);
    } catch (e) {
      if (mounted) showMessage(context, '$e');
    } finally {
      if (mounted) setState(() => _busy = false);
    }
  }

  /// 방장: 공지 올리기 · 바꾸기 (비우면 내린다)
  Future<void> _editNotice() async {
    final field = TextEditingController(text: _notice ?? '');
    final text = await showDialog<String>(
      context: context,
      builder: (context) => AlertDialog(
        title: const Text('공지'),
        content: TextField(
          controller: field,
          maxLength: 300,
          maxLines: 4,
          decoration: const InputDecoration(hintText: '채팅방 맨 위에 고정돼요. 비우면 공지를 내려요.'),
        ),
        actions: [
          TextButton(onPressed: () => Navigator.pop(context), child: const Text('취소')),
          TextButton(onPressed: () => Navigator.pop(context, field.text.trim()), child: const Text('저장')),
        ],
      ),
    );
    if (text == null || !mounted) return;
    try {
      await _api.put('$_challenge/notice', body: {'content': text});
      await _refreshNotice();
      _poll();
    } catch (e) {
      if (mounted) showMessage(context, '$e');
    }
  }

  /// 남의 메시지를 길게 눌렀을 때: 프로필 · 신고 · 차단 (+ 방장은 내보내기)
  Future<void> _openMenu(Map<String, dynamic> m) async {
    final nickname = m['senderNickname'] as String;
    final senderId = (m['senderId'] as num).toInt();
    final choice = await showModalBottomSheet<String>(
      context: context,
      backgroundColor: AppColors.bg,
      shape: const RoundedRectangleBorder(borderRadius: BorderRadius.vertical(top: Radius.circular(26))),
      builder: (context) => SafeArea(
        child: Padding(
          padding: const EdgeInsets.symmetric(vertical: 10),
          child: Column(mainAxisSize: MainAxisSize.min, children: [
            ListTile(
              leading: const Icon(Icons.person_outline),
              title: Text('$nickname님 프로필'),
              onTap: () => Navigator.pop(context, 'profile'),
            ),
            ListTile(
              leading: const Icon(Icons.flag_outlined),
              title: const Text('이 메시지 신고'),
              onTap: () => Navigator.pop(context, 'report'),
            ),
            ListTile(
              leading: const Icon(Icons.block),
              title: Text('$nickname님 차단'),
              onTap: () => Navigator.pop(context, 'block'),
            ),
            if (_host)
              ListTile(
                leading: const Icon(Icons.logout, color: Color(0xFFC2574A)),
                title: Text('$nickname님 내보내기', style: const TextStyle(color: Color(0xFFC2574A))),
                onTap: () => Navigator.pop(context, 'kick'),
              ),
          ]),
        ),
      ),
    );
    if (!mounted || choice == null) return;
    switch (choice) {
      case 'profile':
        Navigator.of(context).push(MaterialPageRoute(builder: (_) => ProfileScreen(userId: senderId)));
      case 'report':
        await _report(m);
      case 'block':
        if (await _confirm('$nickname님을 차단할까요?', '서로의 팔로우가 끊기고, 이 회원의 채팅 · 글 · 댓글이 내 화면에 보이지 않아요.', '차단')) {
          await _run(() => _api.put('/api/users/me/blocks/$senderId'), '$nickname님을 차단했어요.');
          await _open();
        }
      case 'kick':
        if (await _confirm('$nickname님을 내보낼까요?', '다시 참여할 수 없고, 포인트 챌린지면 건 포인트를 돌려줘요.', '내보내기')) {
          await _run(() => _api.post('$_challenge/participants/$senderId/kick'), '$nickname님을 내보냈어요.');
          await _open();
        }
    }
  }

  Future<void> _report(Map<String, dynamic> m) async {
    var reason = _reportReasons.first.$1;
    final ok = await showDialog<bool>(
      context: context,
      builder: (context) => StatefulBuilder(
        builder: (context, setLocal) => AlertDialog(
          title: const Text('메시지 신고'),
          content: Column(mainAxisSize: MainAxisSize.min, crossAxisAlignment: CrossAxisAlignment.stretch, children: [
            for (final (value, label) in _reportReasons)
              Padding(
                padding: const EdgeInsets.only(bottom: 8),
                child: GestureDetector(
                  onTap: () => setLocal(() => reason = value),
                  child: Container(
                    padding: const EdgeInsets.symmetric(horizontal: 14, vertical: 12),
                    decoration: BoxDecoration(
                      color: reason == value ? AppColors.text : AppColors.surfaceAlt,
                      borderRadius: BorderRadius.circular(14),
                    ),
                    child: Text(label,
                        style: TextStyle(
                            fontSize: 12.5,
                            fontWeight: FontWeight.w700,
                            color: reason == value ? Colors.white : AppColors.chipText)),
                  ),
                ),
              ),
          ]),
          actions: [
            TextButton(onPressed: () => Navigator.pop(context, false), child: const Text('취소')),
            TextButton(onPressed: () => Navigator.pop(context, true), child: const Text('신고')),
          ],
        ),
      ),
    );
    if (ok != true) return;
    await _run(() => _api.post('$_base/${m['id']}/reports', body: {'reason': reason}), '신고를 접수했어요.');
  }

  Future<bool> _confirm(String title, String body, String action) async {
    final ok = await showDialog<bool>(
      context: context,
      builder: (context) => AlertDialog(
        title: Text(title),
        content: Text(body),
        actions: [
          TextButton(onPressed: () => Navigator.pop(context, false), child: const Text('취소')),
          TextButton(onPressed: () => Navigator.pop(context, true), child: Text(action)),
        ],
      ),
    );
    return ok == true;
  }

  Future<void> _run(Future<dynamic> Function() call, String done) async {
    try {
      await call();
      if (mounted) showMessage(context, done);
    } catch (e) {
      if (mounted) showMessage(context, '$e');
    }
  }

  void _toBottom() {
    WidgetsBinding.instance.addPostFrameCallback((_) {
      if (_scroll.hasClients) _scroll.jumpTo(_scroll.position.maxScrollExtent);
    });
  }

  @override
  Widget build(BuildContext context) {
    final messages = _messages;
    return Scaffold(
      appBar: AppBar(
        title: Text(widget.title, overflow: TextOverflow.ellipsis),
        actions: [
          if (_host)
            IconButton(tooltip: '공지', onPressed: _editNotice, icon: const Icon(Icons.campaign_outlined)),
          const SizedBox(width: 6),
        ],
      ),
      body: SafeArea(
        child: messages == null
            ? Center(
                child: _error == null
                    ? const CircularProgressIndicator()
                    : Padding(
                        padding: const EdgeInsets.all(32),
                        child: Text('$_error',
                            textAlign: TextAlign.center,
                            style: const TextStyle(color: AppColors.muted, fontWeight: FontWeight.w600)),
                      ),
              )
            : Column(children: [
                if (_notice != null && _notice!.isNotEmpty)
                  Container(
                    width: double.infinity,
                    margin: const EdgeInsets.fromLTRB(16, 4, 16, 6),
                    padding: const EdgeInsets.all(13),
                    decoration: BoxDecoration(color: const Color(0xFFFAECD1), borderRadius: BorderRadius.circular(16)),
                    child: Row(crossAxisAlignment: CrossAxisAlignment.start, children: [
                      const Icon(Icons.campaign, size: 18, color: Color(0xFFC2932A)),
                      const SizedBox(width: 9),
                      Expanded(
                        child: Text(_notice!,
                            style: const TextStyle(fontSize: 12.5, fontWeight: FontWeight.w600, height: 1.5)),
                      ),
                    ]),
                  ),
                Expanded(
                  child: messages.isEmpty
                      ? const Center(
                          child: Text('아직 메시지가 없어요. 먼저 인사를 건네 보세요!',
                              style: TextStyle(color: AppColors.muted, fontSize: 12.5, fontWeight: FontWeight.w600)),
                        )
                      : ListView(
                          controller: _scroll,
                          padding: const EdgeInsets.fromLTRB(16, 6, 16, 10),
                          children: [
                            if (_hasOlder)
                              Center(
                                child: TextButton(
                                  onPressed: _loadOlder,
                                  child: const Text('이전 메시지 더 보기',
                                      style: TextStyle(
                                          color: AppColors.muted, fontSize: 12, fontWeight: FontWeight.w700)),
                                ),
                              ),
                            for (final m in messages) _bubble(m),
                          ],
                        ),
                ),
                _composer(),
              ]),
      ),
    );
  }

  Widget _bubble(Map<String, dynamic> m) {
    // 강퇴 · 공지 같은 안내는 가운데 작은 알약으로
    if (m['type'] == 'SYSTEM') {
      return Padding(
        padding: const EdgeInsets.symmetric(vertical: 8),
        child: Center(
          child: Container(
            padding: const EdgeInsets.symmetric(horizontal: 12, vertical: 6),
            decoration: BoxDecoration(color: AppColors.surfaceAlt, borderRadius: BorderRadius.circular(999)),
            child: Text((m['content'] as String?) ?? '',
                textAlign: TextAlign.center,
                style: const TextStyle(color: AppColors.chipText, fontSize: 11, fontWeight: FontWeight.w700)),
          ),
        ),
      );
    }
    final mine = m['mine'] == true;
    final hidden = m['hidden'] == true;
    final at = DateTime.parse(m['createdAt'] as String);
    final content = hidden ? '(내보내진 참가자의 메시지예요)' : (m['content'] as String?);
    final time = Padding(
      padding: const EdgeInsets.symmetric(horizontal: 6),
      child: Text('${two(at.hour)}:${two(at.minute)}',
          style: const TextStyle(color: AppColors.navIdle, fontSize: 10, fontWeight: FontWeight.w600)),
    );
    final body = ConstrainedBox(
      constraints: BoxConstraints(maxWidth: MediaQuery.sizeOf(context).width * 0.66),
      child: Column(crossAxisAlignment: mine ? CrossAxisAlignment.end : CrossAxisAlignment.start, children: [
        if (m['hasImage'] == true) _photo((m['id'] as num).toInt()),
        if (content != null && content.isNotEmpty)
          Container(
            margin: EdgeInsets.only(top: m['hasImage'] == true ? 5 : 0),
            padding: const EdgeInsets.symmetric(horizontal: 13, vertical: 9),
            decoration: BoxDecoration(
              color: mine ? AppColors.text : AppColors.surface,
              borderRadius: BorderRadius.circular(16),
              boxShadow: mine ? null : cardShadow,
            ),
            child: Text(content,
                style: TextStyle(
                    fontSize: 13.5,
                    fontWeight: FontWeight.w600,
                    height: 1.45,
                    color: hidden ? AppColors.muted : (mine ? Colors.white : AppColors.text))),
          ),
      ]),
    );
    if (mine) {
      return Padding(
        padding: const EdgeInsets.symmetric(vertical: 4),
        child: Row(
            mainAxisAlignment: MainAxisAlignment.end,
            crossAxisAlignment: CrossAxisAlignment.end,
            children: [time, body]),
      );
    }
    final nickname = m['senderNickname'] as String;
    return GestureDetector(
      behavior: HitTestBehavior.opaque,
      onLongPress: hidden ? null : () => _openMenu(m),
      child: Padding(
        padding: const EdgeInsets.symmetric(vertical: 5),
        child: Row(crossAxisAlignment: CrossAxisAlignment.start, children: [
          GestureDetector(
            onTap: () => Navigator.of(context)
                .push(MaterialPageRoute(builder: (_) => ProfileScreen(userId: (m['senderId'] as num).toInt()))),
            child: UserAvatar(nickname: nickname, image: m['senderProfileImageUrl'] as String?, size: 34),
          ),
          const SizedBox(width: 8),
          Flexible(
            child: Column(crossAxisAlignment: CrossAxisAlignment.start, children: [
              Padding(
                padding: const EdgeInsets.only(left: 2, bottom: 4),
                child: Text(nickname,
                    style: const TextStyle(color: AppColors.brown, fontSize: 11.5, fontWeight: FontWeight.w700)),
              ),
              Row(mainAxisSize: MainAxisSize.min, crossAxisAlignment: CrossAxisAlignment.end, children: [
                Flexible(child: body),
                time,
              ]),
            ]),
          ),
        ]),
      ),
    );
  }

  /// 채팅 사진. 참가자만 받을 수 있어 토큰을 붙여 받는다. 누르면 크게 본다
  Widget _photo(int messageId) {
    final image = _images.putIfAbsent(messageId, () => _api.bytes('$_base/$messageId/image'));
    return GestureDetector(
      onTap: () => showDialog<void>(
        context: context,
        builder: (context) => GestureDetector(
          onTap: () => Navigator.pop(context),
          child: FutureBuilder<Uint8List>(
            future: image,
            builder: (context, snap) =>
                snap.hasData ? InteractiveViewer(child: Image.memory(snap.data!)) : const SizedBox.shrink(),
          ),
        ),
      ),
      child: ClipRRect(
        borderRadius: BorderRadius.circular(16),
        child: SizedBox(
          width: 190,
          height: 190,
          child: FutureBuilder<Uint8List>(
            future: image,
            builder: (context, snap) => snap.hasData
                ? Image.memory(snap.data!, fit: BoxFit.cover)
                : const ColoredBox(color: AppColors.surfaceAlt),
          ),
        ),
      ),
    );
  }

  Widget _composer() {
    return Container(
      padding: const EdgeInsets.fromLTRB(8, 8, 10, 10),
      decoration: const BoxDecoration(border: Border(top: BorderSide(color: AppColors.border))),
      child: Row(crossAxisAlignment: CrossAxisAlignment.end, children: [
        IconButton(
          tooltip: '사진 보내기',
          onPressed: _busy ? null : _sendPhoto,
          icon: const Icon(Icons.image_outlined, color: AppColors.brown),
        ),
        Expanded(
          child: TextField(
            controller: _input,
            minLines: 1,
            maxLines: 4,
            maxLength: 500,
            textInputAction: TextInputAction.send,
            onSubmitted: (_) => _send(),
            decoration: const InputDecoration(counterText: '', hintText: '메시지를 입력하세요'),
          ),
        ),
        const SizedBox(width: 6),
        IconButton(
          tooltip: '보내기',
          onPressed: _busy ? null : _send,
          style: IconButton.styleFrom(backgroundColor: AppColors.primary, foregroundColor: Colors.white),
          icon: const Icon(Icons.arrow_upward, size: 20),
        ),
      ]),
    );
  }
}
