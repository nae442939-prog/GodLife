import 'package:flutter/material.dart';
import 'package:provider/provider.dart';

import '../api/models.dart';
import '../app_state.dart';
import '../theme.dart';
import '../widgets/async_view.dart';

/// 포인트 지갑. 충전 포인트(참가비 · 환불 대상)와 보상 포인트(상점 전용)를 따로 보여 준다.
/// 충전(테스트 결제) · 환불은 웹 지갑 화면으로 보낸다 — 앱에는 현금화 기능이 없다.
class WalletScreen extends StatelessWidget {
  const WalletScreen({super.key});

  @override
  Widget build(BuildContext context) {
    final api = context.read<AppState>().api;
    return Scaffold(
      appBar: AppBar(title: const Text('포인트 지갑')),
      body: AsyncView<Wallet>(
      load: () async => Wallet.fromJson(await api.get('/api/wallet') as Map<String, dynamic>),
      builder: (context, w, reload) => ListView(padding: const EdgeInsets.fromLTRB(16, 8, 16, 24), children: [
        Row(children: [
          Expanded(child: _balance('충전 포인트', w.chargedBalance, '챌린지 참가에 써요', AppColors.brown)),
          const SizedBox(width: 12),
          Expanded(child: _balance('보상 포인트', w.rewardBalance, '포인트 상점에서만 써요', AppColors.mint)),
        ]),
        const SizedBox(height: 12),
        // 충전 · 환불은 결제창(토스페이먼츠 테스트 결제)이 있는 웹 지갑 화면에서 한다
        Row(children: [
          Expanded(
            flex: 3,
            child: FilledButton.icon(
              onPressed: () => openWeb('/wallet'),
              icon: const Icon(Icons.add_card),
              label: const Text('포인트 충전'),
            ),
          ),
          const SizedBox(width: 10),
          Expanded(
            flex: 2,
            child: OutlinedButton(
              onPressed: () => openWeb('/wallet'),
              style: OutlinedButton.styleFrom(
                minimumSize: const Size.fromHeight(50),
                side: const BorderSide(color: AppColors.border),
                shape: RoundedRectangleBorder(borderRadius: BorderRadius.circular(12)),
              ),
              child: const Text('환불', style: TextStyle(color: AppColors.text, fontWeight: FontWeight.w700)),
            ),
          ),
        ]),
        const SizedBox(height: 6),
        const Text('테스트 결제라 실제 돈은 빠지지 않아요. 결제를 마치고 왼쪽 위 로고를 누르면 앱 화면으로 돌아와요.',
            style: TextStyle(color: AppColors.muted, fontSize: 12.5, height: 1.4)),
        const SizedBox(height: 12),
        Card(
          child: Padding(
            padding: const EdgeInsets.all(16),
            child: Column(crossAxisAlignment: CrossAxisAlignment.start, children: [
              const Text('챌린지에 건 포인트', style: TextStyle(fontWeight: FontWeight.w800)),
              const SizedBox(height: 10),
              _limit('오늘', w.betToday, w.betDailyLimit),
              const SizedBox(height: 8),
              _limit('이번 달', w.betThisMonth, w.betMonthlyLimit),
            ]),
          ),
        ),
        const SizedBox(height: 12),
        const Text(
          '보상 포인트는 현금으로 바꾸거나 출금할 수 없어요. 직접 충전하고 쓰지 않은 포인트만 결제 취소로 환불받을 수 있어요.',
          style: TextStyle(color: AppColors.muted, fontSize: 12.5, height: 1.4),
        ),
        const SizedBox(height: 22),
        const Padding(
          padding: EdgeInsets.symmetric(horizontal: 4),
          child: Text('거래 내역', style: TextStyle(fontSize: 16, fontWeight: FontWeight.w800)),
        ),
        const SizedBox(height: 8),
        if (w.transactions.isEmpty)
          const Padding(
            padding: EdgeInsets.symmetric(vertical: 32),
            child: Center(child: Text('아직 거래 내역이 없어요.', style: TextStyle(color: AppColors.muted))),
          ),
        Card(
          child: Column(children: [
            for (final (i, t) in w.transactions.indexed) ...[
              if (i > 0) const Divider(height: 1, color: AppColors.border),
              ListTile(
                title: Text(_txLabel(t), style: const TextStyle(fontWeight: FontWeight.w700)),
                subtitle: Text(
                  [
                    if (t.challengeTitle != null) t.challengeTitle!,
                    if (t.orderTitle != null) t.orderTitle!,
                    '${t.source == 'REWARD' ? '보상' : '충전'} ${points(t.balanceAfter)}P · ${shortDateTime(t.createdAt)}',
                  ].join('\n'),
                  style: const TextStyle(fontSize: 12.5),
                ),
                trailing: Text(
                  '${t.amount > 0 ? '+' : '−'}${points(t.amount.abs())}P',
                  style: TextStyle(
                    fontSize: 15,
                    fontWeight: FontWeight.w800,
                    color: t.amount > 0 ? AppColors.mint : AppColors.text,
                  ),
                ),
              ),
            ],
          ]),
        ),
      ]),
    ));
  }

  Widget _balance(String label, int value, String hint, Color color) {
    return Card(
      child: Padding(
        padding: const EdgeInsets.all(16),
        child: Column(crossAxisAlignment: CrossAxisAlignment.start, children: [
          Text(label, style: const TextStyle(color: AppColors.muted, fontWeight: FontWeight.w600)),
          const SizedBox(height: 6),
          Text('${points(value)}P', style: TextStyle(fontSize: 22, fontWeight: FontWeight.w900, color: color)),
          const SizedBox(height: 4),
          Text(hint, style: const TextStyle(color: AppColors.muted, fontSize: 12)),
        ]),
      ),
    );
  }

  Widget _limit(String label, int used, int limit) {
    return Row(children: [
      SizedBox(width: 60, child: Text(label, style: const TextStyle(color: AppColors.muted))),
      Expanded(
        child: ClipRRect(
          borderRadius: BorderRadius.circular(99),
          child: LinearProgressIndicator(
            value: limit == 0 ? 0 : (used / limit).clamp(0, 1).toDouble(),
            minHeight: 8,
            backgroundColor: AppColors.border,
            color: AppColors.brown,
          ),
        ),
      ),
      const SizedBox(width: 10),
      Text('${points(used)} / ${points(limit)}P', style: const TextStyle(fontSize: 12.5, fontWeight: FontWeight.w600)),
    ]);
  }

  String _txLabel(PointTransaction t) => switch (t.type) {
        'CHARGE' => '포인트 충전',
        'CHARGE_CANCEL' => '충전 포인트 환불',
        'ENTRY_FEE' => '챌린지 참가',
        'REFUND' => '참가 포인트 환급',
        'REWARD' => '챌린지 보상',
        'PURCHASE' => '상점 구매',
        'PURCHASE_CANCEL' => '상점 주문 취소',
        'SEASON_BONUS' => '시즌 보너스',
        'ADJUST' => '조정',
        _ => t.type,
      };
}
