# 배포 서버 스크립트

배포 서버(`ubuntu@서버`) 홈에 두고 쓰는 스크립트의 사본입니다. 빌드는 내 PC 에서 하고, 결과물을 서버 홈에 올린 뒤 실행합니다.

| 스크립트 | 하는 일 | 서버 홈에 미리 올려 둘 것 |
|---|---|---|
| `deploy-web.sh` | 웹사이트(React 빌드)를 새 폴더에 풀고 이름을 바꿔 끼운다 | `frontend-dist.tgz` (맨 위 폴더 `dist`) |
| `deploy-app.sh` | 앱(Flutter 웹 빌드)을 `/opt/godlife/app` 에 풀고 nginx 를 다시 읽는다 | `flutter-web.tgz` (맨 위 폴더 `web`) |
| `reset-today.sh` | 시연용: 관리자 계정의 오늘 인증만 지워 다시 인증해 볼 수 있게 한다 | |

백엔드는 `/opt/godlife/backend/app.jar` 를 바꾸고 `sudo systemctl restart godlife-backend` 로 다시 띄웁니다 (이전 jar 는 `app.jar.bak` 으로 남겨 둡니다).
DB 변경(`db/migrations/`)이 있으면 jar 를 바꾸기 **전에** 적용합니다.
