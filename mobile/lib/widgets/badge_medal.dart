import 'dart:math' as math;

import 'package:flutter/material.dart';

enum _Shape { circle, burst, hexagon, shield }

/// 뱃지 한 개의 생김새: 모양 · 가운데 그림 · 색(밝은 · 기본 · 어두운) · 아래 숫자
class _Look {
  const _Look(this.shape, this.icon, this.light, this.base, this.dark, [this.label, this.iconColor = Colors.white]);

  final _Shape shape;
  final IconData icon;
  final Color light;
  final Color base;
  final Color dark;
  final String? label;

  /// 가운데 그림 색 (관리자 뱃지만 금색)
  final Color iconColor;
}

// 묶음마다 모양이 다르다: 인증 횟수 = 동그라미, 연속 인증 = 톱니, 완주 = 육각, 활동 = 방패
const _green = (Color(0xFFB4ECA6), Color(0xFF4FB56A), Color(0xFF2C7A49));
const _teal = (Color(0xFF9AE8DA), Color(0xFF1FAF9A), Color(0xFF0E7566));
const _blue = (Color(0xFFA9D0FF), Color(0xFF4A8FE8), Color(0xFF2A5FB0));
const _purple = (Color(0xFFD2BCFF), Color(0xFF8A63E0), Color(0xFF5837A8));
const _orange = (Color(0xFFFFCB99), Color(0xFFF5863C), Color(0xFFC25512));
const _red = (Color(0xFFFFB3AA), Color(0xFFEE5A4D), Color(0xFFB03026));
const _magenta = (Color(0xFFFFADD3), Color(0xFFE0488F), Color(0xFFA02560));
const _indigo = (Color(0xFFB5BDFF), Color(0xFF5C68E0), Color(0xFF343EA0));
const _gold = (Color(0xFFFFE89A), Color(0xFFF2B82A), Color(0xFFB07C06));
const _silver = (Color(0xFFDDE7F2), Color(0xFF8FA6BF), Color(0xFF58708A));
const _lavender = (Color(0xFFCBCDF8), Color(0xFF7A80D6), Color(0xFF474CA0));
const _coral = (Color(0xFFFFC9B5), Color(0xFFEE7A55), Color(0xFFB84A28));
const _pink = (Color(0xFFFFC4E0), Color(0xFFE768A8), Color(0xFFA83A74));

const _grey = (Color(0xFFEDE9DF), Color(0xFFD3CCBD), Color(0xFFB5AD9B));

_Look _look(String? code) {
  _Look of(_Shape shape, IconData icon, (Color, Color, Color) c, [String? label]) =>
      _Look(shape, icon, c.$1, c.$2, c.$3, label);
  return switch (code) {
    'FIRST_VERIFY' => of(_Shape.circle, Icons.eco, _green),
    'VERIFY_10' => of(_Shape.circle, Icons.photo_camera, _teal, '10'),
    'VERIFY_30' => of(_Shape.circle, Icons.photo_camera, _blue, '30'),
    'VERIFY_100' => of(_Shape.circle, Icons.photo_camera, _purple, '100'),
    'STREAK_3' => of(_Shape.burst, Icons.local_fire_department, _orange, '3'),
    'STREAK_7' => of(_Shape.burst, Icons.local_fire_department, _red, '7'),
    'STREAK_14' => of(_Shape.burst, Icons.local_fire_department, _magenta, '14'),
    'STREAK_30' => of(_Shape.burst, Icons.local_fire_department, _indigo, '30'),
    'STREAK_100' => of(_Shape.burst, Icons.local_fire_department, _gold, '100'),
    'FINISHER' => of(_Shape.hexagon, Icons.flag, _teal),
    'FINISHER_5' => of(_Shape.hexagon, Icons.military_tech, _silver, '5'),
    'FINISHER_10' => of(_Shape.hexagon, Icons.emoji_events, _gold, '10'),
    'CHALLENGER' => of(_Shape.shield, Icons.explore, _lavender),
    'HOST' => of(_Shape.shield, Icons.mic, _coral),
    'POPULAR' => of(_Shape.shield, Icons.forum, _pink),
    // 관리자에게만 있는 뱃지: 먹색 방패에 금색 그림
    'ADMIN' => const _Look(_Shape.shield, Icons.verified_user, Color(0xFF6B7080), Color(0xFF2E323D),
        Color(0xFF14161C), null, Color(0xFFFFD66B)),
    _ => of(_Shape.circle, Icons.military_tech, _gold),
  };
}

/// 뱃지 그림: 색 테두리 · 밝은 안쪽 면 · 윗부분 광택을 겹쳐 그린 메달 위에 입체 그림(assets/badges/{코드}.png),
/// 아래에 숫자 알약. 아직 못 딴 뱃지는 같은 모양을 회색으로, 그림은 흑백으로 흐리게 그린다.
class BadgeMedal extends StatelessWidget {
  const BadgeMedal(this.code, {super.key, required this.earned, this.size = 52, this.showLabel = true});

  /// 뱃지 코드 (서버 ProfileService.badges 의 코드)
  final String? code;
  final bool earned;
  final double size;

  /// 아래 숫자 알약. 이름 옆처럼 아주 작게 그릴 때는 끈다
  final bool showLabel;

  @override
  Widget build(BuildContext context) {
    final look = _look(code);
    final (light, base, dark) = earned ? (look.light, look.base, look.dark) : _grey;
    final label = showLabel ? look.label : null;
    // 그림이 잘 보이게 안쪽 면은 밝게 칠한다. 관리자(먹색 방패)와 인기인(흰 말풍선이라 밝은 면에서는 안 보인다)만 색 면 그대로
    final pale = code != 'ADMIN' && code != 'POPULAR';
    final artSize = size * (label == null ? 0.58 : 0.52);
    // 그림 파일을 못 읽으면 예전처럼 아이콘을 보여 준다
    final icon = Icon(look.icon,
        size: artSize * 0.8, color: pale ? base : (earned ? look.iconColor : Colors.white));
    Widget art = Image.asset('assets/badges/$code.png',
        width: artSize, height: artSize, filterQuality: FilterQuality.medium, errorBuilder: (_, _, _) => icon);
    if (!earned) {
      art = Opacity(
        opacity: 0.5,
        child: ColorFiltered(
          colorFilter: const ColorFilter.matrix([
            0.2126, 0.7152, 0.0722, 0, 0, //
            0.2126, 0.7152, 0.0722, 0, 0, //
            0.2126, 0.7152, 0.0722, 0, 0, //
            0, 0, 0, 1, 0,
          ]),
          child: art,
        ),
      );
    }
    return SizedBox(
      width: size,
      // 숫자 알약이 메달 아래로 조금 내려온다
      height: size * (label == null ? 1 : 1.08),
      child: Stack(alignment: Alignment.topCenter, clipBehavior: Clip.none, children: [
        CustomPaint(
          size: Size.square(size),
          painter: _MedalPainter(look.shape, light, base, dark, shadow: earned, pale: pale),
        ),
        SizedBox(
          width: size,
          height: size,
          child: Center(
            child: Padding(
              padding: EdgeInsets.only(bottom: label == null ? 0 : size * 0.1),
              child: art,
            ),
          ),
        ),
        if (label != null)
          Positioned(
            bottom: 0,
            child: Container(
              constraints: BoxConstraints(minWidth: size * 0.42),
              padding: EdgeInsets.symmetric(horizontal: size * 0.1, vertical: size * 0.025),
              alignment: Alignment.center,
              decoration: BoxDecoration(
                color: Colors.white,
                borderRadius: BorderRadius.circular(999),
                border: Border.all(color: base, width: size * 0.03),
              ),
              child: Text(label,
                  style: TextStyle(
                      fontSize: size * 0.2, fontWeight: FontWeight.w800, height: 1.1, color: dark, letterSpacing: -0.2)),
            ),
          ),
      ]),
    );
  }
}

class _MedalPainter extends CustomPainter {
  _MedalPainter(this.shape, this.light, this.base, this.dark, {required this.shadow, required this.pale});

  final _Shape shape;
  final Color light;
  final Color base;
  final Color dark;
  final bool shadow;

  /// 안쪽 면을 밝게 칠할지 (위에 색 있는 그림을 올릴 때)
  final bool pale;

  @override
  void paint(Canvas canvas, Size size) {
    final s = size.width;
    final rect = Offset.zero & size;
    final rim = _path(shape, s);
    // 안쪽 면은 같은 모양을 가운데로 줄인 것
    final face = rim.transform((Matrix4.identity()
          ..translateByDouble(s / 2, s / 2, 0, 1)
          ..scaleByDouble(0.8, 0.8, 1, 1)
          ..translateByDouble(-s / 2, -s / 2, 0, 1))
        .storage);

    if (shadow) {
      canvas.drawPath(
          rim.shift(Offset(0, s * 0.05)),
          Paint()
            ..color = dark.withValues(alpha: 0.3)
            ..maskFilter = MaskFilter.blur(BlurStyle.normal, s * 0.06));
    }
    // 테두리: 왼쪽 위가 밝고 오른쪽 아래가 어둡다
    canvas.drawPath(
        rim,
        Paint()
          ..shader = LinearGradient(
            begin: Alignment.topLeft,
            end: Alignment.bottomRight,
            colors: [light, base, dark],
            stops: const [0, 0.45, 1],
          ).createShader(rect));
    // 안쪽 면: 테두리와 반대로 기울여 오목해 보이게
    canvas.drawPath(
        face,
        Paint()
          ..shader = LinearGradient(
            begin: Alignment.topLeft,
            end: Alignment.bottomRight,
            colors: pale
                ? [Color.lerp(light, Colors.white, 0.25)!, Color.lerp(light, Colors.white, 0.72)!, Colors.white]
                : [dark, base, Color.lerp(base, light, 0.55)!],
            stops: const [0, 0.5, 1],
          ).createShader(rect));
    canvas.drawPath(
        face,
        Paint()
          ..style = PaintingStyle.stroke
          ..strokeWidth = s * 0.02
          ..color = (pale ? dark : Colors.white).withValues(alpha: pale ? 0.18 : 0.35));
    // 윗부분 광택
    canvas.save();
    canvas.clipPath(face);
    canvas.drawOval(
        Rect.fromLTWH(-s * 0.15, -s * 0.5, s * 1.3, s * 0.95),
        Paint()
          ..shader = LinearGradient(
            begin: Alignment.topCenter,
            end: Alignment.bottomCenter,
            colors: [Colors.white.withValues(alpha: pale ? 0.5 : 0.34), Colors.white.withValues(alpha: 0.06)],
          ).createShader(rect));
    canvas.restore();
  }

  @override
  bool shouldRepaint(_MedalPainter old) =>
      old.shape != shape ||
      old.light != light ||
      old.base != base ||
      old.dark != dark ||
      old.shadow != shadow ||
      old.pale != pale;
}

Path _path(_Shape shape, double s) {
  final c = Offset(s / 2, s / 2);
  final r = s / 2;
  Offset at(double angle, double radius) => c + Offset(math.cos(angle), math.sin(angle)) * radius;
  switch (shape) {
    case _Shape.circle:
      return Path()..addOval(Rect.fromCircle(center: c, radius: r));
    case _Shape.burst:
      // 톱니 12개: 바깥 · 안쪽 반지름을 번갈아 찍고 모서리를 둥글린다
      const teeth = 12;
      return _rounded([
        for (var i = 0; i < teeth * 2; i++) at(-math.pi / 2 + i * math.pi / teeth, i.isEven ? r : r * 0.87),
      ], 0.5);
    case _Shape.hexagon:
      return _rounded([for (var i = 0; i < 6; i++) at(-math.pi / 2 + i * math.pi / 3, r)], 0.2);
    case _Shape.shield:
      return Path()
        ..moveTo(s * 0.5, s * 0.02)
        ..cubicTo(s * 0.64, s * 0.1, s * 0.78, s * 0.13, s * 0.92, s * 0.13)
        ..lineTo(s * 0.92, s * 0.5)
        ..cubicTo(s * 0.92, s * 0.74, s * 0.73, s * 0.9, s * 0.5, s * 0.99)
        ..cubicTo(s * 0.27, s * 0.9, s * 0.08, s * 0.74, s * 0.08, s * 0.5)
        ..lineTo(s * 0.08, s * 0.13)
        ..cubicTo(s * 0.22, s * 0.13, s * 0.36, s * 0.1, s * 0.5, s * 0.02)
        ..close();
  }
}

/// 꼭짓점마다 모서리를 둥글린 다각형. t = 꼭짓점에서 변을 따라 물러나는 비율 (0.5 면 변의 가운데까지)
Path _rounded(List<Offset> points, double t) {
  final path = Path();
  final n = points.length;
  for (var i = 0; i < n; i++) {
    final p = points[i];
    final from = Offset.lerp(p, points[(i - 1 + n) % n], t)!;
    final to = Offset.lerp(p, points[(i + 1) % n], t)!;
    if (i == 0) {
      path.moveTo(from.dx, from.dy);
    } else {
      path.lineTo(from.dx, from.dy);
    }
    path.quadraticBezierTo(p.dx, p.dy, to.dx, to.dy);
  }
  return path..close();
}
