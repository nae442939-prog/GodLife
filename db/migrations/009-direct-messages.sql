-- 1:1 메시지: 서로 팔로우(맞팔로우)한 회원끼리만 보낼 수 있다. 차단하면 서로의 팔로우가 끊겨 보낼 수 없다.
-- 두 사람 대화는 (작은 id, 큰 id) 쌍으로 묶는다 → 한 인덱스로 대화방을 찾는다.
-- 이미 만든 DB 에 적용한다. (새로 만드는 DB 는 01-schema.sql 에 이미 반영됨)
--   mysql -u godlife_user -p godlife < db/migrations/009-direct-messages.sql

CREATE TABLE direct_messages (
  id          BIGINT       NOT NULL AUTO_INCREMENT,
  sender_id   BIGINT       NOT NULL,
  receiver_id BIGINT       NOT NULL,
  low_id      BIGINT       NOT NULL COMMENT '두 사람 중 작은 id (대화방 묶음)',
  high_id     BIGINT       NOT NULL COMMENT '두 사람 중 큰 id',
  content     VARCHAR(500) NOT NULL,
  created_at  DATETIME(3)  NOT NULL DEFAULT CURRENT_TIMESTAMP(3),
  read_at     DATETIME     NULL COMMENT '받는 사람이 읽은 시각 (안 읽음 표시)',
  PRIMARY KEY (id),
  KEY idx_dm_pair (low_id, high_id, id),
  KEY idx_dm_unread (receiver_id, read_at),
  CONSTRAINT fk_dm_sender FOREIGN KEY (sender_id) REFERENCES users (id),
  CONSTRAINT fk_dm_receiver FOREIGN KEY (receiver_id) REFERENCES users (id),
  CONSTRAINT ck_dm_pair CHECK (sender_id <> receiver_id AND low_id < high_id
    AND low_id = LEAST(sender_id, receiver_id) AND high_id = GREATEST(sender_id, receiver_id))
) ENGINE=InnoDB COMMENT='1:1 메시지 (맞팔로우끼리)';
