-- 푸시 알림(웹 푸시): 브라우저가 만든 구독(주소 + 키 2개)을 회원별로 남긴다.
-- 기존 push_tokens 는 앱(FCM/APNs) 토큰용이라 주소(최대 1000자) · 키를 담을 수 없어 테이블을 따로 둔다.
-- 이미 만든 DB 에 적용한다. (새로 만드는 DB 는 01-schema.sql 에 이미 반영됨)
--   mysql -u godlife_user -p godlife < db/migrations/024-push-subscriptions.sql

SET NAMES utf8mb4;

CREATE TABLE IF NOT EXISTS push_subscriptions (
  id            BIGINT        NOT NULL AUTO_INCREMENT,
  user_id       BIGINT        NOT NULL,
  endpoint      VARCHAR(1000) NOT NULL COMMENT '브라우저 푸시 서비스 주소 (알려진 푸시 서비스 주소만 받는다)',
  endpoint_hash CHAR(64)      NOT NULL COMMENT 'endpoint 의 SHA-256. 주소가 길어서 유일 키는 해시에 건다',
  p256dh        VARCHAR(200)  NOT NULL COMMENT '브라우저 공개 키 (본문 암호화용)',
  auth          VARCHAR(100)  NOT NULL COMMENT '브라우저 인증 비밀값 (본문 암호화용)',
  created_at    DATETIME      NOT NULL DEFAULT CURRENT_TIMESTAMP,
  PRIMARY KEY (id),
  UNIQUE KEY uk_push_sub_endpoint (endpoint_hash),
  KEY idx_push_sub_user (user_id),
  CONSTRAINT fk_push_sub_user FOREIGN KEY (user_id) REFERENCES users (id) ON DELETE CASCADE
) ENGINE=InnoDB COMMENT='웹 푸시 구독 (브라우저 하나에 한 줄)';
