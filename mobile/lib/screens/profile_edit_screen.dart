import 'package:flutter/material.dart';
import 'package:image_picker/image_picker.dart';
import 'package:provider/provider.dart';

import '../api/api_client.dart';
import '../app_state.dart';
import '../theme.dart';
import '../widgets/async_view.dart';
import '../widgets/badge_medal.dart';
import '../widgets/design.dart';

/// 프로필 수정 (웹의 프로필 수정 창과 같다): 사진 · 닉네임 · 한 줄 소개 · 뱃지 착용.
/// 사진과 뱃지는 고르는 즉시 바뀌고, 닉네임 · 한 줄 소개는 [저장하기]를 눌러야 바뀐다.
class ProfileEditScreen extends StatefulWidget {
  const ProfileEditScreen({super.key});

  @override
  State<ProfileEditScreen> createState() => _ProfileEditScreenState();
}

class _ProfileEditScreenState extends State<ProfileEditScreen> {
  late final AppState _state = context.read<AppState>();
  late final _nickname = TextEditingController(text: _state.user?.nickname ?? '');
  late final _bio = TextEditingController(text: _state.user?.bio ?? '');
  bool _saving = false;
  bool _photoBusy = false;
  bool _badgeBusy = false;

  ApiClient get _api => _state.api;

  @override
  void dispose() {
    _nickname.dispose();
    _bio.dispose();
    super.dispose();
  }

  Future<void> _save() async {
    final nickname = _nickname.text.trim();
    if (nickname.isEmpty) {
      showMessage(context, '닉네임을 입력해 주세요.');
      return;
    }
    setState(() => _saving = true);
    try {
      await _api.put('/api/users/me/profile', body: {'nickname': nickname, 'bio': _bio.text.trim()});
      await _state.reloadUser();
      if (mounted) showMessage(context, '프로필을 바꿨어요.');
    } catch (e) {
      if (mounted) showMessage(context, '$e');
    } finally {
      if (mounted) setState(() => _saving = false);
    }
  }

  Future<void> _changePhoto() async {
    final XFile? picked;
    try {
      picked = await ImagePicker().pickImage(source: ImageSource.gallery, maxWidth: 1200, imageQuality: 85);
    } catch (_) {
      if (mounted) showMessage(context, '사진을 불러오지 못했어요.');
      return;
    }
    if (picked == null || !mounted) return;
    final bytes = await picked.readAsBytes();
    await _photo(() => _api.upload('/api/users/me/profile-image', bytes, 'profile.jpg'), '프로필 사진을 바꿨어요.');
  }

  Future<void> _photo(Future<dynamic> Function() request, String done) async {
    if (!mounted) return;
    setState(() => _photoBusy = true);
    try {
      await request();
      await _state.reloadUser();
      if (mounted) showMessage(context, done);
    } catch (e) {
      if (mounted) showMessage(context, '$e');
    } finally {
      if (mounted) setState(() => _photoBusy = false);
    }
  }

  /// 뱃지 착용 (code = null 이면 해제). 바뀐 프로필을 그대로 화면에 반영한다
  Future<void> _equip(Map<String, dynamic> profile, String? code) async {
    setState(() => _badgeBusy = true);
    try {
      final updated = await _api.put('/api/users/me/badge', body: {'code': code});
      if (!mounted) return;
      profile
        ..clear()
        ..addAll(Map<String, dynamic>.from(updated as Map));
      showMessage(context, code == null ? '뱃지를 해제했어요.' : '뱃지를 착용했어요.');
    } catch (e) {
      if (mounted) showMessage(context, '$e');
    } finally {
      if (mounted) setState(() => _badgeBusy = false);
    }
  }

  @override
  Widget build(BuildContext context) {
    final user = context.watch<AppState>().user;
    if (user == null) return const SizedBox.shrink();
    final image = user.profileImageUrl;
    return Scaffold(
      appBar: AppBar(title: const Text('프로필 수정')),
      body: SafeArea(
        child: AsyncView<Map<String, dynamic>>(
          load: () async => Map<String, dynamic>.from(await _api.get('/api/users/${user.id}/profile') as Map),
          builder: (context, profile, reload) {
            final earned = ((profile['badges'] as List?) ?? [])
                .cast<Map<String, dynamic>>()
                .where((b) => b['earned'] == true)
                .toList();
            final equipped = profile['equippedBadge'] as String?;
            return ListView(padding: const EdgeInsets.fromLTRB(20, 8, 20, 32), children: [
              // 사진
              Center(
                child: Container(
                  width: 88,
                  height: 88,
                  clipBehavior: Clip.antiAlias,
                  decoration: const BoxDecoration(
                    shape: BoxShape.circle,
                    gradient: LinearGradient(
                      begin: Alignment.topLeft,
                      end: Alignment.bottomRight,
                      colors: [Color(0xFFFBE4D8), Color(0xFFF0876A)],
                    ),
                  ),
                  child: image != null
                      ? Image.network('$serverBase$image',
                          fit: BoxFit.cover,
                          errorBuilder: (_, _, _) => const Icon(Icons.person, color: Colors.white, size: 46))
                      : const Icon(Icons.person, color: Colors.white, size: 46),
                ),
              ),
              const SizedBox(height: 12),
              Row(mainAxisAlignment: MainAxisAlignment.center, children: [
                _small(_photoBusy ? '올리는 중…' : '사진 바꾸기', _photoBusy ? null : _changePhoto),
                if (image != null) ...[
                  const SizedBox(width: 8),
                  _small(
                      '사진 빼기',
                      _photoBusy
                          ? null
                          : () => _photo(() => _api.delete('/api/users/me/profile-image'), '프로필 사진을 뺐어요.')),
                ],
              ]),
              const SizedBox(height: 6),
              const Center(
                child: Text('JPG · PNG, 5MB 이하',
                    style: TextStyle(color: AppColors.muted, fontSize: 11.5, fontWeight: FontWeight.w600)),
              ),
              const SizedBox(height: 22),
              // 닉네임 · 한 줄 소개
              TextField(controller: _nickname, maxLength: 20, decoration: const InputDecoration(labelText: '닉네임')),
              const SizedBox(height: 8),
              TextField(
                  controller: _bio,
                  maxLength: 200,
                  maxLines: 3,
                  decoration: const InputDecoration(labelText: '한 줄 소개', alignLabelWithHint: true)),
              const SizedBox(height: 8),
              FilledButton(onPressed: _saving ? null : _save, child: Text(_saving ? '저장하는 중…' : '저장하기')),
              const SizedBox(height: 28),
              // 뱃지 착용
              const Text('뱃지 착용', style: TextStyle(fontSize: 17, fontWeight: FontWeight.w800, letterSpacing: -0.2)),
              const SizedBox(height: 5),
              Text(
                  earned.isEmpty
                      ? '아직 딴 뱃지가 없어요. 첫 인증을 하면 첫 뱃지를 받아요.'
                      : '딴 뱃지 중 하나를 고르면 이름 옆에 보여요. 착용 중인 뱃지를 다시 누르면 해제돼요.',
                  style: const TextStyle(color: AppColors.muted, fontSize: 12, fontWeight: FontWeight.w600, height: 1.5)),
              const SizedBox(height: 14),
              Wrap(spacing: 10, runSpacing: 10, children: [
                for (final b in earned)
                  _badge(b, b['code'] == equipped,
                      () => _equip(profile, b['code'] == equipped ? null : b['code'] as String)),
              ]),
            ]);
          },
        ),
      ),
    );
  }

  Widget _small(String label, VoidCallback? onTap) {
    return OutlinedButton(
      style: OutlinedButton.styleFrom(
        minimumSize: const Size(0, 38),
        padding: const EdgeInsets.symmetric(horizontal: 16),
        foregroundColor: AppColors.text,
        backgroundColor: AppColors.surface,
        side: const BorderSide(color: AppColors.cardBorder),
        shape: RoundedRectangleBorder(borderRadius: BorderRadius.circular(999)),
        textStyle: const TextStyle(fontFamily: 'NotoSansKR', fontSize: 12.5, fontWeight: FontWeight.w800),
      ),
      onPressed: onTap,
      child: Text(label),
    );
  }

  /// 딴 뱃지 한 칸. 착용 중이면 진한 테두리와 '착용 중'
  Widget _badge(Map<String, dynamic> b, bool on, VoidCallback onTap) {
    return GestureDetector(
      onTap: _badgeBusy ? null : onTap,
      child: Container(
        width: 100,
        padding: const EdgeInsets.fromLTRB(6, 12, 6, 10),
        decoration: BoxDecoration(
          color: on ? const Color(0xFFFDF8E7) : AppColors.surface,
          border: Border.all(color: on ? AppColors.brown : AppColors.cardBorder, width: on ? 2 : 1),
          borderRadius: BorderRadius.circular(14),
        ),
        child: Column(children: [
          BadgeMedal(b['code'] as String?, earned: true, size: 48),
          const SizedBox(height: 7),
          Text(b['name'] as String,
              maxLines: 1,
              overflow: TextOverflow.ellipsis,
              style: const TextStyle(fontSize: 12.5, fontWeight: FontWeight.w800)),
          const SizedBox(height: 5),
          on
              ? ColorTag.of('착용 중', ColorTag.mint)
              : const Text('착용하기',
                  style: TextStyle(color: AppColors.muted, fontSize: 11, fontWeight: FontWeight.w700, height: 1.7)),
        ]),
      ),
    );
  }
}
