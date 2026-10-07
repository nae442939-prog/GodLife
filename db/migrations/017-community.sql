-- 커뮤니티: 게시판 하나 + 말머리(자유 · 인증 후기 · 팁 · 질문), 사진 최대 4장, 글 · 댓글 좋아요, 댓글 · 답글(대댓글), 글 · 댓글 신고.
-- 글에는 챌린지를 골라 그날 인증 결과를 붙일 수 있다. 결과는 서버가 인증 기록을 보고 붙이고 쓴 시점 값으로 굳힌다.
-- (인증 사진 응원 댓글은 처음부터 있던 comments 테이블을 쓴다)
-- 이미 만든 DB 에 적용한다. (새로 만드는 DB 는 01-schema.sql 에 이미 반영됨)
--   mysql -u godlife_user -p godlife < db/migrations/017-community.sql

CREATE TABLE posts (
  id              BIGINT       NOT NULL AUTO_INCREMENT,
  user_id         BIGINT       NOT NULL,
  topic           ENUM('FREE','REVIEW','TIP','QUESTION') NOT NULL COMMENT '말머리: 자유 · 인증 후기 · 팁 · 질문',
  title           VARCHAR(100) NOT NULL,
  content         TEXT         NOT NULL,
  challenge_id    BIGINT       NULL COMMENT '인증 결과를 붙인 챌린지 (삭제되면 NULL, 아래 제목 · 결과는 남는다)',
  challenge_title VARCHAR(100) NULL COMMENT '쓴 시점의 챌린지 제목',
  verify_date     DATE         NULL COMMENT '인증 결과의 날짜 (글 쓴 날)',
  verify_result   ENUM('SUCCESS','FAIL') NULL COMMENT '쓴 시점의 그날 인증 결과. 서버가 인증 기록으로 정한다',
  like_count      INT          NOT NULL DEFAULT 0,
  comment_count   INT          NOT NULL DEFAULT 0,
  status          ENUM('VISIBLE','HIDDEN','DELETED') NOT NULL DEFAULT 'VISIBLE' COMMENT 'HIDDEN = 신고로 관리자가 가림',
  created_at      DATETIME     NOT NULL DEFAULT CURRENT_TIMESTAMP,
  updated_at      DATETIME     NULL,
  PRIMARY KEY (id),
  KEY idx_posts_list (status, id),
  KEY idx_posts_topic (status, topic, id),
  KEY idx_posts_popular (status, like_count, comment_count),
  KEY idx_posts_user (user_id),
  CONSTRAINT fk_posts_user FOREIGN KEY (user_id) REFERENCES users (id),
  CONSTRAINT fk_posts_challenge FOREIGN KEY (challenge_id) REFERENCES challenges (id) ON DELETE SET NULL,
  CONSTRAINT ck_posts_counts CHECK (like_count >= 0 AND comment_count >= 0)
) ENGINE=InnoDB COMMENT='커뮤니티 글';

CREATE TABLE post_images (
  id         BIGINT       NOT NULL AUTO_INCREMENT,
  post_id    BIGINT       NOT NULL,
  photo_key  VARCHAR(120) NOT NULL COMMENT '서버 디스크의 파일 키 (post/{postId}/{uuid}.jpg)',
  created_at DATETIME     NOT NULL DEFAULT CURRENT_TIMESTAMP,
  PRIMARY KEY (id),
  KEY idx_post_images_post (post_id, id),
  CONSTRAINT fk_post_images_post FOREIGN KEY (post_id) REFERENCES posts (id) ON DELETE CASCADE
) ENGINE=InnoDB COMMENT='커뮤니티 글 사진 (글마다 4장까지)';

CREATE TABLE post_likes (
  post_id    BIGINT   NOT NULL,
  user_id    BIGINT   NOT NULL,
  created_at DATETIME NOT NULL DEFAULT CURRENT_TIMESTAMP,
  PRIMARY KEY (post_id, user_id),
  KEY idx_post_likes_user (user_id),
  CONSTRAINT fk_post_likes_post FOREIGN KEY (post_id) REFERENCES posts (id) ON DELETE CASCADE,
  CONSTRAINT fk_post_likes_user FOREIGN KEY (user_id) REFERENCES users (id)
) ENGINE=InnoDB COMMENT='커뮤니티 글 좋아요 (한 사람이 글마다 한 번)';

CREATE TABLE post_comments (
  id         BIGINT       NOT NULL AUTO_INCREMENT,
  post_id    BIGINT       NOT NULL,
  parent_id  BIGINT       NULL COMMENT '답글(대댓글)이면 원 댓글. 답글은 한 단계만 둔다',
  user_id    BIGINT       NOT NULL,
  content    VARCHAR(300) NOT NULL,
  like_count INT          NOT NULL DEFAULT 0,
  status     ENUM('VISIBLE','HIDDEN','DELETED') NOT NULL DEFAULT 'VISIBLE',
  created_at DATETIME     NOT NULL DEFAULT CURRENT_TIMESTAMP,
  PRIMARY KEY (id),
  KEY idx_post_comments_post (post_id, id),
  KEY idx_post_comments_user (user_id),
  KEY idx_post_comments_parent (parent_id),
  CONSTRAINT fk_post_comments_post FOREIGN KEY (post_id) REFERENCES posts (id) ON DELETE CASCADE,
  CONSTRAINT fk_post_comments_parent FOREIGN KEY (parent_id) REFERENCES post_comments (id) ON DELETE CASCADE,
  CONSTRAINT fk_post_comments_user FOREIGN KEY (user_id) REFERENCES users (id)
) ENGINE=InnoDB COMMENT='커뮤니티 댓글';

CREATE TABLE post_comment_likes (
  comment_id BIGINT   NOT NULL,
  user_id    BIGINT   NOT NULL,
  created_at DATETIME NOT NULL DEFAULT CURRENT_TIMESTAMP,
  PRIMARY KEY (comment_id, user_id),
  KEY idx_post_comment_likes_user (user_id),
  CONSTRAINT fk_post_comment_likes_comment FOREIGN KEY (comment_id) REFERENCES post_comments (id) ON DELETE CASCADE,
  CONSTRAINT fk_post_comment_likes_user FOREIGN KEY (user_id) REFERENCES users (id)
) ENGINE=InnoDB COMMENT='커뮤니티 댓글 좋아요 (한 사람이 댓글마다 한 번)';

CREATE TABLE community_reports (
  id          BIGINT       NOT NULL AUTO_INCREMENT,
  target_type ENUM('POST','COMMENT') NOT NULL,
  target_id   BIGINT       NOT NULL COMMENT 'posts.id 또는 post_comments.id',
  reporter_id BIGINT       NOT NULL,
  reason      VARCHAR(200) NOT NULL,
  status      ENUM('OPEN','ACCEPTED','DISMISSED') NOT NULL DEFAULT 'OPEN' COMMENT 'ACCEPTED = 가림',
  created_at  DATETIME     NOT NULL DEFAULT CURRENT_TIMESTAMP,
  handled_at  DATETIME     NULL,
  PRIMARY KEY (id),
  UNIQUE KEY uk_community_reports (target_type, target_id, reporter_id),
  KEY idx_community_reports_status (status, id),
  KEY idx_community_reports_reporter (reporter_id, created_at),
  CONSTRAINT fk_community_reports_reporter FOREIGN KEY (reporter_id) REFERENCES users (id)
) ENGINE=InnoDB COMMENT='커뮤니티 글 · 댓글 신고';
