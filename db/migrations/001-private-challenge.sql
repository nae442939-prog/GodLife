-- 이미 01-schema.sql 로 만든 DB 에 비공개 챌린지(초대 코드) 컬럼을 추가한다. (새로 만드는 DB 는 01-schema.sql 에 이미 반영됨)
--   mysql -u godlife_user -p godlife < db/migrations/001-private-challenge.sql
-- 기존 챌린지에는 임시 코드를 채운다. (새 챌린지는 서버가 헷갈리는 글자 없는 코드를 만든다)

ALTER TABLE challenges
  ADD COLUMN visibility  ENUM('PUBLIC','PRIVATE') NOT NULL DEFAULT 'PUBLIC'
    COMMENT 'PRIVATE = 목록에 안 나오고 초대 링크로만 참여' AFTER mode,
  ADD COLUMN invite_code CHAR(8) NULL
    COMMENT '초대 링크 코드. 헷갈리는 글자(0/O/1/I/L) 없는 영숫자' AFTER visibility;

UPDATE challenges SET invite_code = UPPER(SUBSTRING(REPLACE(UUID(), '-', ''), 1, 8)) WHERE invite_code IS NULL;

ALTER TABLE challenges
  MODIFY invite_code CHAR(8) NOT NULL COMMENT '초대 링크 코드. 헷갈리는 글자(0/O/1/I/L) 없는 영숫자',
  ADD UNIQUE KEY uk_challenges_invite_code (invite_code);
