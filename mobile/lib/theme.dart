import 'package:flutter/material.dart';

/// 웹(frontend/src/index.css)과 같은 색을 쓴다.
/// 코랄은 주요 버튼에만, 강조 · 선택 표시는 진한 갈색으로 (웹과 같은 규칙).
class AppColors {
  static const bg = Color(0xFFFFF8F0);
  static const surface = Color(0xFFFFFFFF);
  static const surfaceAlt = Color(0xFFFBF1E6);
  static const text = Color(0xFF2A2925);
  static const muted = Color(0xFF8C847A);
  static const border = Color(0xFFECE4D8);
  static const primary = Color(0xFFFF6B4A);
  static const mint = Color(0xFF14B8A6);
  static const brown = Color(0xFF6B4A35);
  static const danger = Color(0xFFD6336C);
}

ThemeData buildTheme() {
  final scheme = ColorScheme.fromSeed(seedColor: AppColors.brown, brightness: Brightness.light).copyWith(
    primary: AppColors.brown,
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
      titleTextStyle: TextStyle(fontFamily: 'NotoSansKR', color: AppColors.text, fontSize: 20, fontWeight: FontWeight.w800),
    ),
    cardTheme: CardThemeData(
      color: AppColors.surface,
      elevation: 3,
      shadowColor: const Color(0x226B4A35),
      surfaceTintColor: Colors.transparent,
      margin: EdgeInsets.zero,
      shape: RoundedRectangleBorder(borderRadius: BorderRadius.circular(20)),
    ),
    filledButtonTheme: FilledButtonThemeData(
      style: FilledButton.styleFrom(
        backgroundColor: AppColors.primary,
        foregroundColor: Colors.white,
        minimumSize: const Size.fromHeight(50),
        shape: RoundedRectangleBorder(borderRadius: BorderRadius.circular(12)),
        textStyle: const TextStyle(fontFamily: 'NotoSansKR', fontSize: 16, fontWeight: FontWeight.w700),
      ),
    ),
    inputDecorationTheme: InputDecorationTheme(
      filled: true,
      fillColor: AppColors.surface,
      border: OutlineInputBorder(
        borderRadius: BorderRadius.circular(12),
        borderSide: const BorderSide(color: AppColors.border),
      ),
      enabledBorder: OutlineInputBorder(
        borderRadius: BorderRadius.circular(12),
        borderSide: const BorderSide(color: AppColors.border),
      ),
    ),
    navigationBarTheme: NavigationBarThemeData(
      backgroundColor: AppColors.surface,
      indicatorColor: AppColors.surfaceAlt,
      labelTextStyle: WidgetStateProperty.resolveWith((states) => TextStyle(
            fontFamily: 'NotoSansKR',
            fontSize: 12,
            fontWeight: states.contains(WidgetState.selected) ? FontWeight.w800 : FontWeight.w500,
            color: states.contains(WidgetState.selected) ? AppColors.brown : AppColors.muted,
          )),
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
