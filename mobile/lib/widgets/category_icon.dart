import 'package:flutter/material.dart';

/// 카테고리를 한눈에 알아보게 하는 둥근 네모 아이콘 (글자 꼬리표 대신 쓴다)
class CategoryIcon extends StatelessWidget {
  const CategoryIcon(this.name, {super.key, this.size = 46});

  final String name;
  final double size;

  /// (아이콘, 바탕색, 아이콘 색)
  static (IconData, Color, Color) styleOf(String name) => switch (name) {
        '운동' => (Icons.fitness_center, const Color(0xFFFFE6DE), const Color(0xFFE2593F)),
        '공부' => (Icons.edit_note, const Color(0xFFE2ECFA), const Color(0xFF3273C2)),
        '독서' => (Icons.auto_stories, const Color(0xFFEFE6F8), const Color(0xFF7B55B5)),
        '요리' => (Icons.restaurant, const Color(0xFFFDF0D2), const Color(0xFFD9920F)),
        _ => (Icons.auto_awesome, const Color(0xFFDDF4EF), const Color(0xFF0F9C86)),
      };

  @override
  Widget build(BuildContext context) {
    final (icon, background, foreground) = styleOf(name);
    return Container(
      width: size,
      height: size,
      decoration: BoxDecoration(color: background, borderRadius: BorderRadius.circular(size * 0.3)),
      child: Icon(icon, color: foreground, size: size * 0.52),
    );
  }
}
