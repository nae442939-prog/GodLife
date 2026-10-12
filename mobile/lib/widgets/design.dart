import 'package:flutter/material.dart';

import '../theme.dart';

/// 시안의 알약 모양 칩: 꺼지면 옅은 바탕에 회갈색 글자, 켜지면 먹색 바탕에 흰 글자
class PillChip extends StatelessWidget {
  const PillChip(this.label, {super.key, required this.selected, required this.onTap});

  final String label;
  final bool selected;
  final VoidCallback onTap;

  @override
  Widget build(BuildContext context) {
    return GestureDetector(
      onTap: onTap,
      child: AnimatedContainer(
        duration: const Duration(milliseconds: 150),
        padding: const EdgeInsets.symmetric(horizontal: 15, vertical: 8),
        decoration: BoxDecoration(
          color: selected ? AppColors.text : AppColors.surfaceAlt,
          borderRadius: BorderRadius.circular(999),
        ),
        child: Text(label,
            style: TextStyle(
              fontSize: 12.5,
              fontWeight: FontWeight.w800,
              letterSpacing: -0.1,
              color: selected ? Colors.white : AppColors.chipText,
            )),
      ),
    );
  }
}

/// 무료(민트) / 참가 포인트(주황빛 갈색) 꼬리표
class PriceBadge extends StatelessWidget {
  const PriceBadge({super.key, required this.free, required this.fee});

  final bool free;
  final int fee;

  @override
  Widget build(BuildContext context) {
    return Container(
      padding: const EdgeInsets.symmetric(horizontal: 11, vertical: 6),
      decoration: BoxDecoration(
        color: free ? AppColors.mintBg : AppColors.pointBg,
        borderRadius: BorderRadius.circular(999),
      ),
      child: Text(free ? '무료' : '${points(fee)}P',
          style: TextStyle(
            fontSize: 11.5,
            fontWeight: FontWeight.w800,
            color: free ? AppColors.mint : AppColors.point,
          )),
    );
  }
}

/// 시안의 흰 카드 (옅은 테두리 + 아래로 살짝 지는 그림자)
class AppCard extends StatelessWidget {
  const AppCard({super.key, required this.child, this.onTap, this.padding = const EdgeInsets.fromLTRB(14, 13, 14, 13)});

  final Widget child;
  final VoidCallback? onTap;
  final EdgeInsets padding;

  @override
  Widget build(BuildContext context) {
    return Container(
      decoration: BoxDecoration(
        color: AppColors.surface,
        borderRadius: BorderRadius.circular(20),
        border: Border.all(color: AppColors.cardBorder),
        boxShadow: cardShadow,
      ),
      child: Material(
        color: Colors.transparent,
        borderRadius: BorderRadius.circular(20),
        clipBehavior: Clip.antiAlias,
        child: InkWell(onTap: onTap, child: Padding(padding: padding, child: child)),
      ),
    );
  }
}

/// 화면 아래 가운데에 뜨는 코랄 알약 버튼 (챌린지 만들기 · 글쓰기)
class PillFab extends StatelessWidget {
  const PillFab({super.key, required this.label, required this.icon, required this.onTap});

  final String label;
  final IconData icon;
  final VoidCallback onTap;

  @override
  Widget build(BuildContext context) {
    return Container(
      decoration: BoxDecoration(
        borderRadius: BorderRadius.circular(999),
        boxShadow: const [
          BoxShadow(color: Color(0x8CEE6A4D), blurRadius: 30, offset: Offset(0, 18), spreadRadius: -12),
        ],
      ),
      child: Material(
        color: AppColors.primary,
        borderRadius: BorderRadius.circular(999),
        clipBehavior: Clip.antiAlias,
        child: InkWell(
          onTap: onTap,
          child: Padding(
            padding: const EdgeInsets.symmetric(horizontal: 24, vertical: 14),
            child: Row(mainAxisSize: MainAxisSize.min, children: [
              Icon(icon, color: Colors.white, size: 16),
              const SizedBox(width: 7),
              Text(label,
                  style: const TextStyle(
                      color: Colors.white, fontSize: 13.5, fontWeight: FontWeight.w800, letterSpacing: -0.1)),
            ]),
          ),
        ),
      ),
    );
  }
}
