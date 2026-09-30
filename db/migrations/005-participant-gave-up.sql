-- 진행 중 포기(GAVE_UP). 실패(FAILED)와 구분해 '포기함'으로 보여 준다.
-- 이미 만든 DB 에 적용한다. (새로 만드는 DB 는 01-schema.sql 에 이미 반영됨)
--   mysql -u godlife_user -p godlife < db/migrations/005-participant-gave-up.sql

ALTER TABLE challenge_participants
  MODIFY status ENUM('ACTIVE','COMPLETED','FAILED','GAVE_UP','LEFT','KICKED') NOT NULL DEFAULT 'ACTIVE'
    COMMENT 'COMPLETED/FAILED = 종료 시 판정, GAVE_UP = 진행 중 포기, KICKED = 방장이 내보냄';
