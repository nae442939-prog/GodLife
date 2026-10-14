import 'dart:async';

import 'package:flutter/material.dart';
import 'package:provider/provider.dart';

import '../api/api_client.dart';
import '../api/models.dart';
import '../app_state.dart';
import '../theme.dart';
import '../widgets/async_view.dart';
import '../widgets/user_avatar.dart';
import 'challenge_detail_screen.dart';
import 'profile_screen.dart';

const _page = 50; // 서버가 한 번에 주는 메시지 수

/// 1:1 대화방 (웹의 /messages/:userId 와 같은 규칙).
/// 맞팔로우이거나 같은 챌린지 참가자면 바로 대화하고, 그 밖에는 메시지 요청으로 보내진다:
/// 상대가 수락하기 전에는 3개까지만 보낼 수 있고, 받은 쪽은 수락 · 거절하거나 답장(= 수락)한다.
/// 3초마다 새 메시지를 불러오고, 새 메시지가 오면 맨 아래로 내린다.
class MessageRoomScreen extends StatefulWidget {
  const MessageRoomScreen({super.key, required this.userId});

  final int userId;

  @override
  State<MessageRoomScreen> createState() => _MessageRoomScreenState();
}

class _MessageRoomScreenState extends State<MessageRoomScreen> {
  final _input = TextEditingController();
  final _scroll = ScrollController();

  Map<String, dynamic>? _room;
  List<Map<String, dynamic>> _messages = [];
  bool _hasOlder = false;
  bool _busy = false;
  Object? _error;
  Timer? _timer;

  ApiClient get _api => context.read<AppState>().api;
  String get _base => '/api/messages/${widget.userId}';

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
      final room = await _api.get('$_base/room') as Map<String, dynamic>;
      final list = ((await _api.get(_base)) as List).cast<Map<String, dynamic>>();
      if (!mounted) return;
      setState(() {
        _room = room;
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

  /// 새 메시지만 이어 받는다. 메시지 요청 중이면 수락 여부가 바뀔 수 있어 방 상태도 다시 본다
  Future<void> _poll() async {
    if (_room == null || _busy) return;
    try {
      final after = _messages.isEmpty ? null : _messages.last['id'];
      final fresh =
          ((await _api.get(after == null ? _base : '$_base?after=$after')) as List).cast<Map<String, dynamic>>();
      final room = _room!['direct'] == true ? null : await _api.get('$_base/room') as Map<String, dynamic>;
      if (!mounted) return;
      if (fresh.isNotEmpty || room != null) {
        setState(() {
          final known = {for (final m in _messages) m['id']};
          _messages = [..._messages, ...fresh.where((m) => !known.contains(m['id']))];
          if (room != null) _room = room;
        });
        if (fresh.isNotEmpty) _toBottom();
      }
    } catch (_) {
      // 잠깐 끊겨도 다음 차례에 다시 받는다
    }
  }

  Future<void> _loadOlder() async {
    if (_messages.isEmpty) return;
    try {
      final older =
          ((await _api.get('$_base?before=${_messages.first['id']}')) as List).cast<Map<String, dynamic>>();
      if (mounted) {
        setState(() {
          _messages = [...older, ..._messages];
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
      final room = await _api.get('$_base/room') as Map<String, dynamic>;
      if (!mounted) return;
      _input.clear();
      setState(() {
        if (!_messages.any((m) => m['id'] == sent['id'])) _messages = [..._messages, sent];
        _room = room;
      });
      _toBottom();
    } catch (e) {
      if (mounted) showMessage(context, '$e');
    } finally {
      if (mounted) setState(() => _busy = false);
    }
  }

  /// 대화 상대를 내 챌린지에 초대한다: 참여 중인 챌린지를 고르면 대화방에 초대 카드가 올라간다
  Future<void> _invite() async {
    final List<MyChallenge> mine;
    try {
      mine = ((await _api.get('/api/me/challenges')) as List)
          .map((e) => MyChallenge.fromJson(e as Map<String, dynamic>))
          .where((c) => c.joined || c.host)
          .toList();
    } catch (e) {
      if (mounted) showMessage(context, '$e');
      return;
    }
    if (!mounted) return;
    final picked = await showModalBottomSheet<MyChallenge>(
      context: context,
      backgroundColor: AppColors.bg,
      shape: const RoundedRectangleBorder(borderRadius: BorderRadius.vertical(top: Radius.circular(26))),
      builder: (context) => SafeArea(
        child: ListView(shrinkWrap: true, padding: const EdgeInsets.fromLTRB(8, 18, 8, 12), children: [
          const Padding(
            padding: EdgeInsets.fromLTRB(14, 0, 14, 8),
            child: Text('초대할 챌린지', style: TextStyle(fontSize: 17, fontWeight: FontWeight.w800, letterSpacing: -0.2)),
          ),
          if (mine.isEmpty)
            const Padding(
              padding: EdgeInsets.fromLTRB(14, 8, 14, 16),
              child: Text('참여 중인 챌린지가 없어요. 챌린지에 참여하거나 만들면 초대할 수 있어요.',
                  style: TextStyle(color: AppColors.muted, fontSize: 12.5, fontWeight: FontWeight.w600, height: 1.6)),
            ),
          for (final c in mine)
            ListTile(
              title: Text(c.title,
                  maxLines: 1,
                  overflow: TextOverflow.ellipsis,
                  style: const TextStyle(fontSize: 13.5, fontWeight: FontWeight.w800)),
              subtitle: Text('${c.startDate} ~ ${c.endDate}',
                  style: const TextStyle(color: AppColors.muted, fontSize: 11.5, fontWeight: FontWeight.w600)),
              trailing: const Icon(Icons.chevron_right, color: Color(0xFFC7C0B0), size: 20),
              onTap: () => Navigator.pop(context, c),
            ),
        ]),
      ),
    );
    if (picked == null || !mounted) return;
    setState(() => _busy = true);
    try {
      final sent = await _api.post('$_base/challenge-invite', body: {'challengeId': picked.id}) as Map<String, dynamic>;
      if (!mounted) return;
      setState(() {
        if (!_messages.any((m) => m['id'] == sent['id'])) _messages = [..._messages, sent];
      });
      _toBottom();
    } catch (e) {
      if (mounted) showMessage(context, '$e');
    } finally {
      if (mounted) setState(() => _busy = false);
    }
  }

  /// 받은 메시지 요청 수락 · 거절. 거절하면 대화 목록으로 돌아간다
  Future<void> _respond(bool accept) async {
    setState(() => _busy = true);
    try {
      await _api.post('$_base/${accept ? 'accept' : 'decline'}');
      if (!mounted) return;
      if (!accept) {
        Navigator.of(context).pop();
        return;
      }
      final room = await _api.get('$_base/room') as Map<String, dynamic>;
      if (mounted) setState(() => _room = room);
    } catch (e) {
      if (mounted) showMessage(context, '$e');
    } finally {
      if (mounted) setState(() => _busy = false);
    }
  }

  void _toBottom() {
    WidgetsBinding.instance.addPostFrameCallback((_) {
      if (_scroll.hasClients) _scroll.jumpTo(_scroll.position.maxScrollExtent);
    });
  }

  @override
  Widget build(BuildContext context) {
    final room = _room;
    final partner = room?['partner'] as Map<String, dynamic>?;
    return Scaffold(
      appBar: AppBar(
        titleSpacing: 0,
        title: partner == null
            ? const Text('메시지')
            : GestureDetector(
                onTap: () => Navigator.of(context)
                    .push(MaterialPageRoute(builder: (_) => ProfileScreen(userId: widget.userId))),
                child: Row(children: [
                  UserAvatar(
                      nickname: partner['nickname'] as String,
                      image: partner['profileImageUrl'] as String?,
                      size: 32),
                  const SizedBox(width: 10),
                  Flexible(child: Text(partner['nickname'] as String, overflow: TextOverflow.ellipsis)),
                ]),
              ),
        actions: [
          // 바로 대화하는 사이에게만 챌린지 초대를 보낼 수 있다 (웹과 같다)
          if (room != null && room['direct'] == true && room['canSend'] == true)
            IconButton(
                tooltip: '챌린지 초대', onPressed: _busy ? null : _invite, icon: const Icon(Icons.flag_outlined)),
          const SizedBox(width: 6),
        ],
      ),
      body: SafeArea(
        child: room == null
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
                ..._banners(room, partner!['nickname'] as String),
                Expanded(child: _list(room, partner['nickname'] as String)),
                _composer(room),
              ]),
      ),
    );
  }

  /// 대화방 위의 안내: 같은 챌린지라 바로 대화 / 받은 요청(수락 · 거절) / 보낸 요청(남은 개수)
  List<Widget> _banners(Map<String, dynamic> room, String nickname) {
    final direct = room['direct'] == true;
    final request = room['request'] as String?;
    final left = (room['requestLeft'] as num?)?.toInt() ?? 0;
    return [
      if (direct && room['mutual'] != true && room['sharedChallenge'] == true)
        _notice(body: '같은 챌린지 참가자라 맞팔로우가 아니어도 바로 대화할 수 있어요.'),
      if (!direct && request == 'RECEIVED')
        _notice(
          title: '$nickname님이 메시지 요청을 보냈어요',
          body: '수락하거나 답장하면 대화가 시작돼요. 수락하기 전에는 읽어도 상대에게 표시되지 않아요.',
          actions: Row(children: [
            Expanded(
              child: FilledButton(
                onPressed: _busy ? null : () => _respond(true),
                style: FilledButton.styleFrom(
                    backgroundColor: AppColors.text, minimumSize: const Size.fromHeight(42)),
                child: const Text('수락'),
              ),
            ),
            const SizedBox(width: 8),
            Expanded(
              child: OutlinedButton(
                onPressed: _busy ? null : () => _respond(false),
                style: OutlinedButton.styleFrom(
                  minimumSize: const Size.fromHeight(42),
                  backgroundColor: AppColors.surface,
                  side: const BorderSide(color: AppColors.border),
                  shape: RoundedRectangleBorder(borderRadius: BorderRadius.circular(999)),
                ),
                child: const Text('거절', style: TextStyle(color: AppColors.text, fontWeight: FontWeight.w800)),
              ),
            ),
          ]),
        ),
      if (!direct && (request == 'SENT' || left > 0) && request != 'RECEIVED')
        _notice(
          title: '맞팔로우도, 같은 챌린지 참가자도 아니라 메시지 요청으로 보내져요',
          body: '$nickname님이 수락하면 대화가 시작돼요. ${request != 'SENT' ? '수락 전에는 $left개까지만 보낼 수 있어요.' : left > 0 ? '수락 전에는 $left개 더 보낼 수 있어요.' : '지금은 수락을 기다리고 있어요.'}',
        ),
    ];
  }

  Widget _notice({String? title, required String body, Widget? actions}) {
    return Container(
      width: double.infinity,
      margin: const EdgeInsets.fromLTRB(16, 4, 16, 8),
      padding: const EdgeInsets.all(14),
      decoration: BoxDecoration(color: AppColors.surfaceAlt, borderRadius: BorderRadius.circular(16)),
      child: Column(crossAxisAlignment: CrossAxisAlignment.start, children: [
        if (title != null) ...[
          Text(title, style: const TextStyle(fontSize: 12.5, fontWeight: FontWeight.w800, height: 1.4)),
          const SizedBox(height: 4),
        ],
        Text(body,
            style: const TextStyle(
                fontSize: 12, fontWeight: FontWeight.w600, color: AppColors.brown, height: 1.5)),
        if (actions != null) ...[const SizedBox(height: 10), actions],
      ]),
    );
  }

  Widget _list(Map<String, dynamic> room, String nickname) {
    if (_messages.isEmpty) {
      return Center(
        child: Padding(
          padding: const EdgeInsets.symmetric(horizontal: 36),
          child: Text(room['canSend'] == true ? '$nickname님에게 첫 메시지를 보내 보세요.\n같이 할 챌린지 얘기도 좋아요!' : '',
              textAlign: TextAlign.center,
              style: const TextStyle(
                  color: AppColors.muted, fontSize: 12.5, fontWeight: FontWeight.w600, height: 1.6)),
        ),
      );
    }
    // 내가 보낸 마지막 메시지에만 '읽음'을 붙인다
    final lastMine = _messages.lastWhere((m) => m['mine'] == true, orElse: () => const {});
    return ListView(
      controller: _scroll,
      padding: const EdgeInsets.fromLTRB(16, 6, 16, 10),
      children: [
        if (_hasOlder)
          Center(
            child: TextButton(
              onPressed: _loadOlder,
              child: const Text('이전 메시지 더 보기',
                  style: TextStyle(color: AppColors.muted, fontSize: 12, fontWeight: FontWeight.w700)),
            ),
          ),
        for (final m in _messages) _bubble(m, read: m['id'] == lastMine['id'] && m['read'] == true),
      ],
    );
  }

  Widget _bubble(Map<String, dynamic> m, {required bool read}) {
    final mine = m['mine'] == true;
    final at = DateTime.parse(m['createdAt'] as String);
    final invite = m['invite'] as Map<String, dynamic>?;
    final meta = Padding(
      padding: const EdgeInsets.symmetric(horizontal: 6),
      child: Text('${read ? '읽음 · ' : ''}${two(at.hour)}:${two(at.minute)}',
          style: const TextStyle(color: AppColors.navIdle, fontSize: 10, fontWeight: FontWeight.w600)),
    );
    final body = ConstrainedBox(
      constraints: BoxConstraints(maxWidth: MediaQuery.sizeOf(context).width * 0.7),
      child: invite != null
          ? _inviteCard(invite, mine)
          : Container(
              padding: const EdgeInsets.symmetric(horizontal: 13, vertical: 9),
              decoration: BoxDecoration(
                color: mine ? AppColors.text : AppColors.surface,
                borderRadius: BorderRadius.circular(16),
                boxShadow: mine ? null : cardShadow,
              ),
              child: Text((m['content'] as String?) ?? '',
                  style: TextStyle(
                      fontSize: 13.5,
                      fontWeight: FontWeight.w600,
                      height: 1.45,
                      color: mine ? Colors.white : AppColors.text)),
            ),
    );
    return Padding(
      padding: const EdgeInsets.symmetric(vertical: 4),
      child: Row(
        mainAxisAlignment: mine ? MainAxisAlignment.end : MainAxisAlignment.start,
        crossAxisAlignment: CrossAxisAlignment.end,
        children: mine ? [meta, body] : [body, meta],
      ),
    );
  }

  /// 챌린지 초대 카드
  Widget _inviteCard(Map<String, dynamic> invite, bool mine) {
    final open = invite['open'] == true;
    final bet = invite['mode'] == 'BET';
    return Container(
      padding: const EdgeInsets.all(14),
      decoration: BoxDecoration(
        color: AppColors.surface,
        borderRadius: BorderRadius.circular(16),
        boxShadow: cardShadow,
      ),
      child: Column(crossAxisAlignment: CrossAxisAlignment.start, mainAxisSize: MainAxisSize.min, children: [
        Text(mine ? '챌린지에 초대했어요' : '챌린지 초대가 왔어요',
            style: const TextStyle(color: AppColors.muted, fontSize: 11, fontWeight: FontWeight.w700)),
        const SizedBox(height: 5),
        Text(invite['title'] as String, style: const TextStyle(fontSize: 13.5, fontWeight: FontWeight.w800)),
        const SizedBox(height: 4),
        Text(
            '${invite['categoryName']} · ${bet ? '포인트 ${points((invite['entryFee'] as num).toInt())}P' : '무료'}\n${invite['startDate']} ~ ${invite['endDate']}',
            style: const TextStyle(
                color: AppColors.brown, fontSize: 11.5, fontWeight: FontWeight.w600, height: 1.5)),
        const SizedBox(height: 10),
        if (mine || open)
          OutlinedButton(
            // 받은 초대는 초대 코드로 연다 (비공개 챌린지도 보고 참여할 수 있다)
            onPressed: () => Navigator.of(context).push(MaterialPageRoute(
                builder: (_) => ChallengeDetailScreen(
                    id: (invite['challengeId'] as num).toInt(),
                    inviteCode: mine ? null : invite['inviteCode'] as String?))),
            style: OutlinedButton.styleFrom(
              minimumSize: const Size.fromHeight(38),
              side: const BorderSide(color: AppColors.text),
              shape: RoundedRectangleBorder(borderRadius: BorderRadius.circular(999)),
            ),
            child: Text(mine ? '챌린지 보기' : '보고 참여하기',
                style: const TextStyle(color: AppColors.text, fontWeight: FontWeight.w800)),
          )
        else
          const Text('모집이 끝난 챌린지예요',
              style: TextStyle(color: AppColors.muted, fontSize: 11.5, fontWeight: FontWeight.w700)),
      ]),
    );
  }

  /// 아래 입력 칸. 보낼 수 없으면 이유를 대신 보여 준다
  Widget _composer(Map<String, dynamic> room) {
    if (room['canSend'] != true) {
      final text = switch (room['reason']) {
        'BLOCKED' => '메시지를 보낼 수 없는 회원이에요.',
        'REQUEST_LIMIT' => '상대가 메시지 요청을 수락하면 이어서 보낼 수 있어요.',
        _ => '지금은 메시지를 보낼 수 없어요.',
      };
      return Container(
        width: double.infinity,
        padding: const EdgeInsets.fromLTRB(20, 14, 20, 16),
        decoration: const BoxDecoration(border: Border(top: BorderSide(color: AppColors.border))),
        child: Text(text,
            textAlign: TextAlign.center,
            style: const TextStyle(color: AppColors.muted, fontSize: 12.5, fontWeight: FontWeight.w700)),
      );
    }
    return Container(
      padding: const EdgeInsets.fromLTRB(16, 8, 10, 10),
      decoration: const BoxDecoration(border: Border(top: BorderSide(color: AppColors.border))),
      child: Row(crossAxisAlignment: CrossAxisAlignment.end, children: [
        Expanded(
          child: TextField(
            controller: _input,
            minLines: 1,
            maxLines: 4,
            maxLength: 500,
            textInputAction: TextInputAction.send,
            onSubmitted: (_) => _send(),
            decoration: InputDecoration(
              counterText: '',
              hintText: room['request'] == 'RECEIVED' ? '답장하면 요청을 수락해요' : '메시지를 입력하세요',
            ),
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
