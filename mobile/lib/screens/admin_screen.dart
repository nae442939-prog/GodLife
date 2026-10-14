import 'dart:typed_data';

import 'package:flutter/material.dart';
import 'package:provider/provider.dart';

import '../api/api_client.dart';
import '../app_state.dart';
import '../theme.dart';
import '../widgets/async_view.dart';
import '../widgets/design.dart';
import 'challenge_detail_screen.dart';
import 'post_detail_screen.dart';
import 'profile_screen.dart';

/// (탭 이름, 안내 문구)
const _sections = [
  ('인증 검토', 'AI 가 판단하기 애매했거나 참가자가 신고한 인증 사진을 직접 보고 승인하거나 거절해요.'),
  ('담합 의심', '같은 두 사람이 작은 포인트 챌린지에서 여러 번 함께하고, 늘 한쪽만 실패한 조합이에요.'),
  ('다중 계정', '같은 기기를 쓴 계정이 3개 이상이거나 같은 IP 를 쓴 계정이 5개 이상인 묶음이에요. 표시만 하고 계정을 막지는 않아요.'),
  ('커뮤니티 신고', '신고된 글과 댓글이에요. 가리면 커뮤니티에서 보이지 않게 돼요.'),
  ('1:1 문의', '회원이 고객센터에 남긴 문의에 답변해요.'),
];

/// 관리자 화면 (웹의 /admin/* 과 같다): 인증 검토 · 담합 의심 · 다중 계정 · 커뮤니티 신고 · 1:1 문의.
/// 관리자(role = ADMIN)만 마이페이지 메뉴에서 들어온다. 포인트 상점 관리는 웹에서 연다.
class AdminScreen extends StatefulWidget {
  const AdminScreen({super.key});

  @override
  State<AdminScreen> createState() => _AdminScreenState();
}

class _AdminScreenState extends State<AdminScreen> {
  int _section = 0;

  @override
  Widget build(BuildContext context) {
    return Scaffold(
      appBar: AppBar(title: const Text('관리자')),
      body: SafeArea(
        child: Column(crossAxisAlignment: CrossAxisAlignment.start, children: [
          SingleChildScrollView(
            scrollDirection: Axis.horizontal,
            padding: const EdgeInsets.fromLTRB(16, 4, 16, 10),
            child: Row(children: [
              for (final (i, s) in _sections.indexed)
                Padding(
                  padding: const EdgeInsets.symmetric(horizontal: 4),
                  child: PillChip(s.$1, selected: _section == i, onTap: () => setState(() => _section = i)),
                ),
              Padding(
                padding: const EdgeInsets.symmetric(horizontal: 4),
                child: PillChip('포인트 상점 (웹)', selected: false, onTap: () => openWeb('/admin/shop')),
              ),
            ]),
          ),
          Padding(
            padding: const EdgeInsets.fromLTRB(22, 0, 22, 10),
            child: Text(_sections[_section].$2,
                style: const TextStyle(color: AppColors.muted, fontSize: 12, fontWeight: FontWeight.w600, height: 1.5)),
          ),
          Expanded(
            child: switch (_section) {
              0 => const _Reviews(),
              1 => const _Collusion(),
              2 => const _Devices(),
              3 => const _CommunityReports(),
              _ => const _Inquiries(),
            },
          ),
        ]),
      ),
    );
  }
}

// ── 같이 쓰는 조각 ─────────────────────────────────────────────

const _warn = (Color(0xFFF6E2DC), Color(0xFFC2574A));

void _open(BuildContext context, Widget page) =>
    Navigator.of(context).push(MaterialPageRoute(builder: (_) => page));

String _day(Object? value) {
  final text = '${value ?? ''}';
  return text.length >= 10 ? text.substring(0, 10) : text;
}

List<Map<String, dynamic>> _list(Object? data) => ((data as List?) ?? []).cast<Map<String, dynamic>>();

/// 상태 고르기 칩 한 줄
Widget _filters(List<(String, String)> filters, String selected, void Function(String) onSelect) {
  return SingleChildScrollView(
    scrollDirection: Axis.horizontal,
    padding: const EdgeInsets.fromLTRB(16, 0, 16, 10),
    child: Row(children: [
      for (final (value, label) in filters)
        Padding(
          padding: const EdgeInsets.symmetric(horizontal: 4),
          child: PillChip(label, selected: selected == value, onTap: () => onSelect(value)),
        ),
    ]),
  );
}

/// 목록이 비었을 때도 아래로 당겨 새로 고칠 수 있게 스크롤 안에 넣는다
Widget _empty(String text) {
  return ListView(padding: const EdgeInsets.fromLTRB(22, 30, 22, 28), children: [
    Text(text,
        textAlign: TextAlign.center,
        style: const TextStyle(color: AppColors.muted, fontSize: 12.5, fontWeight: FontWeight.w600)),
  ]);
}

/// 한 건의 카드: 상태 꼬리표 → 제목 → 작은 설명 → 내용
Widget _card({
  required String tag,
  required (Color, Color) tagColors,
  required Widget title,
  required String sub,
  List<Widget> children = const [],
}) {
  return Padding(
    padding: const EdgeInsets.only(bottom: 12),
    child: AppCard(
      bordered: false,
      padding: const EdgeInsets.all(16),
      child: Column(crossAxisAlignment: CrossAxisAlignment.start, children: [
        Align(alignment: Alignment.centerLeft, child: ColorTag.of(tag, tagColors)),
        const SizedBox(height: 9),
        title,
        const SizedBox(height: 3),
        Text(sub, style: const TextStyle(color: AppColors.muted, fontSize: 11.5, fontWeight: FontWeight.w600)),
        ...children,
      ]),
    ),
  );
}

const _titleStyle = TextStyle(fontSize: 14.5, fontWeight: FontWeight.w800, letterSpacing: -0.2, height: 1.35);

/// 눌러서 다른 화면으로 가는 제목
Widget _link(String text, VoidCallback onTap) {
  return GestureDetector(onTap: onTap, child: Text(text, style: _titleStyle));
}

/// '이름  값' 한 줄
Widget _fact(String name, String value) {
  return Padding(
    padding: const EdgeInsets.only(top: 7),
    child: Row(crossAxisAlignment: CrossAxisAlignment.start, children: [
      SizedBox(
        width: 118,
        child: Text(name, style: const TextStyle(color: AppColors.muted, fontSize: 12, fontWeight: FontWeight.w700)),
      ),
      Expanded(child: Text(value, style: const TextStyle(fontSize: 12.5, fontWeight: FontWeight.w700, height: 1.45))),
    ]),
  );
}

/// 아래쪽 버튼 두 개 (왼쪽: 테두리, 오른쪽: 주요)
Widget _actions({
  required String secondary,
  required String primary,
  required bool busy,
  required VoidCallback onSecondary,
  required VoidCallback onPrimary,
}) {
  return Padding(
    padding: const EdgeInsets.only(top: 14),
    child: Row(children: [
      Expanded(
        child: OutlinedButton(
          style: OutlinedButton.styleFrom(
            minimumSize: const Size(0, 44),
            foregroundColor: AppColors.text,
            side: const BorderSide(color: AppColors.cardBorder),
            shape: RoundedRectangleBorder(borderRadius: BorderRadius.circular(999)),
            textStyle: const TextStyle(fontFamily: 'NotoSansKR', fontSize: 13.5, fontWeight: FontWeight.w800),
          ),
          onPressed: busy ? null : onSecondary,
          child: Text(secondary),
        ),
      ),
      const SizedBox(width: 8),
      Expanded(
        child: FilledButton(
          style: FilledButton.styleFrom(
            minimumSize: const Size(0, 44),
            textStyle: const TextStyle(fontFamily: 'NotoSansKR', fontSize: 13.5, fontWeight: FontWeight.w800),
          ),
          onPressed: busy ? null : onPrimary,
          child: Text(busy ? '처리하는 중…' : primary),
        ),
      ),
    ]),
  );
}

// ── 인증 검토 ─────────────────────────────────────────────────

const _reviewReason = {
  'LOW_CONFIDENCE': 'AI 가 확신하지 못함',
  'DUPLICATE_SUSPECT': '예전 사진과 거의 같음',
  'REPORTED': '신고됨',
};
const _reviewStatus = {'OPEN': '검토 대기', 'APPROVED': '승인', 'REJECTED': '거절'};

/// AI 모델의 라벨 (categories.ai_label · category_sub_types.ai_label)
const _aiLabel = {
  'exercise': '운동',
  'study': '공부',
  'reading': '독서',
  'cooking': '요리',
  'wake_up': '일찍 일어나기',
  'walk': '산책',
  'water': '물 마시기',
  'clean': '청소 · 정리',
  'plant': '식물 가꾸기',
  'other': '기타',
};

String? _percent(Object? value) {
  final n = value == null ? null : num.tryParse('$value');
  return n == null ? null : '${(n * 100).round()}%';
}

/// 검토 중인 인증은 일단 인정된 상태다. 승인하면 그대로 인정되고, 거절하면 그 인증이 취소되고 회원에게 알림이 간다.
class _Reviews extends StatefulWidget {
  const _Reviews();

  @override
  State<_Reviews> createState() => _ReviewsState();
}

class _ReviewsState extends State<_Reviews> {
  String _filter = 'OPEN';

  @override
  Widget build(BuildContext context) {
    final api = context.read<AppState>().api;
    return Column(crossAxisAlignment: CrossAxisAlignment.start, children: [
      _filters(const [('OPEN', '검토 대기'), ('APPROVED', '승인'), ('REJECTED', '거절'), ('', '전체')], _filter,
          (v) => setState(() => _filter = v)),
      Expanded(
        child: AsyncView<List<Map<String, dynamic>>>(
          key: ValueKey(_filter),
          load: () async => _list(await api.get('/api/admin/reviews${_filter.isEmpty ? '' : '?status=$_filter'}')),
          builder: (context, items, reload) => items.isEmpty
              ? _empty(_filter == 'OPEN' ? '검토를 기다리는 인증이 없어요.' : '검토한 인증이 없어요.')
              : ListView(padding: const EdgeInsets.fromLTRB(20, 0, 20, 28), children: [
                  for (final r in items) _ReviewCard(key: ValueKey(r['id']), review: r, api: api, reload: reload),
                ]),
        ),
      ),
    ]);
  }
}

class _ReviewCard extends StatefulWidget {
  const _ReviewCard({super.key, required this.review, required this.api, required this.reload});

  final Map<String, dynamic> review;
  final ApiClient api;
  final Future<void> Function() reload;

  @override
  State<_ReviewCard> createState() => _ReviewCardState();
}

class _ReviewCardState extends State<_ReviewCard> {
  late final _memo = TextEditingController(text: widget.review['memo'] as String? ?? '');
  // 관리자만 받을 수 있는 사진이라 토큰을 붙여 받는다
  late final Future<Uint8List> _photo = widget.api.bytes('/api/admin/reviews/images/${widget.review['verificationId']}');
  late final Future<Uint8List>? _similar = _showsSimilar
      ? widget.api.bytes('/api/admin/reviews/images/${widget.review['duplicateOfId']}')
      : null;
  bool _busy = false;

  bool get _showsSimilar =>
      widget.review['reason'] == 'DUPLICATE_SUSPECT' && widget.review['duplicateOfId'] != null;

  @override
  void dispose() {
    _memo.dispose();
    super.dispose();
  }

  Future<void> _decide(bool approve) async {
    if (!approve) {
      // 거절은 한 번 더 묻는다
      final ok = await showDialog<bool>(
        context: context,
        builder: (context) => AlertDialog(
          title: const Text('이 인증을 취소할까요?'),
          content: const Text('거절하면 인증이 취소되고 회원에게 알림이 가요.'),
          actions: [
            TextButton(onPressed: () => Navigator.of(context).pop(false), child: const Text('아니요')),
            TextButton(onPressed: () => Navigator.of(context).pop(true), child: const Text('거절하기')),
          ],
        ),
      );
      if (ok != true) return;
    }
    setState(() => _busy = true);
    try {
      await widget.api.post('/api/admin/reviews/${widget.review['id']}/${approve ? 'approve' : 'reject'}',
          body: {'memo': _memo.text.trim()});
      if (mounted) showMessage(context, approve ? '승인했어요.' : '거절했어요.');
      await widget.reload();
    } catch (e) {
      if (mounted) showMessage(context, '$e');
    } finally {
      if (mounted) setState(() => _busy = false);
    }
  }

  @override
  Widget build(BuildContext context) {
    final r = widget.review;
    final status = r['status'] as String;
    final open = status == 'OPEN';
    final predicted = r['predictedLabel'] as String?;
    final expected = r['expectedLabel'] as String?;
    final similarity = _percent(r['maxSimilarity']);
    final reports = _list(r['reports']);
    final memo = r['memo'] as String?;
    final similar = _similar;

    return _card(
      tag: _reviewStatus[status] ?? status,
      tagColors: status == 'APPROVED' ? ColorTag.mint : (status == 'REJECTED' ? _warn : ColorTag.gold),
      title: _link(r['challengeTitle'] as String,
          () => _open(context, ChallengeDetailScreen(id: (r['challengeId'] as num).toInt()))),
      sub: '${r['categoryName']} · ${r['nickname']} · ${r['verifyDate']}',
      children: [
        const SizedBox(height: 12),
        Row(crossAxisAlignment: CrossAxisAlignment.start, children: [
          Expanded(child: _photoBox(_photo, '이번 인증 사진')),
          if (similar != null) ...[
            const SizedBox(width: 8),
            Expanded(child: _photoBox(similar, '가장 닮은 예전 사진')),
          ] else
            const Spacer(),
        ]),
        const SizedBox(height: 6),
        _fact('넘어온 이유', _reviewReason[r['reason']] ?? '${r['reason']}'),
        if (predicted != null)
          _fact(
              'AI 가 본 것',
              '${_aiLabel[predicted] ?? predicted} (${_percent(r['confidence']) ?? '-'})'
                  '${predicted != expected && expected != 'other' ? ' · 챌린지는 ${r['categoryName']}' : ''}'),
        if (similarity != null) _fact('예전 사진과 닮은 정도', similarity),
        if (reports.isNotEmpty)
          _fact('들어온 신고 ${reports.length}건', [for (final p in reports) '${p['nickname']}: ${p['reason']}'].join('\n')),
        if (!open && memo != null && memo.isNotEmpty) _fact('메모', memo),
        GestureDetector(
          onTap: () => _open(context, ProfileScreen(userId: (r['userId'] as num).toInt())),
          child: Padding(
            padding: const EdgeInsets.only(top: 10),
            child: Text('${r['nickname']} 프로필 보기',
                style: const TextStyle(color: AppColors.brown, fontSize: 12, fontWeight: FontWeight.w800)),
          ),
        ),
        if (open) ...[
          const SizedBox(height: 12),
          TextField(
            controller: _memo,
            maxLength: 100,
            style: const TextStyle(fontSize: 13),
            decoration: const InputDecoration(
              labelText: '메모 (거절하면 회원의 거절 이유로 남아요)',
              hintText: '예: 운동 사진이 아니에요',
              counterText: '',
              isDense: true,
            ),
          ),
          _actions(
            secondary: '거절',
            primary: '승인',
            busy: _busy,
            onSecondary: () => _decide(false),
            onPrimary: () => _decide(true),
          ),
        ],
      ],
    );
  }

  /// 사진 한 장 (누르면 크게 본다)
  Widget _photoBox(Future<Uint8List> image, String caption) {
    return Column(crossAxisAlignment: CrossAxisAlignment.start, children: [
      FutureBuilder<Uint8List>(
        future: image,
        builder: (context, snap) => GestureDetector(
          onTap: snap.hasData
              ? () => showDialog<void>(
                    context: context,
                    builder: (context) => Dialog(
                      clipBehavior: Clip.antiAlias,
                      child: InteractiveViewer(child: Image.memory(snap.data!, fit: BoxFit.contain)),
                    ),
                  )
              : null,
          child: ClipRRect(
            borderRadius: BorderRadius.circular(14),
            child: AspectRatio(
              aspectRatio: 1,
              child: snap.hasData
                  ? Image.memory(snap.data!, fit: BoxFit.cover)
                  : ColoredBox(
                      color: AppColors.surfaceAlt,
                      child: snap.hasError
                          ? const Icon(Icons.image_not_supported_outlined, color: AppColors.navIdle)
                          : null,
                    ),
            ),
          ),
        ),
      ),
      const SizedBox(height: 5),
      Text(caption, style: const TextStyle(color: AppColors.muted, fontSize: 11, fontWeight: FontWeight.w700)),
    ]);
  }
}

// ── 담합 의심 ─────────────────────────────────────────────────

const _collusionStatus = {'OPEN': '확인 전', 'CONFIRMED': '담합으로 봄', 'DISMISSED': '문제없음'};

/// 서버가 자정마다 찾아 올린 조합. 여기서는 판단만 기록한다 (포인트를 되돌리거나 계정을 막지 않는다).
class _Collusion extends StatefulWidget {
  const _Collusion();

  @override
  State<_Collusion> createState() => _CollusionState();
}

class _CollusionState extends State<_Collusion> {
  String _filter = 'OPEN';
  int? _busy;

  Future<void> _decide(ApiClient api, int id, bool confirm, Future<void> Function() reload) async {
    setState(() => _busy = id);
    try {
      await api.post('/api/admin/collusion/$id/${confirm ? 'confirm' : 'dismiss'}');
      await reload();
    } catch (e) {
      if (mounted) showMessage(context, '$e');
    } finally {
      if (mounted) setState(() => _busy = null);
    }
  }

  @override
  Widget build(BuildContext context) {
    final api = context.read<AppState>().api;
    return Column(crossAxisAlignment: CrossAxisAlignment.start, children: [
      _filters(const [('OPEN', '확인 전'), ('CONFIRMED', '담합으로 봄'), ('DISMISSED', '문제없음'), ('', '전체')], _filter,
          (v) => setState(() => _filter = v)),
      Expanded(
        child: AsyncView<List<Map<String, dynamic>>>(
          key: ValueKey(_filter),
          load: () async => _list(await api.get('/api/admin/collusion${_filter.isEmpty ? '' : '?status=$_filter'}')),
          builder: (context, items, reload) => items.isEmpty
              ? _empty(_filter == 'OPEN' ? '확인할 조합이 없어요.' : '기록이 없어요.')
              : ListView(padding: const EdgeInsets.fromLTRB(20, 0, 20, 28), children: [
                  for (final f in items) _row(context, api, f, reload),
                ]),
        ),
      ),
    ]);
  }

  Widget _row(BuildContext context, ApiClient api, Map<String, dynamic> f, Future<void> Function() reload) {
    final id = (f['id'] as num).toInt();
    final status = f['status'] as String;
    return _card(
      tag: _collusionStatus[status] ?? status,
      tagColors: status == 'DISMISSED' ? ColorTag.mint : (status == 'CONFIRMED' ? _warn : ColorTag.gold),
      title: Wrap(children: [
        _link('${f['userANickname']}', () => _open(context, ProfileScreen(userId: (f['userAId'] as num).toInt()))),
        const Text(' · ', style: _titleStyle),
        _link('${f['userBNickname']}', () => _open(context, ProfileScreen(userId: (f['userBId'] as num).toInt()))),
      ]),
      sub: '${_day(f['detectedAt'])} 발견',
      children: [
        const SizedBox(height: 5),
        _fact('함께한 포인트 챌린지', '${f['coMatchCount']}번'),
        _fact('한쪽만 실패한 비율', '${(num.tryParse('${f['score']}') ?? 0).round()}%'),
        GestureDetector(
          onTap: () => _open(context, ChallengeDetailScreen(id: (f['challengeId'] as num).toInt())),
          child: _fact('최근에 함께한 챌린지', '${f['challengeTitle']}'),
        ),
        if (status == 'OPEN')
          _actions(
            secondary: '문제없음',
            primary: '담합으로 기록',
            busy: _busy == id,
            onSecondary: () => _decide(api, id, false, reload),
            onPrimary: () => _decide(api, id, true, reload),
          ),
      ],
    );
  }
}

// ── 다중 계정 의심 ─────────────────────────────────────────────

const _memberStatus = {'SUSPENDED': '정지', 'WITHDRAWN': '탈퇴'};

/// 같은 기종끼리 지문이 겹치거나 가족 · 학교가 IP 를 같이 쓸 수 있어서 보여 주기만 한다.
class _Devices extends StatelessWidget {
  const _Devices();

  @override
  Widget build(BuildContext context) {
    final api = context.read<AppState>().api;
    return AsyncView<List<Map<String, dynamic>>>(
      load: () async => _list(await api.get('/api/admin/devices')),
      builder: (context, groups, reload) => groups.isEmpty
          ? _empty('의심되는 묶음이 없어요.')
          : ListView(padding: const EdgeInsets.fromLTRB(20, 0, 20, 28), children: [
              for (final g in groups)
                _card(
                  tag: g['kind'] == 'DEVICE' ? '같은 기기' : '같은 IP',
                  tagColors: g['kind'] == 'DEVICE' ? ColorTag.lav : ColorTag.gold,
                  title: Text('계정 ${g['accounts']}개', style: _titleStyle),
                  sub: '${g['kind'] == 'DEVICE' ? '기기 ${g['key']}…' : g['key']} · 마지막 접속 ${_day(g['lastSeenAt'])}',
                  children: [
                    const SizedBox(height: 6),
                    for (final m in _list(g['members'])) _member(context, m),
                  ],
                ),
            ]),
    );
  }

  Widget _member(BuildContext context, Map<String, dynamic> m) {
    final status = _memberStatus[m['status']];
    return GestureDetector(
      behavior: HitTestBehavior.opaque,
      onTap: () => _open(context, ProfileScreen(userId: (m['userId'] as num).toInt())),
      child: Container(
        padding: const EdgeInsets.symmetric(vertical: 9),
        decoration: const BoxDecoration(border: Border(top: BorderSide(color: Color(0xFFF5F2EA)))),
        child: Row(children: [
          Expanded(
            child: Column(crossAxisAlignment: CrossAxisAlignment.start, children: [
              Text('${m['nickname']}', style: const TextStyle(fontSize: 13, fontWeight: FontWeight.w800)),
              const SizedBox(height: 2),
              Text('${m['email']} · 가입 ${_day(m['joinedAt'])} · 접속 ${_day(m['lastSeenAt'])}',
                  style: const TextStyle(color: AppColors.muted, fontSize: 11, fontWeight: FontWeight.w600)),
            ]),
          ),
          if (status != null) ...[const SizedBox(width: 8), ColorTag.of(status, _warn)],
        ]),
      ),
    );
  }
}

// ── 커뮤니티 신고 ──────────────────────────────────────────────

/// 같은 글 · 댓글에 온 신고는 한 줄로 묶여 온다. 가리면 신고한 사람과 쓴 사람에게 알림이 간다.
class _CommunityReports extends StatefulWidget {
  const _CommunityReports();

  @override
  State<_CommunityReports> createState() => _CommunityReportsState();
}

class _CommunityReportsState extends State<_CommunityReports> {
  String? _busy;

  Future<void> _handle(ApiClient api, Map<String, dynamic> r, String action, Future<void> Function() reload) async {
    final key = '${r['targetType']}:${r['targetId']}';
    setState(() => _busy = key);
    try {
      await api.post('/api/admin/community-reports/${r['targetType']}/${r['targetId']}/$action');
      await reload();
    } catch (e) {
      if (mounted) showMessage(context, '$e');
    } finally {
      if (mounted) setState(() => _busy = null);
    }
  }

  @override
  Widget build(BuildContext context) {
    final api = context.read<AppState>().api;
    return AsyncView<List<Map<String, dynamic>>>(
      load: () async => _list(await api.get('/api/admin/community-reports')),
      builder: (context, items, reload) => items.isEmpty
          ? _empty('확인할 신고가 없어요.')
          : ListView(padding: const EdgeInsets.fromLTRB(20, 0, 20, 28), children: [
              for (final r in items) _row(context, api, r, reload),
            ]),
    );
  }

  Widget _row(BuildContext context, ApiClient api, Map<String, dynamic> r, Future<void> Function() reload) {
    final type = r['targetType'] == 'POST' ? '글' : '댓글';
    final postId = (r['postId'] as num?)?.toInt();
    final reasons = ((r['reasons'] as List?) ?? []).map((e) => '· $e').join('\n');
    return _card(
      tag: '$type · 신고 ${r['reportCount']}건',
      tagColors: _warn,
      title: postId == null
          ? const Text('(지워진 글)', style: _titleStyle)
          : _link('${r['title']}', () => _open(context, PostDetailScreen(id: postId))),
      sub: '${r['authorNickname']} · ${_day(r['firstReportedAt'])} 첫 신고',
      children: [
        const SizedBox(height: 10),
        Text('${r['content'] ?? ''}', style: const TextStyle(fontSize: 13, fontWeight: FontWeight.w500, height: 1.55)),
        if (reasons.isNotEmpty) ...[
          const SizedBox(height: 10),
          Container(
            width: double.infinity,
            padding: const EdgeInsets.all(12),
            decoration: BoxDecoration(color: AppColors.surfaceAlt, borderRadius: BorderRadius.circular(12)),
            child: Text(reasons,
                style: const TextStyle(color: AppColors.brown, fontSize: 12, fontWeight: FontWeight.w600, height: 1.6)),
          ),
        ],
        _actions(
          secondary: '문제없음',
          primary: '$type 가리기',
          busy: _busy == '${r['targetType']}:${r['targetId']}',
          onSecondary: () => _handle(api, r, 'dismiss', reload),
          onPrimary: () => _handle(api, r, 'hide', reload),
        ),
      ],
    );
  }
}

// ── 1:1 문의 ──────────────────────────────────────────────────

const _inquiryCategory = {
  'ACCOUNT': '계정',
  'CHALLENGE': '챌린지 · 인증',
  'POINT': '포인트',
  'BUG': '오류 신고',
  'ETC': '기타',
};

/// 답변 대기 문의가 오래된 것부터 나오고, 답변을 달면 회원의 [설정 → 고객센터 → 내 문의 내역]에 보인다.
class _Inquiries extends StatefulWidget {
  const _Inquiries();

  @override
  State<_Inquiries> createState() => _InquiriesState();
}

class _InquiriesState extends State<_Inquiries> {
  String _filter = 'WAITING';

  @override
  Widget build(BuildContext context) {
    final api = context.read<AppState>().api;
    return Column(crossAxisAlignment: CrossAxisAlignment.start, children: [
      _filters(const [('WAITING', '답변 대기'), ('ANSWERED', '답변 완료'), ('', '전체')], _filter,
          (v) => setState(() => _filter = v)),
      Expanded(
        child: AsyncView<List<Map<String, dynamic>>>(
          key: ValueKey(_filter),
          load: () async => _list(await api.get('/api/admin/inquiries${_filter.isEmpty ? '' : '?status=$_filter'}')),
          builder: (context, items, reload) => items.isEmpty
              ? _empty(_filter == 'WAITING' ? '답변을 기다리는 문의가 없어요.' : '문의가 없어요.')
              : ListView(padding: const EdgeInsets.fromLTRB(20, 0, 20, 28), children: [
                  for (final x in items)
                    _InquiryCard(
                        key: ValueKey((x['inquiry'] as Map<String, dynamic>)['id']), item: x, api: api, reload: reload),
                ]),
        ),
      ),
    ]);
  }
}

class _InquiryCard extends StatefulWidget {
  const _InquiryCard({super.key, required this.item, required this.api, required this.reload});

  final Map<String, dynamic> item;
  final ApiClient api;
  final Future<void> Function() reload;

  @override
  State<_InquiryCard> createState() => _InquiryCardState();
}

class _InquiryCardState extends State<_InquiryCard> {
  late final _answer = TextEditingController(text: _inquiry['answer'] as String? ?? '');
  bool _busy = false;

  Map<String, dynamic> get _inquiry => widget.item['inquiry'] as Map<String, dynamic>;

  @override
  void dispose() {
    _answer.dispose();
    super.dispose();
  }

  Future<void> _submit() async {
    final answer = _answer.text.trim();
    if (answer.isEmpty) {
      showMessage(context, '답변을 입력해 주세요.');
      return;
    }
    setState(() => _busy = true);
    try {
      await widget.api.post('/api/admin/inquiries/${_inquiry['id']}/answer', body: {'answer': answer});
      if (mounted) showMessage(context, '답변을 저장했어요.');
      await widget.reload();
    } catch (e) {
      if (mounted) showMessage(context, '$e');
    } finally {
      if (mounted) setState(() => _busy = false);
    }
  }

  @override
  Widget build(BuildContext context) {
    final q = _inquiry;
    final answered = q['status'] == 'ANSWERED';
    final created = '${q['createdAt'] ?? ''}';
    return _card(
      tag: answered ? '답변 완료' : '답변 대기',
      tagColors: answered ? ColorTag.mint : ColorTag.gold,
      title: Text('${q['title']}', style: _titleStyle),
      sub: '${_inquiryCategory[q['category']] ?? q['category']} · ${widget.item['nickname']} · '
          '${created.length >= 16 ? created.substring(0, 16).replaceFirst('T', ' ') : created}',
      children: [
        const SizedBox(height: 10),
        Text('${q['content']}', style: const TextStyle(fontSize: 13, fontWeight: FontWeight.w500, height: 1.55)),
        GestureDetector(
          onTap: () => _open(context, ProfileScreen(userId: (widget.item['userId'] as num).toInt())),
          child: Padding(
            padding: const EdgeInsets.only(top: 10),
            child: Text('${widget.item['nickname']} 프로필 보기',
                style: const TextStyle(color: AppColors.brown, fontSize: 12, fontWeight: FontWeight.w800)),
          ),
        ),
        const SizedBox(height: 12),
        TextField(
          controller: _answer,
          minLines: 3,
          maxLines: 6,
          maxLength: 2000,
          style: const TextStyle(fontSize: 13),
          decoration: const InputDecoration(
            labelText: '답변',
            hintText: '회원에게 보일 답변을 적어 주세요.',
            alignLabelWithHint: true,
            counterText: '',
          ),
        ),
        const SizedBox(height: 12),
        FilledButton(
          style: FilledButton.styleFrom(
            minimumSize: const Size.fromHeight(44),
            textStyle: const TextStyle(fontFamily: 'NotoSansKR', fontSize: 13.5, fontWeight: FontWeight.w800),
          ),
          onPressed: _busy ? null : _submit,
          child: Text(_busy ? '저장하는 중…' : (answered ? '답변 고치기' : '답변 달기')),
        ),
      ],
    );
  }
}
