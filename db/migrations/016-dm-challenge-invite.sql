-- 1:1 메시지의 챌린지 초대: 대화방에서 [같이 챌린지 만들기]로 만든 챌린지를 초대 카드로 보낸다.
-- 초대 카드는 메시지 한 줄에 challenge_id 를 달아 표시한다. 챌린지가 삭제되면 NULL 이 되어 보통 글 메시지로 남는다.
-- 이미 만든 DB 에 적용한다. (새로 만드는 DB 는 01-schema.sql 에 이미 반영됨)
--   mysql -u godlife_user -p godlife < db/migrations/016-dm-challenge-invite.sql

ALTER TABLE direct_messages
  ADD COLUMN challenge_id BIGINT NULL COMMENT '챌린지 초대 카드면 그 챌린지 (삭제되면 NULL)' AFTER content,
  ADD CONSTRAINT fk_dm_challenge FOREIGN KEY (challenge_id) REFERENCES challenges (id) ON DELETE SET NULL;
