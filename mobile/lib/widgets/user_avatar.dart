import 'package:flutter/material.dart';

import '../api/api_client.dart';
import 'design.dart';

/// 회원의 동그란 사진. 사진이 없거나 못 받으면 닉네임 첫 글자를 보여 준다
class UserAvatar extends StatelessWidget {
  const UserAvatar({super.key, required this.nickname, required this.image, this.size = 38});

  final String nickname;
  final String? image;
  final double size;

  @override
  Widget build(BuildContext context) {
    return Container(
      width: size,
      height: size,
      alignment: Alignment.center,
      clipBehavior: Clip.antiAlias,
      decoration: BoxDecoration(color: ColorTag.mint.$1, shape: BoxShape.circle),
      child: image != null
          ? Image.network('$serverBase$image',
              width: size, height: size, fit: BoxFit.cover, errorBuilder: (_, _, _) => _initial())
          : _initial(),
    );
  }

  Widget _initial() => Text(nickname.isEmpty ? '' : nickname.substring(0, 1),
      style: TextStyle(fontSize: size * 0.38, fontWeight: FontWeight.w800, color: ColorTag.mint.$2));
}
