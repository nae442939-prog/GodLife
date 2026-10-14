-- 가입 환영 알림: 테스트 서버에서 체험 데이터를 넣을 때 '가입을 축하해요' 알림을 함께 보낸다 (DemoStarter).
-- 이미 만든 DB 에 적용한다. (새로 만드는 DB 는 01-schema.sql 에 이미 반영됨)
--   mysql -u godlife_user -p godlife < db/migrations/028-welcome-notification.sql

SET NAMES utf8mb4;

ALTER TABLE notifications
  MODIFY COLUMN type ENUM('SETTLEMENT','VERIFY_REMINDER','COMMENT','REPORT_RESULT','REPORT_ALERT',
                          'FOLLOW','MESSAGE_REQUEST','INQUIRY_ANSWER','VERIFY_REJECTED','ORDER','TIER','SEASON',
                          'WEEKLY_REPORT','WELCOME') NOT NULL;
