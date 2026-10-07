-- 주간 회고 리포트: 지난 한 주(월~일)의 인증 기록으로 성공률 · 요일별 · 챌린지별 통계와 코칭 문구를 만든다.
-- weekly_reports 테이블은 01-schema.sql 에 이미 있다(미리 만들어 둔 것) — 여기서는 알림 종류만 넓힌다.
-- (MySQL ENUM 이라 Java enum 만 늘리면 INSERT IGNORE 가 알림을 조용히 버린다)
-- 이미 만든 DB 에 적용한다. (새로 만드는 DB 는 01-schema.sql 에 이미 반영됨)
--   mysql -u godlife_user -p godlife < db/migrations/023-weekly-report.sql

SET NAMES utf8mb4;

ALTER TABLE notifications
  MODIFY type ENUM('SETTLEMENT','VERIFY_REMINDER','COMMENT','REPORT_RESULT','REPORT_ALERT',
                   'FOLLOW','MESSAGE_REQUEST','INQUIRY_ANSWER','VERIFY_REJECTED','ORDER','TIER','SEASON',
                   'WEEKLY_REPORT') NOT NULL;
