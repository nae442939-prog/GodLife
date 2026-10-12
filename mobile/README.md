# 갓생살기 모바일 앱 (Flutter)

웹과 같은 백엔드 API 를 쓰는 안드로이드 앱입니다. 웹 전체를 옮기지 않고, 폰에서 매일 쓰는 핵심 흐름만 담았습니다.

| 화면 | 내용 |
|---|---|
| 로그인 | 이메일 로그인, 자동 로그인 (리프레시 쿠키 보관) |
| 둘러보기 | 모집 중인 챌린지 목록, 무료/포인트 · 카테고리로 거르기 |
| 챌린지 상세 | 규칙 · 참가자 · 참여하기 / 참여 취소 |
| 내 챌린지 | 오늘 인증할 챌린지가 맨 위, 진행률 |
| 인증 | **카메라로 바로 찍은 사진만** 올리기 → 서버의 AI 모델이 판정, 오늘 참가자들의 인증 사진 |
| 포인트 지갑 | 충전 포인트 / 보상 포인트, 베팅 한도, 거래 내역 |
| 알림 | 알림함, 읽음 처리, 챌린지로 이동 |
| 마이페이지 | 프로필, 칭호(티어) 점수, 로그아웃 |

회원가입 · 소셜 로그인 · 결제(충전/환불) · 포인트 상점 · 커뮤니티는 웹에서 합니다.

## 구조

```
lib/
  main.dart            앱 시작, 로그인 여부에 따라 화면 나누기
  app_state.dart       로그인 상태 (Provider)
  theme.dart           웹과 같은 색 · 글자
  api/api_client.dart  서버 호출: 토큰 붙이기, 401 이면 한 번 재발급 후 재시도, 사진 올리기
  api/models.dart      서버 응답 모델
  screens/             화면
  widgets/             공용 위젯 (불러오는 중 · 실패 · 당겨서 새로 고침)
test/                  모델 · 표기 단위 테스트
```

인증 방식은 웹과 같습니다. 액세스 토큰은 메모리에만 두고, 리프레시 토큰은 서버가 내려준 HttpOnly 쿠키를
앱의 쿠키 저장소가 보관했다가 인증 경로에만 다시 보냅니다.

## 실행

준비물: Flutter SDK, Android SDK

```bash
cd mobile
flutter pub get
flutter test                      # 단위 테스트
flutter run                       # 연결된 폰 · 에뮬레이터에서 실행
flutter build apk --release       # build/app/outputs/flutter-apk/app-release.apk
```

서버 주소는 기본으로 배포 서버를 봅니다. 다른 서버에 붙이려면:

```bash
flutter run --dart-define=API_BASE=http://10.0.2.2:5173   # 에뮬레이터에서 내 PC 의 개발 서버
```

아이폰처럼 APK 를 깔 수 없는 기기에서는 같은 코드를 웹으로 빌드해 브라우저로 엽니다 (배포 서버의 `/app/`).

```bash
flutter build web --release --base-href /app/
```

