import 'package:flutter/material.dart';

/// 앱 시안의 색. 코랄은 주요 버튼에만, 선택 표시는 먹색(#201d17)으로 한다.
class AppColors {
  static const bg = Color(0xFFFDFBF6);
  static const surface = Color(0xFFFFFFFF);
  static const surfaceAlt = Color(0xFFF1EDE2); // 꺼진 칩 · 옅은 바탕
  static const text = Color(0xFF201D17);
  static const muted = Color(0xFFA39C8C);
  static const chipText = Color(0xFF948E7C);
  static const navIdle = Color(0xFFB3ADA0);
  static const border = Color(0xFFF1EDE2);
  static const cardBorder = Color(0xFFF3EFE4);
  static const primary = Color(0xFFEE6A4D);
  static const mint = Color(0xFF0F9C86);
  static const mintBg = Color(0xFFE3F5F1);
  static const point = Color(0xFFC2742F);
  static const pointBg = Color(0xFFFDEEE1);
  static const brown = Color(0xFF4A4638); // 아이콘 · 강조 글자
  static const danger = Color(0xFFD6336C);
}

/// 시안의 카드 그림자 (아래로 살짝, 넓게 퍼지지 않게)
const cardShadow = [
  BoxShadow(color: Color(0x243C2814), blurRadius: 14, offset: Offset(0, 6), spreadRadius: -10),
];

ThemeData buildTheme() {
  final scheme = ColorScheme.fromSeed(seedColor: AppColors.text, brightness: Brightness.light).copyWith(
    primary: AppColors.text,
    onPrimary: Colors.white,
    surface: AppColors.surface,
    onSurface: AppColors.text,
    error: AppColors.danger,
  );
  return ThemeData(
    useMaterial3: true,
    fontFamily: 'NotoSansKR',
    colorScheme: scheme,
    scaffoldBackgroundColor: AppColors.bg,
    appBarTheme: const AppBarTheme(
      backgroundColor: AppColors.bg,
      foregroundColor: AppColors.text,
      elevation: 0,
      scrolledUnderElevation: 0,
      titleTextStyle: TextStyle(
          fontFamily: 'NotoSansKR', color: AppColors.text, fontSize: 17, fontWeight: FontWeight.w800, letterSpacing: -0.3),
    ),
    cardTheme: CardThemeData(
      color: AppColors.surface,
      elevation: 2,
      shadowColor: const Color(0x1F3C2814),
      surfaceTintColor: Colors.transparent,
      margin: EdgeInsets.zero,
      shape: RoundedRectangleBorder(
        borderRadius: BorderRadius.circular(20),
        side: const BorderSide(color: AppColors.cardBorder),
      ),
    ),
    filledButtonTheme: FilledButtonThemeData(
      style: FilledButton.styleFrom(
        backgroundColor: AppColors.primary,
        foregroundColor: Colors.white,
        minimumSize: const Size.fromHeight(50),
        shape: RoundedRectangleBorder(borderRadius: BorderRadius.circular(999)),
        textStyle: const TextStyle(fontFamily: 'NotoSansKR', fontSize: 15, fontWeight: FontWeight.w800),
      ),
    ),
    chipTheme: ChipThemeData(
      shape: RoundedRectangleBorder(borderRadius: BorderRadius.circular(999)),
      side: BorderSide.none,
    ),
    inputDecorationTheme: InputDecorationTheme(
      filled: true,
      fillColor: AppColors.surface,
      hintStyle: const TextStyle(color: AppColors.muted),
      border: OutlineInputBorder(
        borderRadius: BorderRadius.circular(14),
        borderSide: const BorderSide(color: AppColors.cardBorder),
      ),
      enabledBorder: OutlineInputBorder(
        borderRadius: BorderRadius.circular(14),
        borderSide: const BorderSide(color: AppColors.cardBorder),
      ),
    ),
  );
}

/// 12,000 처럼 천 단위 쉼표
String points(int value) {
  final s = value.abs().toString();
  final out = StringBuffer();
  for (var i = 0; i < s.length; i++) {
    if (i > 0 && (s.length - i) % 3 == 0) out.write(',');
    out.write(s[i]);
  }
  return '${value < 0 ? '-' : ''}$out';
}

String two(int n) => n.toString().padLeft(2, '0');

/// 10.12 09:41
String shortDateTime(DateTime t) => '${two(t.month)}.${two(t.day)} ${two(t.hour)}:${two(t.minute)}';

const _weekdays = ['월', '화', '수', '목', '금', '토', '일'];

/// '2026-10-16' → '10월 16일(목)'
String koreanDate(String iso) {
  final d = DateTime.parse(iso);
  return '${d.month}월 ${d.day}일(${_weekdays[d.weekday - 1]})';
}

/// 오늘부터 그 날짜까지 남은 날 (오늘이면 0, 지났으면 음수)
int daysUntil(String iso) {
  final d = DateTime.parse(iso);
  final now = DateTime.now();
  return DateTime(d.year, d.month, d.day).difference(DateTime(now.year, now.month, now.day)).inDays;
}
