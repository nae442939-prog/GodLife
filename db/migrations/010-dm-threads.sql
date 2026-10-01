-- 메시지 요청: 맞팔로우도 아니고 같은 챌린지 참가자도 아닌 사람에게 보내면 '요청'으로 간다.
-- 받은 사람이 수락(또는 답장)하면 대화가 열리고, 거절하면 보낸 사람은 더 보낼 수 없다.
-- 두 사람 사이에 한 줄 (작은 id, 큰 id). 맞팔로우·같은 챌린지 사이는 이 줄 없이 바로 대화한다.
-- 이미 만든 DB 에 적용한다. (새로 만드는 DB 는 01-schema.sql 에 이미 반영됨)
--   mysql -u godlife_user -p godlife < db/migrations/010-dm-threads.sql

CREATE TABLE dm_threads (
  low_id       BIGINT   NOT NULL,
  high_id      BIGINT   NOT NULL,
  requester_id BIGINT   NOT NULL COMMENT '요청을 보낸 사람',
  status       ENUM('PENDING','ACCEPTED','DECLINED') NOT NULL DEFAULT 'PENDING',
  created_at   DATETIME NOT NULL DEFAULT CURRENT_TIMESTAMP,
  updated_at   DATETIME NOT NULL DEFAULT CURRENT_TIMESTAMP ON UPDATE CURRENT_TIMESTAMP,
  PRIMARY KEY (low_id, high_id),
  KEY idx_dm_threads_status (status),
  CONSTRAINT fk_dm_threads_low FOREIGN KEY (low_id) REFERENCES users (id),
  CONSTRAINT fk_dm_threads_high FOREIGN KEY (high_id) REFERENCES users (id),
  CONSTRAINT ck_dm_threads CHECK (low_id < high_id AND requester_id IN (low_id, high_id))
) ENGINE=InnoDB COMMENT='메시지 요청 (맞팔로우·같은 챌린지가 아닌 사이)';
