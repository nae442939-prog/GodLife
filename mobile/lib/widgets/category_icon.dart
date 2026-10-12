import 'package:flutter/material.dart';

/// 챌린지를 한눈에 알아보게 하는 동그란 아이콘 (시안의 다섯 가지 색: 코랄 · 민트 · 골드 · 그린 · 라벤더).
/// 카테고리만 알면 카테고리 그림을, 세부 종류나 제목을 알면 그에 맞는 그림(산책 · 물 · 샐러드 …)을 고른다.
class CategoryIcon extends StatelessWidget {
  const CategoryIcon(this.category, {super.key, this.title = '', this.subType, this.size = 46});

  final String category;
  final String title;
  final String? subType;
  final double size;

  static const _coral = (Color(0xFFFDE2D7), Color(0xFFD9693F));
  static const _mint = (Color(0xFFD7F1EA), Color(0xFF0F9C86));
  static const _gold = (Color(0xFFFAECD1), Color(0xFFC2932A));
  static const _green = (Color(0xFFE1EFD3), Color(0xFF5A8A3A));
  static const _lav = (Color(0xFFE2E3F5), Color(0xFF5A5F9E));

  /// (아이콘, (바탕색, 그림 색))
  static (IconData, (Color, Color)) styleOf(String category, {String title = '', String? subType}) {
    bool has(List<String> words) => words.any((w) => title.contains(w) || (subType ?? '').contains(w));

    if (has(['산책', '한 바퀴', '걷기', '러닝', '달리기'])) return (Icons.directions_walk, _coral);
    if (has(['물 ', '식물'])) return (Icons.water_drop, _mint);
    if (has(['기상', '모닝', '일찍'])) return (Icons.wb_sunny, _gold);
    if (has(['정리', '청소'])) return (Icons.cleaning_services, _mint);
    if (has(['샐러드', '채소'])) return (Icons.rice_bowl, _green);
    if (has(['홈트', '스트레칭', '요가'])) return (Icons.bolt, _coral);
    return switch (category) {
      '운동' => (Icons.fitness_center, _coral),
      '공부' => (Icons.article, _lav),
      '독서' => (Icons.menu_book, _lav),
      '요리' => (Icons.soup_kitchen, _gold),
      _ => (Icons.auto_awesome, _mint),
    };
  }

  @override
  Widget build(BuildContext context) {
    final (icon, (background, foreground)) = styleOf(category, title: title, subType: subType);
    return Container(
      width: size,
      height: size,
      decoration: BoxDecoration(color: background, shape: BoxShape.circle),
      child: Icon(icon, color: foreground, size: size * 0.5),
    );
  }
}
