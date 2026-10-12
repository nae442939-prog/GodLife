import 'package:flutter/foundation.dart' show kIsWeb;
import 'package:flutter/material.dart';
import 'package:url_launcher/url_launcher.dart';

import '../api/api_client.dart';

import '../theme.dart';

/// 불러오는 중 · 실패(다시 시도) · 결과를 한 모양으로 보여 준다. 아래로 당기면 새로 고친다.
class AsyncView<T> extends StatefulWidget {
  const AsyncView({super.key, required this.load, required this.builder});

  final Future<T> Function() load;
  final Widget Function(BuildContext context, T data, Future<void> Function() reload) builder;

  @override
  State<AsyncView<T>> createState() => AsyncViewState<T>();
}

class AsyncViewState<T> extends State<AsyncView<T>> {
  T? _data;
  Object? _error;
  bool _loading = true;

  @override
  void initState() {
    super.initState();
    reload();
  }

  Future<void> reload() async {
    try {
      final data = await widget.load();
      if (!mounted) return;
      setState(() {
        _data = data;
        _error = null;
        _loading = false;
      });
    } catch (e) {
      if (!mounted) return;
      setState(() {
        _error = e;
        _loading = false;
      });
    }
  }

  @override
  Widget build(BuildContext context) {
    if (_loading) return const Center(child: CircularProgressIndicator());
    final data = _data;
    if (data == null) {
      return Center(
        child: Padding(
          padding: const EdgeInsets.all(24),
          child: Column(mainAxisSize: MainAxisSize.min, children: [
            Text('$_error', textAlign: TextAlign.center, style: const TextStyle(color: AppColors.muted)),
            const SizedBox(height: 12),
            OutlinedButton(
              onPressed: () {
                setState(() => _loading = true);
                reload();
              },
              child: const Text('다시 시도'),
            ),
          ]),
        ),
      );
    }
    return RefreshIndicator(onRefresh: reload, child: widget.builder(context, data, reload));
  }
}

void showMessage(BuildContext context, String message) {
  ScaffoldMessenger.of(context)
    ..hideCurrentSnackBar()
    ..showSnackBar(SnackBar(content: Text(message), behavior: SnackBarBehavior.floating));
}

/// 작은 꼬리표 (무료 / 12,000P / 카테고리)
class Tag extends StatelessWidget {
  const Tag(this.label, {super.key, this.color = AppColors.brown, this.filled = false});

  final String label;
  final Color color;
  final bool filled;

  @override
  Widget build(BuildContext context) {
    return Container(
      padding: const EdgeInsets.symmetric(horizontal: 9, vertical: 4),
      decoration: BoxDecoration(
        color: filled ? color : color.withValues(alpha: 0.1),
        borderRadius: BorderRadius.circular(99),
      ),
      child: Text(label,
          style: TextStyle(fontSize: 12, fontWeight: FontWeight.w700, color: filled ? Colors.white : color)),
    );
  }
}

/// 앱에 없는 화면(충전 · 상점 · 가입)을 웹에서 연다. 웹 빌드에서는 같은 창에서 연다
Future<void> openWeb(String path) async {
  await launchUrl(Uri.parse('$serverBase$path'), webOnlyWindowName: kIsWeb ? '_self' : null);
}
