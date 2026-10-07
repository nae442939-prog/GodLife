-- 오픈채팅 관리 기능: 방장 공지 · 강퇴 · 시스템 메시지 · 차단 · 신고.
-- 이미 만든 DB 에 적용한다. (새로 만드는 DB 는 01-schema.sql 에 이미 반영됨)
--   mysql -u godlife_user -p godlife < db/migrations/003-chat-moderation.sql

-- 방장이 올리는 공지 (챌린지당 1개, 채팅방 맨 위 고정)
ALTER TABLE challenges
  ADD COLUMN notice            VARCHAR(300) NULL COMMENT '방장 공지. 채팅방 맨 위 고정' AFTER description,
  ADD COLUMN notice_updated_at DATETIME     NULL AFTER notice;

-- 강퇴: 방장이 내보낸 참가자. 채팅·상세 접근이 막히고 초대 링크로도 다시 못 들어온다.
ALTER TABLE challenge_participants
  MODIFY status ENUM('ACTIVE','COMPLETED','FAILED','LEFT','KICKED') NOT NULL DEFAULT 'ACTIVE';

-- 시스템 메시지(강퇴 · 공지 알림). sender 는 방장.
ALTER TABLE chat_messages
  ADD COLUMN type ENUM('USER','SYSTEM') NOT NULL DEFAULT 'USER' AFTER sender_id;

-- 차단: 차단한 사람 화면에서만 그 사람의 메시지를 숨긴다.
CREATE TABLE user_blocks (
  id         BIGINT   NOT NULL AUTO_INCREMENT,
  blocker_id BIGINT   NOT NULL,
  blocked_id BIGINT   NOT NULL,
  created_at DATETIME NOT NULL DEFAULT CURRENT_TIMESTAMP,
  PRIMARY KEY (id),
  UNIQUE KEY uk_user_blocks (blocker_id, blocked_id),
  CONSTRAINT fk_user_blocks_blocker FOREIGN KEY (blocker_id) REFERENCES users (id),
  CONSTRAINT fk_user_blocks_blocked FOREIGN KEY (blocked_id) REFERENCES users (id),
  CONSTRAINT ck_user_blocks_self CHECK (blocker_id <> blocked_id)
) ENGINE=InnoDB COMMENT='사용자 차단';

-- 채팅 신고: 한 사람이 같은 메시지를 두 번 신고할 수 없다. 한 참가자에게 20건 이상 쌓이면 방장에게 알린다.
CREATE TABLE chat_reports (
  id               BIGINT       NOT NULL AUTO_INCREMENT,
  challenge_id     BIGINT       NOT NULL,
  message_id       BIGINT       NOT NULL,
  reporter_id      BIGINT       NOT NULL,
  reported_user_id BIGINT       NOT NULL,
  reason           ENUM('ABUSE','SPAM','INAPPROPRIATE','OTHER') NOT NULL
                     COMMENT '욕설·비방 / 스팸·광고 / 부적절한 내용 / 기타',
  detail           VARCHAR(300) NULL,
  status           ENUM('OPEN','RESOLVED') NOT NULL DEFAULT 'OPEN' COMMENT 'RESOLVED = 방장이 강퇴하거나 넘김',
  created_at       DATETIME     NOT NULL DEFAULT CURRENT_TIMESTAMP,
  PRIMARY KEY (id),
  UNIQUE KEY uk_chat_reports_message_reporter (message_id, reporter_id),
  KEY idx_chat_reports_target (challenge_id, reported_user_id, status),
  CONSTRAINT fk_chat_reports_challenge FOREIGN KEY (challenge_id) REFERENCES challenges (id),
  CONSTRAINT fk_chat_reports_message FOREIGN KEY (message_id) REFERENCES chat_messages (id),
  CONSTRAINT fk_chat_reports_reporter FOREIGN KEY (reporter_id) REFERENCES users (id),
  CONSTRAINT fk_chat_reports_reported FOREIGN KEY (reported_user_id) REFERENCES users (id)
) ENGINE=InnoDB COMMENT='오픈채팅 신고';

-- 신고 누적 알림 (방장에게)
ALTER TABLE notifications
  MODIFY type ENUM('SETTLEMENT','VERIFY_REMINDER','COMMENT','REPORT_RESULT','REPORT_ALERT') NOT NULL;
