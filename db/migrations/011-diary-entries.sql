-- 갓생기록 일기: 짧은 회고(글 · 기분 · 태그한 챌린지 · 사진 한 장). 하루에 여러 개 쓸 수 있다. 항상 비공개(본인만)이고 포인트 보상이 없다.
-- 이미 만든 DB 에 적용한다. (새로 만드는 DB 는 01-schema.sql 에 이미 반영됨)
--   mysql -u godlife_user -p godlife < db/migrations/011-diary-entries.sql

CREATE TABLE diary_entries (
  id         BIGINT       NOT NULL AUTO_INCREMENT,
  user_id    BIGINT       NOT NULL,
  entry_date DATE         NOT NULL COMMENT '일기의 날짜 (지난 날짜도 쓰고 고칠 수 있다. 하루에 여러 개 쓸 수 있다)',
  content    VARCHAR(500) NOT NULL COMMENT '글 (기분이나 사진만 남기면 빈 문자열)',
  mood       ENUM('GREAT','GOOD','OKAY','SAD','HARD') NULL COMMENT '그날 기분 (최고예요 · 좋아요 · 보통이에요 · 아쉬워요 · 힘들었어요)',
  photo_key  VARCHAR(500) NULL COMMENT '일기에 붙인 사진 파일 키 (본인만 볼 수 있다)',
  created_at DATETIME     NOT NULL DEFAULT CURRENT_TIMESTAMP,
  updated_at DATETIME     NOT NULL DEFAULT CURRENT_TIMESTAMP ON UPDATE CURRENT_TIMESTAMP,
  PRIMARY KEY (id),
  KEY idx_diary_user_date (user_id, entry_date),
  CONSTRAINT fk_diary_user FOREIGN KEY (user_id) REFERENCES users (id)
) ENGINE=InnoDB COMMENT='갓생기록 일기 (항상 비공개)';

-- 일기에 태그한 챌린지 (그날 함께한 챌린지 중 고른 것)
CREATE TABLE diary_tags (
  diary_id     BIGINT NOT NULL,
  challenge_id BIGINT NOT NULL,
  PRIMARY KEY (diary_id, challenge_id),
  CONSTRAINT fk_diary_tags_diary FOREIGN KEY (diary_id) REFERENCES diary_entries (id) ON DELETE CASCADE,
  CONSTRAINT fk_diary_tags_challenge FOREIGN KEY (challenge_id) REFERENCES challenges (id) ON DELETE CASCADE
) ENGINE=InnoDB COMMENT='일기에 태그한 챌린지';
