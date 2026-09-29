-- 이미 만든 DB 에 챌린지 오픈채팅 테이블을 추가한다. (새로 만드는 DB 는 01-schema.sql 에 이미 반영됨)
--   mysql -u godlife_user -p godlife < db/migrations/002-challenge-chat.sql

CREATE TABLE chat_messages (
  id           BIGINT       NOT NULL AUTO_INCREMENT,
  challenge_id BIGINT       NOT NULL COMMENT '챌린지 1개 = 채팅방 1개',
  sender_id    BIGINT       NOT NULL,
  content      VARCHAR(500) NOT NULL COMMENT '텍스트만 (이미지/파일 없음)',
  created_at   DATETIME(3)  NOT NULL DEFAULT CURRENT_TIMESTAMP(3),
  PRIMARY KEY (id),
  KEY idx_chat_challenge_id (challenge_id, id) COMMENT '방별로 id N 이후/이전 메시지 (폴링 커서)',
  CONSTRAINT fk_chat_challenge FOREIGN KEY (challenge_id) REFERENCES challenges (id),
  CONSTRAINT fk_chat_sender FOREIGN KEY (sender_id) REFERENCES users (id)
) ENGINE=InnoDB COMMENT='챌린지 오픈채팅 메시지';
