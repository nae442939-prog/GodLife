# 갓생살기 (GodLife)

습관 챌린지 서비스입니다. 운동 · 공부 · 독서 같은 챌린지에 포인트를 걸고, 매일 사진으로 인증합니다.
끝까지 해낸 사람은 건 포인트를 그대로 돌려받고, 해내지 못한 사람의 포인트는 해낸 사람들에게 **보상 포인트**로 나눠집니다.

부트캠프 과제로 만든 풀스택 개인 프로젝트입니다.

## 핵심 설계

**직접 학습한 AI 로 인증 사진을 검증합니다.**
외부 AI API 를 부르지 않습니다. MobileNetV2 를 직접 모은 사진으로 전이학습(파인튜닝)해서, 따로 띄운 FastAPI 서버에서 추론합니다.
사진이 챌린지 카테고리와 맞는지 판정하고, 이미지 임베딩의 유사도로 같은 사진 재사용을 잡아내며, 애매한 사진은 관리자 검토로 넘깁니다.
사진 모양이 제각각인 '기타' 카테고리는 세부 종류(일찍 일어나기 · 산책 · 물 마시기 · 청소/정리 · 식물 가꾸기)로 나눠 학습해, 개설자가 고른 종류로 판정합니다.

**도박이 되지 않도록 닫힌 포인트 경제로 설계했습니다.**
- 결과가 우연이 아니라 본인의 인증(노력)으로 정해집니다.
- 현금 출금 · 환전 기능이 없습니다. 보상 포인트는 포인트 상점에서만 쓸 수 있습니다.
- 예외는 하나뿐입니다: 본인이 직접 충전하고 쓰지 않은 금액의 결제 취소(환불).
  그래서 포인트 원장은 출처(충전 / 보상 / 상점)를 나눠 기록하고, 환불은 충전분만 대상으로 합니다.

**포인트가 두 번 나가지 않게 했습니다.**
정산 · 결제 · 주문은 DB 트랜잭션 + 행 잠금(`SELECT ... FOR UPDATE`) + 멱등 키로 처리합니다.

## 기능

| 영역 | 내용 |
|---|---|
| 계정 | 이메일 가입 · 로그인, 카카오 · 구글 · 네이버 소셜 로그인, 휴대폰 본인인증(계정당 1개), 아이디 · 비밀번호 찾기, 자동 로그인, 회원 탈퇴 |
| 챌린지 | 개설(무료 / 포인트, 매일 / 주 N회, 인증 가능 시간대, 기타 세부 종류), 둘러보기 · 검색 · 정렬, 비공개 챌린지와 초대 링크, 진행 관리(자정 배치) |
| 인증 | 카메라 직촬 사진 인증, 서버 수신 시각 기준 판정, AI 자동 판정, 재사용 사진 탐지, 관리자 검토, 인증 신고 |
| 포인트 | 지갑(충전 · 보상 분리), 거래 원장, 매일 정산 결과 · 종료 시 일괄 지급, 토스페이먼츠 결제(테스트 모드) · 결제 취소 환불 |
| 랭킹 | 전체 · 친구 · 내 챌린지 랭킹, 주간 · 월간 시즌 랭킹(상위 3명 보너스), 칭호(티어) 승급 · 강등 |
| 갓생기록 | 일기장, 성공 · 실패 캘린더, 주간 회고 리포트(성공률 · 요일별 · 챌린지별 · 코칭 문구) |
| 소셜 | 팔로우, 1:1 메시지와 메시지 요청, 챌린지 오픈채팅(WebSocket 실시간 알림), 커뮤니티(글 · 사진 · 댓글 · 좋아요 · 신고), 차단 |
| 포인트 상점 | 상품 · 찜 · 장바구니 · 주문 · 주문 취소, 관리자 상품 · 주문 관리 |
| 알림 | 알림함, 종류별 켜기/끄기, 브라우저 푸시 알림(웹 푸시) |
| 부정행위 방지 | 담합 의심 표시, 기기 핑거프린팅(같은 기기 · IP 다중 계정 표시), 요청 횟수 제한 |
| 관리자 | 인증 검토, 담합 의심, 다중 계정 의심, 커뮤니티 신고, 포인트 상점, 1:1 문의 |

## 기술 스택

| 영역 | 스택 |
|---|---|
| Frontend | React 19 + Vite (JavaScript) |
| Backend | Java 21 + Spring Boot 4 (Maven), Spring Security(JWT), JPA + JDBC, MySQL 8 |
| AI 서버 | Python + FastAPI + PyTorch (MobileNetV2 전이학습 모델 서빙) |

AI 서버를 따로 둔 이유는 Java 에서 PyTorch 로 학습한 모델을 직접 돌릴 수 없기 때문입니다. 백엔드가 HTTP 로 호출하고, AI 서버가 꺼져 있으면 AI 판정만 건너뜁니다.

## 폴더 구조

```
backend/     Spring Boot (기능별 패키지: auth, challenge, verification, settlement, wallet, ...)
frontend/    Vite + React
ai-server/   FastAPI 추론 서버, training/ 에 학습 스크립트와 노트북
db/          00-init.sql(DB · 계정) → 01-schema.sql(테이블) → 02-seed.sql(기준 데이터), migrations/(변경 이력)
docs/        erd.md (스키마에서 자동 생성한 ERD), erd.html (초기 시안)
```

DB 구조는 [docs/erd.md](docs/erd.md) 에 있습니다.

## 실행 방법

준비물: JDK 21, Node.js, Python 3.12, MySQL 8

### 1. DB

```bash
# db/00-init.sql 의 CHANGE_ME 를 본인이 정한 비밀번호로 바꾼 뒤 (바꾼 채로 커밋하지 않습니다)
mysql -u root -p < db/00-init.sql
mysql -u godlife_user -p godlife < db/01-schema.sql
mysql -u godlife_user -p godlife < db/02-seed.sql
```

화면을 채울 더미 데이터(선택)는 `db/dev-dummy.sql` · `db/dev-recruiting.sql` 등으로 넣습니다.
`backend/.env` 에 `DEMO_KEEP_DATES=true` 를 두면 서버가 더미 데이터의 날짜를 매일 오늘에 맞춰 밀어서,
날짜가 지나도 모집 중 · 진행 중인 챌린지와 랭킹이 그대로 보입니다. (실제 회원의 기록은 건드리지 않습니다)

### 2. 백엔드 (http://localhost:8080)

```bash
cp backend/.env.example backend/.env    # 값 채우기: DB 비밀번호, JWT_SECRET, PHONE_HMAC_SECRET 은 필수
cd backend
./mvnw spring-boot:run
```

소셜 로그인 · 결제 · 메일 · 푸시 알림 키는 비워 두어도 서버가 뜹니다. 그 기능만 꺼집니다.

### 3. AI 서버 (http://localhost:8000)

```bash
cd ai-server
python -m venv venv
venv\Scripts\activate                   # macOS / Linux: source venv/bin/activate
pip install -r requirements.txt
uvicorn main:app --port 8000
```

학습한 모델(`ai-server/models/godlife-mobilenetv2.pt`)은 저장소에 들어 있어 바로 추론할 수 있습니다.
모델 파일이 없으면 서버는 뜨지만 판정을 하지 않고, 백엔드는 AI 판정 없이 인증을 받습니다.
다시 학습하려면 `ai-server/training/` 의 스크립트나 노트북을 씁니다 (학습 사진은 저장소에 없습니다).

### 4. 프론트엔드 (http://localhost:5173)

```bash
cd frontend
npm install
npm run dev
```

개발 서버가 `/api` · `/ws` 요청을 백엔드로 넘겨 주므로 브라우저에서는 5173 번만 열면 됩니다.

## 테스트

```bash
cd backend
./mvnw test
```

테스트는 개발 DB 가 아니라 `godlife_test` DB 를 씁니다. 같은 스키마(`01-schema.sql`)와 기준 데이터(`02-seed.sql`)를 넣어 두어야 합니다.
