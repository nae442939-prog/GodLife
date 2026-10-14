-- 뱃지 착용: 딴 뱃지 중 하나를 골라 이름 옆에 보여 준다 (설정 → 프로필에서 착용 · 해제).
-- equipped_badge 는 뱃지 코드(ProfileService.badges 의 code, 예: STREAK_7). 뱃지 자체는 저장하지 않고 기록으로 계산하므로
-- 착용한 뱃지를 더는 못 딴 상태가 되면 화면에는 보이지 않는다.
-- 이미 만든 DB 에 적용한다. (새로 만드는 DB 는 01-schema.sql 에 이미 반영됨)
--   mysql -u godlife_user -p godlife < db/migrations/027-equipped-badge.sql

SET NAMES utf8mb4;

ALTER TABLE users
  ADD COLUMN equipped_badge VARCHAR(30) NULL COMMENT '착용한 뱃지 코드 (이름 옆에 보여 준다. 없으면 NULL)' AFTER bio;
