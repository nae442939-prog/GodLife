-- 시즌제 랭킹: 주간(월~일) · 월간(1일~말일) 시즌이 같이 돌아간다.
-- 점수 = 그 시즌 기간 동안 승인된 인증 횟수(일반 랭킹과 같은 '성공' 기준).
-- 시즌이 끝나면 순위를 매기고(공동 순위 포함) 상위 3명에게 차등 보너스를 준다: 1위 5,000P · 2위 3,000P · 3위 1,000P
-- (reward 포인트, point_transactions.type = SEASON_BONUS — 이미 01-schema.sql 에 있던 값). 다음 시즌은 바로 새로 시작한다.
-- seasons · season_rankings 테이블은 01-schema.sql 에 이미 있다(미리 만들어 둔 것) — 여기서는 알림 종류만 넓힌다.
-- 이미 만든 DB 에 적용한다. (새로 만드는 DB 는 01-schema.sql 에 이미 반영됨)
--   mysql -u godlife_user -p godlife < db/migrations/022-season.sql

SET NAMES utf8mb4;

ALTER TABLE notifications
  MODIFY type ENUM('SETTLEMENT','VERIFY_REMINDER','COMMENT','REPORT_RESULT','REPORT_ALERT',
                   'FOLLOW','MESSAGE_REQUEST','INQUIRY_ANSWER','VERIFY_REJECTED','ORDER','TIER','SEASON') NOT NULL;
