-- 알림함: 알림을 누르면 갈 화면(link)과, 같은 알림을 두 번 만들지 않기 위한 키(dedupe_key)를 더하고
-- 알림 종류에 팔로우 · 메시지 요청 · 문의 답변을 추가한다. 알림 설정(종류별 켜기/끄기)은 notification_settings.
-- 이미 만든 DB 에 적용한다. (새로 만드는 DB 는 01-schema.sql 에 이미 반영됨)
--   mysql -u godlife_user -p godlife < db/migrations/014-notification-center.sql

ALTER TABLE notifications
  MODIFY type ENUM('SETTLEMENT','VERIFY_REMINDER','COMMENT','REPORT_RESULT','REPORT_ALERT',
                   'FOLLOW','MESSAGE_REQUEST','INQUIRY_ANSWER') NOT NULL,
  ADD COLUMN link       VARCHAR(200) NULL COMMENT '누르면 갈 화면 주소',
  ADD COLUMN dedupe_key VARCHAR(100) NULL COMMENT '같은 알림을 두 번 만들지 않기 위한 키 (예: verify:{챌린지}:{날짜})',
  ADD UNIQUE KEY uk_notifications_dedupe (user_id, dedupe_key);

CREATE TABLE notification_settings (
  user_id          BIGINT   NOT NULL,
  verify_reminder  BOOLEAN  NOT NULL DEFAULT TRUE COMMENT '오늘 인증하는 날 알림',
  challenge_result BOOLEAN  NOT NULL DEFAULT TRUE COMMENT '챌린지 종료 · 정산 결과 알림',
  social           BOOLEAN  NOT NULL DEFAULT TRUE COMMENT '팔로우 · 메시지 요청 알림',
  updated_at       DATETIME NOT NULL DEFAULT CURRENT_TIMESTAMP ON UPDATE CURRENT_TIMESTAMP,
  PRIMARY KEY (user_id),
  CONSTRAINT fk_notification_settings_user FOREIGN KEY (user_id) REFERENCES users (id) ON DELETE CASCADE
) ENGINE=InnoDB COMMENT='알림 설정 (줄이 없으면 모두 켜짐)';
