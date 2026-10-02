-- GodLife 스키마 (MySQL 8.0 / InnoDB / utf8mb4) — 1~3차 전체, 테이블 41개
-- 설계도: docs/erd.html
-- 실행: mysql -u godlife_user -p godlife < db/01-schema.sql   (빈 DB 기준, DROP 문 없음)
--
-- 규칙
--  * 포인트/금액은 전부 BIGINT (정수 포인트). 잔액의 진실은 point_transactions 합계, wallets.balance 는 캐시 + 락 앵커.
--  * 출금/환전 테이블은 의도적으로 없다 (폐쇄형 포인트 경제, 프로젝트 규칙 2).
--  * 카드정보 컬럼 없음. 결제는 PG 토큰만 저장하며 테스트 모드 고정 (프로젝트 규칙 3).
--  * 삭제는 기본 RESTRICT. 회원은 물리 삭제 대신 status=WITHDRAWN. 토큰류만 회원 삭제 시 CASCADE.

SET NAMES utf8mb4;

-- =====================================================================
-- 01. 계정 · 인증 · 티어
-- =====================================================================

CREATE TABLE tiers (
  id                 INT          NOT NULL AUTO_INCREMENT,
  name               VARCHAR(20)  NOT NULL COMMENT 'BRONZE ~ DIAMOND',
  min_score          INT          NOT NULL COMMENT '승급 기준 누적 점수',
  daily_bet_limit    BIGINT       NOT NULL COMMENT '일일 총 베팅 상한. 저티어일수록 낮음',
  monthly_bet_limit  BIGINT       NOT NULL COMMENT '월간 총 베팅 상한',
  high_stake_allowed BOOLEAN      NOT NULL DEFAULT FALSE COMMENT '고액 베팅방 입장 가능 여부',
  PRIMARY KEY (id),
  UNIQUE KEY uk_tiers_name (name),
  CONSTRAINT ck_tiers_limits CHECK (daily_bet_limit >= 0 AND monthly_bet_limit >= daily_bet_limit)
) ENGINE=InnoDB COMMENT='티어';

CREATE TABLE users (
  id                BIGINT       NOT NULL AUTO_INCREMENT,
  email             VARCHAR(255) NOT NULL COMMENT '로그인 ID',
  password_hash     VARCHAR(255) NULL COMMENT 'BCrypt 해시. 소셜 전용 가입자는 NULL',
  nickname          VARCHAR(30)  NOT NULL,
  profile_image_url VARCHAR(500) NULL,
  bio               VARCHAR(200) NULL COMMENT '자기소개',
  phone_hash        CHAR(64)     NULL COMMENT '휴대폰 번호 HMAC 해시(원문 미저장). UNIQUE = 계정당 1개 → 다중계정 베팅 악용 방지',
  phone_enc         VARCHAR(100) NULL COMMENT '휴대폰 번호 암호화 값 (본인에게만 다시 보여 준다)',
  tier_id           INT          NOT NULL DEFAULT 1 COMMENT '신규 가입자는 최저 티어(id=1, BRONZE)',
  role              ENUM('USER','ADMIN') NOT NULL DEFAULT 'USER',
  status            ENUM('ACTIVE','SUSPENDED','WITHDRAWN') NOT NULL DEFAULT 'ACTIVE',
  created_at        DATETIME     NOT NULL DEFAULT CURRENT_TIMESTAMP,
  PRIMARY KEY (id),
  UNIQUE KEY uk_users_email (email),
  UNIQUE KEY uk_users_nickname (nickname),
  UNIQUE KEY uk_users_phone_hash (phone_hash),
  CONSTRAINT fk_users_tier FOREIGN KEY (tier_id) REFERENCES tiers (id)
) ENGINE=InnoDB COMMENT='회원';

CREATE TABLE social_accounts (
  id               BIGINT       NOT NULL AUTO_INCREMENT,
  user_id          BIGINT       NOT NULL,
  provider         ENUM('KAKAO','GOOGLE','NAVER') NOT NULL,
  provider_user_id VARCHAR(100) NOT NULL,
  linked_at        DATETIME     NOT NULL DEFAULT CURRENT_TIMESTAMP,
  PRIMARY KEY (id),
  UNIQUE KEY uk_social_provider_user (provider, provider_user_id),
  KEY idx_social_user (user_id),
  CONSTRAINT fk_social_user FOREIGN KEY (user_id) REFERENCES users (id) ON DELETE CASCADE
) ENGINE=InnoDB COMMENT='소셜 로그인 연동';

CREATE TABLE phone_verifications (
  id            BIGINT   NOT NULL AUTO_INCREMENT,
  phone_hash    CHAR(64) NOT NULL,
  phone_enc     VARCHAR(100) NULL COMMENT '인증하는 번호의 암호화 값 (인증이 끝나면 users.phone_enc 로 옮긴다)',
  code_hash     CHAR(64) NOT NULL COMMENT 'SMS 인증번호 해시',
  attempt_count TINYINT  NOT NULL DEFAULT 0 COMMENT '시도 횟수 제한',
  expires_at    DATETIME NOT NULL,
  verified_at   DATETIME NULL,
  proof_hash    CHAR(64) NULL COMMENT '인증 완료 후 발급하는 1회용 증표 해시. 가입/아이디 찾기/번호 등록에 제출',
  proof_used_at DATETIME NULL COMMENT '증표 사용 시각 (재사용 방지)',
  user_id       BIGINT   NULL COMMENT '가입 도중에는 아직 user 가 없을 수 있어 NULL 허용',
  PRIMARY KEY (id),
  UNIQUE KEY uk_phone_ver_proof (proof_hash),
  KEY idx_phone_ver_phone (phone_hash, expires_at),
  KEY idx_phone_ver_user (user_id),
  CONSTRAINT fk_phone_ver_user FOREIGN KEY (user_id) REFERENCES users (id) ON DELETE CASCADE
) ENGINE=InnoDB COMMENT='휴대폰 본인인증(SMS)';

CREATE TABLE devices (
  id               BIGINT       NOT NULL AUTO_INCREMENT,
  user_id          BIGINT       NOT NULL,
  fingerprint_hash CHAR(64)     NOT NULL COMMENT '기기 핑거프린트 해시',
  ip_address       VARCHAR(45)  NOT NULL,
  user_agent       VARCHAR(255) NULL,
  first_seen_at    DATETIME     NOT NULL DEFAULT CURRENT_TIMESTAMP,
  last_seen_at     DATETIME     NOT NULL DEFAULT CURRENT_TIMESTAMP,
  PRIMARY KEY (id),
  UNIQUE KEY uk_devices_user_fp (user_id, fingerprint_hash),
  KEY idx_devices_fp (fingerprint_hash),
  KEY idx_devices_ip (ip_address),
  CONSTRAINT fk_devices_user FOREIGN KEY (user_id) REFERENCES users (id)
) ENGINE=InnoDB COMMENT='기기 지문 (같은 기기/IP 대량 가입 탐지)';

CREATE TABLE refresh_tokens (
  id         BIGINT   NOT NULL AUTO_INCREMENT,
  user_id    BIGINT   NOT NULL,
  device_id  BIGINT   NULL,
  token_hash CHAR(64) NOT NULL COMMENT '토큰 원문 미저장',
  expires_at DATETIME NOT NULL,
  revoked_at DATETIME NULL COMMENT '로그아웃 = 현재 기기 토큰만 폐기',
  PRIMARY KEY (id),
  UNIQUE KEY uk_refresh_token_hash (token_hash),
  KEY idx_refresh_user (user_id),
  KEY idx_refresh_device (device_id),
  CONSTRAINT fk_refresh_user FOREIGN KEY (user_id) REFERENCES users (id) ON DELETE CASCADE,
  CONSTRAINT fk_refresh_device FOREIGN KEY (device_id) REFERENCES devices (id) ON DELETE SET NULL
) ENGINE=InnoDB COMMENT='리프레시 토큰';

CREATE TABLE password_reset_tokens (
  id            BIGINT   NOT NULL AUTO_INCREMENT,
  user_id       BIGINT   NOT NULL,
  code_hash     CHAR(64) NOT NULL COMMENT '이메일로 보낸 6자리 인증번호 해시',
  attempt_count TINYINT  NOT NULL DEFAULT 0 COMMENT '인증번호 시도 횟수 제한',
  token_hash    CHAR(64) NULL COMMENT '인증번호 확인 후 발급하는 1회용 재설정 토큰 해시',
  expires_at    DATETIME NOT NULL,
  used_at       DATETIME NULL,
  PRIMARY KEY (id),
  UNIQUE KEY uk_pwreset_token_hash (token_hash),
  KEY idx_pwreset_user (user_id),
  CONSTRAINT fk_pwreset_user FOREIGN KEY (user_id) REFERENCES users (id) ON DELETE CASCADE
) ENGINE=InnoDB COMMENT='비밀번호 재설정 토큰';

-- =====================================================================
-- 02. 챌린지 코어
-- =====================================================================

CREATE TABLE categories (
  id        INT         NOT NULL AUTO_INCREMENT,
  name      VARCHAR(30) NOT NULL COMMENT '운동 · 공부 · 독서 …',
  ai_label  VARCHAR(50) NOT NULL COMMENT 'AI 분류 모델 클래스 라벨과 1:1 매핑',
  is_active BOOLEAN     NOT NULL DEFAULT TRUE,
  PRIMARY KEY (id),
  UNIQUE KEY uk_categories_name (name),
  UNIQUE KEY uk_categories_ai_label (ai_label)
) ENGINE=InnoDB COMMENT='챌린지 카테고리';

CREATE TABLE challenges (
  id                BIGINT       NOT NULL AUTO_INCREMENT,
  host_id           BIGINT       NOT NULL COMMENT '개설자',
  category_id       INT          NOT NULL,
  title             VARCHAR(100) NOT NULL,
  description       TEXT         NOT NULL,
  notice            VARCHAR(300) NULL COMMENT '방장 공지. 채팅방 맨 위 고정',
  notice_updated_at DATETIME     NULL,
  mode              ENUM('FREE','BET') NOT NULL COMMENT 'FREE(무료) · BET(베팅)',
  visibility        ENUM('PUBLIC','PRIVATE') NOT NULL DEFAULT 'PUBLIC' COMMENT 'PRIVATE = 목록에 안 나오고 초대 링크로만 참여',
  invite_code       CHAR(8)      NOT NULL COMMENT '초대 링크 코드. 헷갈리는 글자(0/O/1/I/L) 없는 영숫자',
  start_date        DATE         NOT NULL,
  end_date          DATE         NOT NULL,
  frequency_type    ENUM('DAILY','WEEKLY_N') NOT NULL,
  weekly_count      TINYINT      NULL COMMENT 'WEEKLY_N 일 때 주 N회',
  entry_fee         BIGINT       NOT NULL DEFAULT 0 COMMENT '개설자가 정한 참가비(예치 포인트). FREE 모드는 0',
  min_bet           BIGINT       NOT NULL DEFAULT 0 COMMENT '1회 최소 예치 포인트',
  max_bet           BIGINT       NOT NULL DEFAULT 0 COMMENT '1회 최대 예치 포인트',
  max_participants  INT          NOT NULL,
  participant_count INT          NOT NULL DEFAULT 0 COMMENT '인기순 정렬용 비정규화 카운터',
  verify_from       TIME         NULL COMMENT '인증 가능 시간대(옵션). 예: 새벽 기상 ~06시',
  verify_until      TIME         NULL,
  partial_refund    BOOLEAN      NOT NULL DEFAULT FALSE COMMENT '부분 성공 시 비례 환급 옵션',
  status            ENUM('RECRUITING','ONGOING','ENDED','SETTLED') NOT NULL DEFAULT 'RECRUITING',
  created_at        DATETIME     NOT NULL DEFAULT CURRENT_TIMESTAMP,
  PRIMARY KEY (id),
  KEY idx_challenges_category_status (category_id, status),
  KEY idx_challenges_end_date (end_date),
  KEY idx_challenges_host (host_id),
  KEY idx_challenges_popular (status, participant_count),
  UNIQUE KEY uk_challenges_invite_code (invite_code),
  CONSTRAINT fk_challenges_host FOREIGN KEY (host_id) REFERENCES users (id),
  CONSTRAINT fk_challenges_category FOREIGN KEY (category_id) REFERENCES categories (id),
  CONSTRAINT ck_challenges_period CHECK (end_date >= start_date),
  CONSTRAINT ck_challenges_capacity CHECK (max_participants >= 1 AND participant_count >= 0),
  CONSTRAINT ck_challenges_frequency CHECK (
    (frequency_type = 'DAILY' AND weekly_count IS NULL)
    OR (frequency_type = 'WEEKLY_N' AND weekly_count BETWEEN 1 AND 7)),
  CONSTRAINT ck_challenges_mode_bet CHECK (
    (mode = 'FREE' AND entry_fee = 0 AND min_bet = 0 AND max_bet = 0)
    OR (mode = 'BET' AND min_bet > 0 AND min_bet <= entry_fee AND entry_fee <= max_bet)),
  CONSTRAINT ck_challenges_verify_window CHECK (
    (verify_from IS NULL AND verify_until IS NULL)
    OR (verify_from IS NOT NULL AND verify_until IS NOT NULL))
) ENGINE=InnoDB COMMENT='챌린지';

CREATE TABLE challenge_participants (
  id             BIGINT   NOT NULL AUTO_INCREMENT,
  challenge_id   BIGINT   NOT NULL,
  user_id        BIGINT   NOT NULL,
  deposit_amount BIGINT   NOT NULL DEFAULT 0 COMMENT '참가 시점 예치 포인트 스냅샷',
  status         ENUM('ACTIVE','COMPLETED','FAILED','GAVE_UP','LEFT','KICKED') NOT NULL DEFAULT 'ACTIVE' COMMENT 'COMPLETED/FAILED = 종료 시 판정, GAVE_UP = 진행 중 포기, KICKED = 방장이 내보냄',
  success_days   INT      NOT NULL DEFAULT 0 COMMENT '인증 성공 일수 (승인 시 증가)',
  current_streak INT      NOT NULL DEFAULT 0,
  max_streak     INT      NOT NULL DEFAULT 0,
  joined_at      DATETIME NOT NULL DEFAULT CURRENT_TIMESTAMP,
  PRIMARY KEY (id),
  UNIQUE KEY uk_participants_challenge_user (challenge_id, user_id),
  KEY idx_participants_ranking (challenge_id, success_days DESC, max_streak DESC),
  KEY idx_participants_user (user_id),
  CONSTRAINT fk_participants_challenge FOREIGN KEY (challenge_id) REFERENCES challenges (id),
  CONSTRAINT fk_participants_user FOREIGN KEY (user_id) REFERENCES users (id),
  CONSTRAINT ck_participants_counts CHECK (deposit_amount >= 0 AND success_days >= 0 AND current_streak >= 0 AND max_streak >= current_streak)
) ENGINE=InnoDB COMMENT='챌린지 참가자';

CREATE TABLE chat_messages (
  id           BIGINT       NOT NULL AUTO_INCREMENT,
  challenge_id BIGINT       NOT NULL COMMENT '챌린지 1개 = 채팅방 1개',
  sender_id    BIGINT       NOT NULL,
  type         ENUM('USER','SYSTEM') NOT NULL DEFAULT 'USER' COMMENT 'SYSTEM = 강퇴·공지 알림 (sender 는 방장)',
  content      VARCHAR(500) NULL COMMENT '글. 사진만 보낸 메시지는 NULL',
  image_key    VARCHAR(80)  NULL COMMENT '사진 파일 키(서버가 만든 이름). 참가자만 내려받을 수 있다',
  created_at   DATETIME(3)  NOT NULL DEFAULT CURRENT_TIMESTAMP(3),
  PRIMARY KEY (id),
  KEY idx_chat_challenge_id (challenge_id, id) COMMENT '방별로 id N 이후/이전 메시지 (폴링 커서)',
  CONSTRAINT fk_chat_challenge FOREIGN KEY (challenge_id) REFERENCES challenges (id),
  CONSTRAINT fk_chat_sender FOREIGN KEY (sender_id) REFERENCES users (id)
) ENGINE=InnoDB COMMENT='챌린지 오픈채팅 메시지';

CREATE TABLE user_blocks (
  id         BIGINT   NOT NULL AUTO_INCREMENT,
  blocker_id BIGINT   NOT NULL,
  blocked_id BIGINT   NOT NULL,
  created_at DATETIME NOT NULL DEFAULT CURRENT_TIMESTAMP,
  PRIMARY KEY (id),
  UNIQUE KEY uk_user_blocks (blocker_id, blocked_id),
  CONSTRAINT fk_user_blocks_blocker FOREIGN KEY (blocker_id) REFERENCES users (id),
  CONSTRAINT fk_user_blocks_blocked FOREIGN KEY (blocked_id) REFERENCES users (id),
  CONSTRAINT ck_user_blocks_self CHECK (blocker_id <> blocked_id)
) ENGINE=InnoDB COMMENT='사용자 차단';


CREATE TABLE chat_reports (
  id               BIGINT       NOT NULL AUTO_INCREMENT,
  challenge_id     BIGINT       NOT NULL,
  message_id       BIGINT       NOT NULL,
  reporter_id      BIGINT       NOT NULL,
  reported_user_id BIGINT       NOT NULL,
  reason           ENUM('ABUSE','SPAM','INAPPROPRIATE','OTHER') NOT NULL
                     COMMENT '욕설·비방 / 스팸·광고 / 부적절한 내용 / 기타',
  detail           VARCHAR(300) NULL,
  status           ENUM('OPEN','RESOLVED') NOT NULL DEFAULT 'OPEN' COMMENT 'RESOLVED = 방장이 강퇴하거나 넘김',
  created_at       DATETIME     NOT NULL DEFAULT CURRENT_TIMESTAMP,
  PRIMARY KEY (id),
  UNIQUE KEY uk_chat_reports_message_reporter (message_id, reporter_id),
  KEY idx_chat_reports_target (challenge_id, reported_user_id, status),
  CONSTRAINT fk_chat_reports_challenge FOREIGN KEY (challenge_id) REFERENCES challenges (id),
  CONSTRAINT fk_chat_reports_message FOREIGN KEY (message_id) REFERENCES chat_messages (id),
  CONSTRAINT fk_chat_reports_reporter FOREIGN KEY (reporter_id) REFERENCES users (id),
  CONSTRAINT fk_chat_reports_reported FOREIGN KEY (reported_user_id) REFERENCES users (id)
) ENGINE=InnoDB COMMENT='오픈채팅 신고';

-- =====================================================================
-- 03. 인증 · AI 검증
-- =====================================================================

CREATE TABLE verifications (
  id             BIGINT       NOT NULL AUTO_INCREMENT,
  participant_id BIGINT       NOT NULL,
  verify_date    DATE         NOT NULL COMMENT '서버 수신 시각 기준 날짜',
  received_at    DATETIME(3)  NOT NULL DEFAULT CURRENT_TIMESTAMP(3) COMMENT '서버 수신 시각. 클라이언트 시각은 신뢰하지 않음',
  image_url      VARCHAR(500) NOT NULL COMMENT '카메라 직촬 사진 (갤러리 업로드 차단)',
  image_hash     CHAR(64)     NOT NULL COMMENT 'SHA-256. 바이트 단위 동일 사진 즉시 탐지',
  status         ENUM('PENDING','APPROVED','REJECTED','IN_REVIEW') NOT NULL DEFAULT 'PENDING',
  reject_reason  VARCHAR(100) NULL,
  PRIMARY KEY (id),
  UNIQUE KEY uk_verifications_participant_date (participant_id, verify_date),
  KEY idx_verifications_image_hash (image_hash),
  KEY idx_verifications_status (status),
  CONSTRAINT fk_verifications_participant FOREIGN KEY (participant_id) REFERENCES challenge_participants (id)
) ENGINE=InnoDB COMMENT='인증 제출';

CREATE TABLE ai_inference_results (
  id              BIGINT        NOT NULL AUTO_INCREMENT,
  verification_id BIGINT        NOT NULL,
  model_version   VARCHAR(30)   NOT NULL COMMENT '파인튜닝 모델 버전 (직접 학습, ai-server 서빙)',
  predicted_label VARCHAR(50)   NOT NULL,
  confidence      DECIMAL(5,4)  NOT NULL,
  category_match  BOOLEAN       NOT NULL COMMENT '예측 라벨 == 챌린지 카테고리 ai_label',
  max_similarity  DECIMAL(5,4)  NULL COMMENT '기존 임베딩과의 최대 코사인 유사도',
  duplicate_of_id BIGINT        NULL COMMENT '가장 유사했던 과거 인증 (재사용 의심)',
  decision        ENUM('AUTO_PASS','AUTO_REJECT','NEED_REVIEW') NOT NULL,
  inferred_at     DATETIME      NOT NULL DEFAULT CURRENT_TIMESTAMP,
  PRIMARY KEY (id),
  UNIQUE KEY uk_ai_result_verification (verification_id),
  KEY idx_ai_result_duplicate (duplicate_of_id),
  CONSTRAINT fk_ai_result_verification FOREIGN KEY (verification_id) REFERENCES verifications (id),
  CONSTRAINT fk_ai_result_duplicate FOREIGN KEY (duplicate_of_id) REFERENCES verifications (id),
  CONSTRAINT ck_ai_result_scores CHECK (confidence BETWEEN 0 AND 1 AND (max_similarity IS NULL OR max_similarity BETWEEN -1 AND 1))
) ENGINE=InnoDB COMMENT='AI 판정 결과';

CREATE TABLE image_embeddings (
  verification_id BIGINT       NOT NULL,
  embedding       VARBINARY(5120) NOT NULL COMMENT 'MobileNetV2 중간층 벡터(1280 x float32). 코사인 유사도 비교용',
  model_version   VARCHAR(30)  NOT NULL,
  PRIMARY KEY (verification_id),
  CONSTRAINT fk_embeddings_verification FOREIGN KEY (verification_id) REFERENCES verifications (id)
) ENGINE=InnoDB COMMENT='이미지 임베딩';

CREATE TABLE review_queue (
  id              BIGINT       NOT NULL AUTO_INCREMENT,
  verification_id BIGINT       NOT NULL,
  reason          ENUM('LOW_CONFIDENCE','DUPLICATE_SUSPECT','REPORTED') NOT NULL,
  status          ENUM('OPEN','APPROVED','REJECTED') NOT NULL DEFAULT 'OPEN',
  reviewer_id     BIGINT       NULL,
  reviewed_at     DATETIME     NULL,
  memo            VARCHAR(200) NULL,
  PRIMARY KEY (id),
  UNIQUE KEY uk_review_verification (verification_id),
  KEY idx_review_status (status),
  KEY idx_review_reviewer (reviewer_id),
  CONSTRAINT fk_review_verification FOREIGN KEY (verification_id) REFERENCES verifications (id),
  CONSTRAINT fk_review_reviewer FOREIGN KEY (reviewer_id) REFERENCES users (id)
) ENGINE=InnoDB COMMENT='관리자 검토 큐';

CREATE TABLE reports (
  id              BIGINT       NOT NULL AUTO_INCREMENT,
  verification_id BIGINT       NOT NULL,
  reporter_id     BIGINT       NOT NULL,
  reason          VARCHAR(200) NOT NULL,
  status          ENUM('OPEN','ACCEPTED','DISMISSED') NOT NULL DEFAULT 'OPEN',
  created_at      DATETIME     NOT NULL DEFAULT CURRENT_TIMESTAMP,
  PRIMARY KEY (id),
  UNIQUE KEY uk_reports_verification_reporter (verification_id, reporter_id),
  KEY idx_reports_reporter (reporter_id),
  CONSTRAINT fk_reports_verification FOREIGN KEY (verification_id) REFERENCES verifications (id),
  CONSTRAINT fk_reports_reporter FOREIGN KEY (reporter_id) REFERENCES users (id)
) ENGINE=InnoDB COMMENT='인증 신고';

-- =====================================================================
-- 04. 포인트 · 원장 · 정산
-- =====================================================================

CREATE TABLE wallets (
  id         BIGINT   NOT NULL AUTO_INCREMENT,
  user_id    BIGINT   NOT NULL,
  balance    BIGINT   NOT NULL DEFAULT 0 COMMENT '전체 잔액 캐시 (= charged + reward). 진실의 원천은 point_transactions. SELECT ... FOR UPDATE 락 앵커',
  charged_balance BIGINT NOT NULL DEFAULT 0 COMMENT '직접 충전한 포인트. 쓰지 않은 만큼만 결제 취소 환불 가능',
  reward_balance  BIGINT NOT NULL DEFAULT 0 COMMENT '챌린지 보상·이벤트 포인트. 상점에서만 사용, 환불·현금화 불가',
  updated_at DATETIME NOT NULL DEFAULT CURRENT_TIMESTAMP ON UPDATE CURRENT_TIMESTAMP,
  PRIMARY KEY (id),
  UNIQUE KEY uk_wallets_user (user_id),
  CONSTRAINT fk_wallets_user FOREIGN KEY (user_id) REFERENCES users (id),
  CONSTRAINT ck_wallets_balance CHECK (balance >= 0),
  CONSTRAINT ck_wallets_sources CHECK (charged_balance >= 0 AND reward_balance >= 0 AND balance = charged_balance + reward_balance)
) ENGINE=InnoDB COMMENT='포인트 지갑';

CREATE TABLE point_transactions (
  id              BIGINT      NOT NULL AUTO_INCREMENT,
  wallet_id       BIGINT      NOT NULL,
  type            ENUM('CHARGE','CHARGE_CANCEL','ENTRY_FEE','REFUND','REWARD','PURCHASE','SEASON_BONUS','ADJUST') NOT NULL COMMENT 'CHARGE_CANCEL = 충전 포인트 환불(결제 취소). REFUND = 챌린지 참가비 환급',
  source          ENUM('CHARGED','REWARD','SHOP') NOT NULL COMMENT '어느 출처의 포인트가 움직였는지. 환불 로직은 CHARGED 만 본다',
  amount          BIGINT      NOT NULL COMMENT '부호 있는 증감액 (+/-)',
  balance_after   BIGINT      NOT NULL COMMENT '거래 후 그 출처(source)의 잔액',
  ref_type        VARCHAR(20) NULL COMMENT '다형 참조 (participants / settlement_items / payments / orders)',
  ref_id          BIGINT      NULL COMMENT 'FK 없음 - 원장은 참조 대상이 삭제돼도 남아야 함',
  idempotency_key VARCHAR(80) NOT NULL COMMENT '같은 요청 재시도 시 이중 지급/차감 방지',
  created_at      DATETIME    NOT NULL DEFAULT CURRENT_TIMESTAMP,
  PRIMARY KEY (id),
  UNIQUE KEY uk_ptx_idempotency (idempotency_key),
  KEY idx_ptx_wallet_created (wallet_id, created_at),
  KEY idx_ptx_wallet_type_created (wallet_id, type, created_at),
  KEY idx_ptx_ref (ref_type, ref_id),
  CONSTRAINT fk_ptx_wallet FOREIGN KEY (wallet_id) REFERENCES wallets (id),
  CONSTRAINT ck_ptx_amount CHECK (amount <> 0 AND balance_after >= 0)
) ENGINE=InnoDB COMMENT='거래 원장 (INSERT-only, UPDATE/DELETE 금지)';

CREATE TABLE settlements (
  id             BIGINT   NOT NULL AUTO_INCREMENT,
  challenge_id   BIGINT   NOT NULL COMMENT '챌린지당 정산 1회 - 배치가 두 번 돌아도 중복 정산 불가',
  status         ENUM('PENDING','DONE') NOT NULL DEFAULT 'PENDING',
  total_pool     BIGINT   NOT NULL DEFAULT 0 COMMENT '전체 예치금 합계',
  forfeited_pool BIGINT   NOT NULL DEFAULT 0 COMMENT '실패자 몰수분 (재분배 원천)',
  distributed    BIGINT   NOT NULL DEFAULT 0,
  reward_cap     BIGINT   NOT NULL COMMENT '적용된 1회 정산당 최대 획득 상한',
  settled_at     DATETIME NULL COMMENT '매일 자정 cron 배치가 기록',
  PRIMARY KEY (id),
  UNIQUE KEY uk_settlements_challenge (challenge_id),
  CONSTRAINT fk_settlements_challenge FOREIGN KEY (challenge_id) REFERENCES challenges (id),
  CONSTRAINT ck_settlements_amounts CHECK (total_pool >= 0 AND forfeited_pool >= 0 AND distributed >= 0 AND reward_cap >= 0)
) ENGINE=InnoDB COMMENT='챌린지 정산';

CREATE TABLE settlement_items (
  id             BIGINT       NOT NULL AUTO_INCREMENT,
  settlement_id  BIGINT       NOT NULL,
  participant_id BIGINT       NOT NULL,
  success_rate   DECIMAL(5,2) NOT NULL,
  refund_amount  BIGINT       NOT NULL DEFAULT 0 COMMENT '예치금 환급 (부분 성공 시 비례)',
  reward_amount  BIGINT       NOT NULL DEFAULT 0 COMMENT '실패자 포인트 성공률 비례 배분분',
  forfeit_amount BIGINT       NOT NULL DEFAULT 0 COMMENT '몰수액. 지갑 잔액이 변하지 않아 원장 타입이 아닌 여기에 기록',
  capped         BOOLEAN      NOT NULL DEFAULT FALSE COMMENT '획득 상한에 걸렸는지',
  refund_tx_id   BIGINT       NULL COMMENT '지급 원장 추적',
  reward_tx_id   BIGINT       NULL,
  PRIMARY KEY (id),
  UNIQUE KEY uk_sitems_settlement_participant (settlement_id, participant_id),
  KEY idx_sitems_participant (participant_id),
  KEY idx_sitems_refund_tx (refund_tx_id),
  KEY idx_sitems_reward_tx (reward_tx_id),
  CONSTRAINT fk_sitems_settlement FOREIGN KEY (settlement_id) REFERENCES settlements (id),
  CONSTRAINT fk_sitems_participant FOREIGN KEY (participant_id) REFERENCES challenge_participants (id),
  CONSTRAINT fk_sitems_refund_tx FOREIGN KEY (refund_tx_id) REFERENCES point_transactions (id),
  CONSTRAINT fk_sitems_reward_tx FOREIGN KEY (reward_tx_id) REFERENCES point_transactions (id),
  CONSTRAINT ck_sitems_amounts CHECK (success_rate BETWEEN 0 AND 100 AND refund_amount >= 0 AND reward_amount >= 0 AND forfeit_amount >= 0)
) ENGINE=InnoDB COMMENT='정산 상세';

-- 매일 결과 (포인트 챌린지): 하루(주 N회는 한 주) 몫 단위로 성공자 환급 예정 + 실패분을 그날 성공자에게 보상 예정. 지급은 끝날 때 한 번에
CREATE TABLE daily_settlements (
  id             BIGINT   NOT NULL AUTO_INCREMENT,
  challenge_id   BIGINT   NOT NULL,
  period_start   DATE     NOT NULL COMMENT '매일 챌린지는 그날, 주 N회는 그 주 첫날 (시작일부터 7일씩)',
  period_end     DATE     NOT NULL,
  success_count  INT      NOT NULL COMMENT '그 기간 목표를 채운 사람 수',
  fail_count     INT      NOT NULL COMMENT '못 채운 사람 수 (포기한 사람 포함)',
  forfeited_pool BIGINT   NOT NULL COMMENT '못 채운 사람들이 잃은 포인트 합',
  reward_share   BIGINT   NOT NULL COMMENT '성공한 사람 한 명이 받을 보상 (상한 적용 후, 끝날 때 지급)',
  distributed    BIGINT   NOT NULL COMMENT '나눠 줄 보상 합 (나머지·상한 초과분은 나누지 않음)',
  settled_at     DATETIME NOT NULL,
  PRIMARY KEY (id),
  UNIQUE KEY uk_daily_settlement (challenge_id, period_start),
  CONSTRAINT fk_daily_settlement_challenge FOREIGN KEY (challenge_id) REFERENCES challenges (id),
  CONSTRAINT ck_daily_settlement CHECK (success_count >= 0 AND fail_count >= 0 AND forfeited_pool >= 0
    AND reward_share >= 0 AND distributed >= 0 AND distributed <= forfeited_pool)
) ENGINE=InnoDB COMMENT='매일(주) 결과 (지급은 챌린지 종료 시 settlements 로 한 번에)';

-- =====================================================================
-- 05. 결제 (테스트 모드 전용)
-- =====================================================================

CREATE TABLE payments (
  id             BIGINT       NOT NULL AUTO_INCREMENT,
  user_id        BIGINT       NOT NULL,
  provider       ENUM('TOSS','KAKAOPAY') NOT NULL,
  order_id       VARCHAR(64)  NOT NULL COMMENT '멱등키 역할. 같은 주문 재처리 방지',
  payment_key    VARCHAR(200) NOT NULL COMMENT 'PG 결제키/토큰만 저장. 카드정보 컬럼 없음',
  amount         BIGINT       NOT NULL COMMENT '결제 금액(원)',
  points_granted BIGINT       NOT NULL DEFAULT 0,
  status         ENUM('READY','PAID','FAILED','CANCELED') NOT NULL DEFAULT 'READY',
  is_test        BOOLEAN      NOT NULL DEFAULT TRUE COMMENT '샌드박스 결제 여부. 테스트 모드 전용이라 항상 TRUE (프로젝트 규칙 3)',
  transaction_id BIGINT       NULL COMMENT '충전 원장 연결',
  paid_at        DATETIME     NULL,
  created_at     DATETIME     NOT NULL DEFAULT CURRENT_TIMESTAMP,
  PRIMARY KEY (id),
  UNIQUE KEY uk_payments_order (order_id),
  UNIQUE KEY uk_payments_key (payment_key),
  UNIQUE KEY uk_payments_tx (transaction_id),
  KEY idx_payments_user (user_id),
  CONSTRAINT fk_payments_user FOREIGN KEY (user_id) REFERENCES users (id),
  CONSTRAINT fk_payments_tx FOREIGN KEY (transaction_id) REFERENCES point_transactions (id),
  CONSTRAINT ck_payments_test_only CHECK (is_test = TRUE),
  CONSTRAINT ck_payments_amount CHECK (amount > 0 AND points_granted >= 0)
) ENGINE=InnoDB COMMENT='포인트 충전 결제 (PG 샌드박스)';

-- =====================================================================
-- 06. 랭킹 · 시즌
-- =====================================================================

CREATE TABLE user_stats (
  user_id             BIGINT       NOT NULL,
  total_verifications INT          NOT NULL DEFAULT 0,
  total_success       INT          NOT NULL DEFAULT 0,
  success_rate        DECIMAL(5,2) NOT NULL DEFAULT 0,
  current_streak      INT          NOT NULL DEFAULT 0,
  max_streak          INT          NOT NULL DEFAULT 0,
  month_points        BIGINT       NOT NULL DEFAULT 0 COMMENT '이번 달 획득 포인트',
  month_key           CHAR(7)      NOT NULL COMMENT '예: 2026-09',
  updated_at          DATETIME     NOT NULL DEFAULT CURRENT_TIMESTAMP ON UPDATE CURRENT_TIMESTAMP,
  PRIMARY KEY (user_id),
  KEY idx_stats_success_rate (success_rate DESC),
  KEY idx_stats_max_streak (max_streak DESC),
  KEY idx_stats_month_points (month_key, month_points DESC),
  CONSTRAINT fk_stats_user FOREIGN KEY (user_id) REFERENCES users (id)
) ENGINE=InnoDB COMMENT='개인 통계 (집계 테이블)';

CREATE TABLE seasons (
  id         BIGINT   NOT NULL AUTO_INCREMENT,
  type       ENUM('WEEKLY','MONTHLY') NOT NULL,
  start_date DATE     NOT NULL,
  end_date   DATE     NOT NULL,
  status     ENUM('ACTIVE','CLOSED') NOT NULL DEFAULT 'ACTIVE',
  bonus_paid BOOLEAN  NOT NULL DEFAULT FALSE COMMENT '시즌 종료 보너스 지급 완료 여부',
  PRIMARY KEY (id),
  UNIQUE KEY uk_seasons_type_start (type, start_date),
  CONSTRAINT ck_seasons_period CHECK (end_date >= start_date)
) ENGINE=InnoDB COMMENT='시즌';

CREATE TABLE season_rankings (
  id           BIGINT NOT NULL AUTO_INCREMENT,
  season_id    BIGINT NOT NULL,
  user_id      BIGINT NOT NULL,
  score        INT    NOT NULL DEFAULT 0,
  rank_no      INT    NULL COMMENT '시즌 종료 시 확정',
  bonus_points BIGINT NOT NULL DEFAULT 0 COMMENT '상위권 보너스',
  PRIMARY KEY (id),
  UNIQUE KEY uk_season_rank_user (season_id, user_id),
  KEY idx_season_rank_no (season_id, rank_no),
  KEY idx_season_rank_score (season_id, score DESC),
  KEY idx_season_rank_user (user_id),
  CONSTRAINT fk_season_rank_season FOREIGN KEY (season_id) REFERENCES seasons (id),
  CONSTRAINT fk_season_rank_user FOREIGN KEY (user_id) REFERENCES users (id)
) ENGINE=InnoDB COMMENT='시즌 랭킹';

-- =====================================================================
-- 07. 부정행위 방지
-- =====================================================================

CREATE TABLE collusion_flags (
  id             BIGINT       NOT NULL AUTO_INCREMENT,
  challenge_id   BIGINT       NOT NULL,
  user_a_id      BIGINT       NOT NULL COMMENT '항상 user_a_id < user_b_id 로 정규화',
  user_b_id      BIGINT       NOT NULL,
  co_match_count INT          NOT NULL COMMENT '같은 조합이 함께 매칭된 횟수',
  score          DECIMAL(5,2) NOT NULL,
  status         ENUM('OPEN','CONFIRMED','DISMISSED') NOT NULL DEFAULT 'OPEN',
  detected_at    DATETIME     NOT NULL DEFAULT CURRENT_TIMESTAMP,
  PRIMARY KEY (id),
  UNIQUE KEY uk_collusion_pair (challenge_id, user_a_id, user_b_id),
  KEY idx_collusion_user_a (user_a_id),
  KEY idx_collusion_user_b (user_b_id),
  CONSTRAINT fk_collusion_challenge FOREIGN KEY (challenge_id) REFERENCES challenges (id),
  CONSTRAINT fk_collusion_user_a FOREIGN KEY (user_a_id) REFERENCES users (id),
  CONSTRAINT fk_collusion_user_b FOREIGN KEY (user_b_id) REFERENCES users (id),
  CONSTRAINT ck_collusion_order CHECK (user_a_id < user_b_id)
) ENGINE=InnoDB COMMENT='담합 의심 플래그';

-- =====================================================================
-- 08. 소셜 · 배지
-- =====================================================================

CREATE TABLE badges (
  id          INT          NOT NULL AUTO_INCREMENT,
  code        VARCHAR(30)  NOT NULL,
  name        VARCHAR(50)  NOT NULL,
  description VARCHAR(200) NOT NULL,
  rule_json   JSON         NOT NULL COMMENT '획득 조건',
  PRIMARY KEY (id),
  UNIQUE KEY uk_badges_code (code)
) ENGINE=InnoDB COMMENT='배지';

CREATE TABLE user_badges (
  user_id   BIGINT   NOT NULL,
  badge_id  INT      NOT NULL,
  earned_at DATETIME NOT NULL DEFAULT CURRENT_TIMESTAMP,
  PRIMARY KEY (user_id, badge_id),
  KEY idx_user_badges_badge (badge_id),
  CONSTRAINT fk_user_badges_user FOREIGN KEY (user_id) REFERENCES users (id),
  CONSTRAINT fk_user_badges_badge FOREIGN KEY (badge_id) REFERENCES badges (id)
) ENGINE=InnoDB COMMENT='회원 배지';

CREATE TABLE follows (
  follower_id  BIGINT   NOT NULL,
  following_id BIGINT   NOT NULL,
  created_at   DATETIME NOT NULL DEFAULT CURRENT_TIMESTAMP,
  PRIMARY KEY (follower_id, following_id),
  KEY idx_follows_following (following_id),
  CONSTRAINT fk_follows_follower FOREIGN KEY (follower_id) REFERENCES users (id),
  CONSTRAINT fk_follows_following FOREIGN KEY (following_id) REFERENCES users (id),
  CONSTRAINT ck_follows_self CHECK (follower_id <> following_id)
) ENGINE=InnoDB COMMENT='팔로우';

-- 1:1 메시지: 맞팔로우·같은 챌린지는 바로, 그 밖은 메시지 요청(dm_threads). (작은 id, 큰 id) 쌍으로 대화방을 묶는다
CREATE TABLE direct_messages (
  id          BIGINT       NOT NULL AUTO_INCREMENT,
  sender_id   BIGINT       NOT NULL,
  receiver_id BIGINT       NOT NULL,
  low_id      BIGINT       NOT NULL COMMENT '두 사람 중 작은 id (대화방 묶음)',
  high_id     BIGINT       NOT NULL COMMENT '두 사람 중 큰 id',
  content     VARCHAR(500) NOT NULL,
  challenge_id BIGINT      NULL COMMENT '챌린지 초대 카드면 그 챌린지 (삭제되면 NULL)',
  created_at  DATETIME(3)  NOT NULL DEFAULT CURRENT_TIMESTAMP(3),
  read_at     DATETIME     NULL COMMENT '받는 사람이 읽은 시각 (안 읽음 표시)',
  PRIMARY KEY (id),
  KEY idx_dm_pair (low_id, high_id, id),
  KEY idx_dm_unread (receiver_id, read_at),
  CONSTRAINT fk_dm_sender FOREIGN KEY (sender_id) REFERENCES users (id),
  CONSTRAINT fk_dm_receiver FOREIGN KEY (receiver_id) REFERENCES users (id),
  CONSTRAINT fk_dm_challenge FOREIGN KEY (challenge_id) REFERENCES challenges (id) ON DELETE SET NULL,
  CONSTRAINT ck_dm_pair CHECK (sender_id <> receiver_id AND low_id < high_id
    AND low_id = LEAST(sender_id, receiver_id) AND high_id = GREATEST(sender_id, receiver_id))
) ENGINE=InnoDB COMMENT='1:1 메시지 (맞팔로우·같은 챌린지는 바로, 그 밖은 메시지 요청)';

-- 메시지 요청: 맞팔로우·같은 챌린지가 아닌 사이의 첫 연락. 수락해야 대화가 열린다
CREATE TABLE dm_threads (
  low_id       BIGINT   NOT NULL,
  high_id      BIGINT   NOT NULL,
  requester_id BIGINT   NOT NULL COMMENT '요청을 보낸 사람',
  status       ENUM('PENDING','ACCEPTED','DECLINED') NOT NULL DEFAULT 'PENDING',
  created_at   DATETIME NOT NULL DEFAULT CURRENT_TIMESTAMP,
  updated_at   DATETIME NOT NULL DEFAULT CURRENT_TIMESTAMP ON UPDATE CURRENT_TIMESTAMP,
  PRIMARY KEY (low_id, high_id),
  KEY idx_dm_threads_status (status),
  CONSTRAINT fk_dm_threads_low FOREIGN KEY (low_id) REFERENCES users (id),
  CONSTRAINT fk_dm_threads_high FOREIGN KEY (high_id) REFERENCES users (id),
  CONSTRAINT ck_dm_threads CHECK (low_id < high_id AND requester_id IN (low_id, high_id))
) ENGINE=InnoDB COMMENT='메시지 요청 (맞팔로우·같은 챌린지가 아닌 사이)';

-- 갓생기록 일기: 글 · 기분 · 태그한 챌린지 · 사진 한 장. 하루에 여러 개 쓸 수 있다. 항상 비공개(본인만), 포인트 보상 없음
CREATE TABLE diary_entries (
  id         BIGINT       NOT NULL AUTO_INCREMENT,
  user_id    BIGINT       NOT NULL,
  entry_date DATE         NOT NULL COMMENT '일기의 날짜 (지난 날짜도 쓰고 고칠 수 있다. 하루에 여러 개 쓸 수 있다)',
  content    VARCHAR(500) NOT NULL COMMENT '글 (기분이나 사진만 남기면 빈 문자열)',
  mood       ENUM('GREAT','GOOD','OKAY','SAD','HARD') NULL COMMENT '그날 기분 (최고예요 · 좋아요 · 보통이에요 · 아쉬워요 · 힘들었어요)',
  photo_key  VARCHAR(500) NULL COMMENT '일기에 붙인 사진 파일 키 (본인만 볼 수 있다)',
  created_at DATETIME     NOT NULL DEFAULT CURRENT_TIMESTAMP,
  updated_at DATETIME     NOT NULL DEFAULT CURRENT_TIMESTAMP ON UPDATE CURRENT_TIMESTAMP,
  PRIMARY KEY (id),
  KEY idx_diary_user_date (user_id, entry_date),
  CONSTRAINT fk_diary_user FOREIGN KEY (user_id) REFERENCES users (id)
) ENGINE=InnoDB COMMENT='갓생기록 일기 (항상 비공개)';

-- 일기에 태그한 챌린지 (그날 함께한 챌린지 중 고른 것)
CREATE TABLE diary_tags (
  diary_id     BIGINT NOT NULL,
  challenge_id BIGINT NOT NULL,
  PRIMARY KEY (diary_id, challenge_id),
  CONSTRAINT fk_diary_tags_diary FOREIGN KEY (diary_id) REFERENCES diary_entries (id) ON DELETE CASCADE,
  CONSTRAINT fk_diary_tags_challenge FOREIGN KEY (challenge_id) REFERENCES challenges (id) ON DELETE CASCADE
) ENGINE=InnoDB COMMENT='일기에 태그한 챌린지';

-- 고객센터 1:1 문의: 회원이 남기고 관리자가 답변한다. 본인 문의만 볼 수 있다
CREATE TABLE inquiries (
  id          BIGINT        NOT NULL AUTO_INCREMENT,
  user_id     BIGINT        NOT NULL,
  category    ENUM('ACCOUNT','CHALLENGE','POINT','BUG','ETC') NOT NULL COMMENT '계정 · 챌린지/인증 · 포인트 · 오류 신고 · 기타',
  title       VARCHAR(100)  NOT NULL,
  content     VARCHAR(2000) NOT NULL,
  status      ENUM('WAITING','ANSWERED') NOT NULL DEFAULT 'WAITING',
  answer      VARCHAR(2000) NULL,
  answered_at DATETIME      NULL,
  created_at  DATETIME      NOT NULL DEFAULT CURRENT_TIMESTAMP,
  PRIMARY KEY (id),
  KEY idx_inquiries_user (user_id, id),
  KEY idx_inquiries_status (status, id),
  CONSTRAINT fk_inquiries_user FOREIGN KEY (user_id) REFERENCES users (id)
) ENGINE=InnoDB COMMENT='고객센터 1:1 문의';

CREATE TABLE comments (
  id              BIGINT       NOT NULL AUTO_INCREMENT,
  verification_id BIGINT       NOT NULL,
  user_id         BIGINT       NOT NULL,
  content         VARCHAR(300) NOT NULL,
  is_deleted      BOOLEAN      NOT NULL DEFAULT FALSE,
  created_at      DATETIME     NOT NULL DEFAULT CURRENT_TIMESTAMP,
  PRIMARY KEY (id),
  KEY idx_comments_verification (verification_id, created_at),
  KEY idx_comments_user (user_id),
  CONSTRAINT fk_comments_verification FOREIGN KEY (verification_id) REFERENCES verifications (id),
  CONSTRAINT fk_comments_user FOREIGN KEY (user_id) REFERENCES users (id)
) ENGINE=InnoDB COMMENT='응원 댓글';

-- =====================================================================
-- 09. 리텐션
-- =====================================================================

CREATE TABLE notifications (
  id      BIGINT       NOT NULL AUTO_INCREMENT,
  user_id BIGINT       NOT NULL,
  type    ENUM('SETTLEMENT','VERIFY_REMINDER','COMMENT','REPORT_RESULT','REPORT_ALERT',
               'FOLLOW','MESSAGE_REQUEST','INQUIRY_ANSWER','VERIFY_REJECTED') NOT NULL,
  title   VARCHAR(100) NOT NULL,
  body    VARCHAR(300) NOT NULL,
  link       VARCHAR(200) NULL COMMENT '누르면 갈 화면 주소',
  dedupe_key VARCHAR(100) NULL COMMENT '같은 알림을 두 번 만들지 않기 위한 키 (예: verify:{챌린지}:{날짜})',
  read_at DATETIME     NULL,
  sent_at DATETIME     NOT NULL DEFAULT CURRENT_TIMESTAMP,
  PRIMARY KEY (id),
  UNIQUE KEY uk_notifications_dedupe (user_id, dedupe_key),
  KEY idx_notifications_user_read (user_id, read_at),
  CONSTRAINT fk_notifications_user FOREIGN KEY (user_id) REFERENCES users (id)
) ENGINE=InnoDB COMMENT='알림';

CREATE TABLE notification_settings (
  user_id          BIGINT   NOT NULL,
  verify_reminder  BOOLEAN  NOT NULL DEFAULT TRUE COMMENT '오늘 인증하는 날 알림',
  challenge_result BOOLEAN  NOT NULL DEFAULT TRUE COMMENT '챌린지 종료 · 정산 결과 알림',
  social           BOOLEAN  NOT NULL DEFAULT TRUE COMMENT '팔로우 · 메시지 요청 알림',
  updated_at       DATETIME NOT NULL DEFAULT CURRENT_TIMESTAMP ON UPDATE CURRENT_TIMESTAMP,
  PRIMARY KEY (user_id),
  CONSTRAINT fk_notification_settings_user FOREIGN KEY (user_id) REFERENCES users (id) ON DELETE CASCADE
) ENGINE=InnoDB COMMENT='알림 설정 (줄이 없으면 모두 켜짐)';

CREATE TABLE push_tokens (
  id        BIGINT       NOT NULL AUTO_INCREMENT,
  user_id   BIGINT       NOT NULL,
  device_id BIGINT       NULL,
  token     VARCHAR(255) NOT NULL,
  platform  ENUM('WEB','ANDROID','IOS') NOT NULL,
  PRIMARY KEY (id),
  UNIQUE KEY uk_push_token (token),
  KEY idx_push_user (user_id),
  KEY idx_push_device (device_id),
  CONSTRAINT fk_push_user FOREIGN KEY (user_id) REFERENCES users (id) ON DELETE CASCADE,
  CONSTRAINT fk_push_device FOREIGN KEY (device_id) REFERENCES devices (id) ON DELETE SET NULL
) ENGINE=InnoDB COMMENT='푸시 토큰';

CREATE TABLE weekly_reports (
  id            BIGINT       NOT NULL AUTO_INCREMENT,
  user_id       BIGINT       NOT NULL,
  week_start    DATE         NOT NULL,
  success_rate  DECIMAL(5,2) NOT NULL,
  worst_weekday TINYINT      NULL COMMENT '가장 자주 실패한 요일 (1=월 ~ 7=일)',
  stats_json    JSON         NOT NULL,
  coaching_text TEXT         NOT NULL,
  generated_at  DATETIME     NOT NULL DEFAULT CURRENT_TIMESTAMP,
  PRIMARY KEY (id),
  UNIQUE KEY uk_weekly_user_week (user_id, week_start),
  CONSTRAINT fk_weekly_user FOREIGN KEY (user_id) REFERENCES users (id),
  CONSTRAINT ck_weekly_weekday CHECK (worst_weekday IS NULL OR worst_weekday BETWEEN 1 AND 7)
) ENGINE=InnoDB COMMENT='주간 회고 리포트';

-- =====================================================================
-- 10. 마켓플레이스
-- =====================================================================

CREATE TABLE sponsors (
  id            BIGINT       NOT NULL AUTO_INCREMENT,
  name          VARCHAR(100) NOT NULL,
  contact_email VARCHAR(255) NOT NULL,
  status        ENUM('ACTIVE','SUSPENDED') NOT NULL DEFAULT 'ACTIVE',
  PRIMARY KEY (id)
) ENGINE=InnoDB COMMENT='스폰서';

CREATE TABLE product_categories (
  id   INT         NOT NULL AUTO_INCREMENT,
  name VARCHAR(30) NOT NULL COMMENT '운동용품 · 문구류 · 생활용품 …',
  PRIMARY KEY (id),
  UNIQUE KEY uk_product_categories_name (name)
) ENGINE=InnoDB COMMENT='상품 카테고리';

CREATE TABLE products (
  id           BIGINT       NOT NULL AUTO_INCREMENT,
  sponsor_id   BIGINT       NOT NULL,
  category_id  INT          NOT NULL,
  name         VARCHAR(150) NOT NULL,
  description  TEXT         NOT NULL,
  image_url    VARCHAR(500) NOT NULL,
  price_points BIGINT       NOT NULL,
  stock        INT          NOT NULL DEFAULT 0,
  status       ENUM('ON_SALE','SOLD_OUT','HIDDEN') NOT NULL DEFAULT 'ON_SALE',
  PRIMARY KEY (id),
  KEY idx_products_category_status (category_id, status),
  KEY idx_products_sponsor (sponsor_id),
  CONSTRAINT fk_products_sponsor FOREIGN KEY (sponsor_id) REFERENCES sponsors (id),
  CONSTRAINT fk_products_category FOREIGN KEY (category_id) REFERENCES product_categories (id),
  CONSTRAINT ck_products_values CHECK (price_points > 0 AND stock >= 0)
) ENGINE=InnoDB COMMENT='상품';

CREATE TABLE addresses (
  id         BIGINT         NOT NULL AUTO_INCREMENT,
  user_id    BIGINT         NOT NULL,
  recipient  VARCHAR(50)    NOT NULL,
  phone_enc  VARBINARY(255) NOT NULL COMMENT 'AES 암호화 저장 (개인정보)',
  zipcode    CHAR(5)        NOT NULL,
  address1   VARCHAR(200)   NOT NULL,
  address2   VARCHAR(200)   NOT NULL DEFAULT '',
  is_default BOOLEAN        NOT NULL DEFAULT FALSE,
  PRIMARY KEY (id),
  KEY idx_addresses_user (user_id),
  CONSTRAINT fk_addresses_user FOREIGN KEY (user_id) REFERENCES users (id)
) ENGINE=InnoDB COMMENT='배송지';

CREATE TABLE wishlists (
  user_id    BIGINT   NOT NULL,
  product_id BIGINT   NOT NULL,
  created_at DATETIME NOT NULL DEFAULT CURRENT_TIMESTAMP,
  PRIMARY KEY (user_id, product_id),
  KEY idx_wishlists_product (product_id),
  CONSTRAINT fk_wishlists_user FOREIGN KEY (user_id) REFERENCES users (id) ON DELETE CASCADE,
  CONSTRAINT fk_wishlists_product FOREIGN KEY (product_id) REFERENCES products (id) ON DELETE CASCADE
) ENGINE=InnoDB COMMENT='찜';

CREATE TABLE orders (
  id             BIGINT      NOT NULL AUTO_INCREMENT,
  user_id        BIGINT      NOT NULL,
  address_id     BIGINT      NOT NULL,
  transaction_id BIGINT      NOT NULL COMMENT '포인트 차감 원장 연결',
  request_key    VARCHAR(80) NOT NULL COMMENT '멱등키. 주문 버튼 중복 클릭 방지',
  total_points   BIGINT      NOT NULL,
  status         ENUM('PREPARING','SHIPPING','DELIVERED','CANCELED') NOT NULL DEFAULT 'PREPARING',
  tracking_no    VARCHAR(50) NULL,
  ordered_at     DATETIME    NOT NULL DEFAULT CURRENT_TIMESTAMP,
  PRIMARY KEY (id),
  UNIQUE KEY uk_orders_tx (transaction_id),
  UNIQUE KEY uk_orders_request_key (request_key),
  KEY idx_orders_user (user_id, ordered_at),
  KEY idx_orders_address (address_id),
  CONSTRAINT fk_orders_user FOREIGN KEY (user_id) REFERENCES users (id),
  CONSTRAINT fk_orders_address FOREIGN KEY (address_id) REFERENCES addresses (id),
  CONSTRAINT fk_orders_tx FOREIGN KEY (transaction_id) REFERENCES point_transactions (id),
  CONSTRAINT ck_orders_total CHECK (total_points > 0)
) ENGINE=InnoDB COMMENT='주문';

CREATE TABLE order_items (
  id          BIGINT NOT NULL AUTO_INCREMENT,
  order_id    BIGINT NOT NULL,
  product_id  BIGINT NOT NULL,
  quantity    INT    NOT NULL,
  unit_points BIGINT NOT NULL COMMENT '주문 시점 가격 스냅샷',
  PRIMARY KEY (id),
  KEY idx_order_items_order (order_id),
  KEY idx_order_items_product (product_id),
  CONSTRAINT fk_order_items_order FOREIGN KEY (order_id) REFERENCES orders (id),
  CONSTRAINT fk_order_items_product FOREIGN KEY (product_id) REFERENCES products (id),
  CONSTRAINT ck_order_items_values CHECK (quantity >= 1 AND unit_points > 0)
) ENGINE=InnoDB COMMENT='주문 상품';
