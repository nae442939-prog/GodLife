-- 고객센터 1:1 문의: 회원이 남기고, 관리자가 답변한다. 본인 문의만 볼 수 있다.
-- 이미 만든 DB 에 적용한다. (새로 만드는 DB 는 01-schema.sql 에 이미 반영됨)
--   mysql -u godlife_user -p godlife < db/migrations/012-inquiries.sql

CREATE TABLE inquiries (
  id          BIGINT        NOT NULL AUTO_INCREMENT,
  user_id     BIGINT        NOT NULL,
  category    ENUM('ACCOUNT','CHALLENGE','POINT','BUG','ETC') NOT NULL COMMENT '계정 · 챌린지/인증 · 포인트 · 오류 신고 · 기타',
  title       VARCHAR(100)  NOT NULL,
  content     VARCHAR(2000) NOT NULL,
  status      ENUM('WAITING','ANSWERED') NOT NULL DEFAULT 'WAITING',
  answer      VARCHAR(2000) NULL,
  answered_at DATETIME      NULL,
  created_at  DATETIME      NOT NULL DEFAULT CURRENT_TIMESTAMP,
  PRIMARY KEY (id),
  KEY idx_inquiries_user (user_id, id),
  KEY idx_inquiries_status (status, id),
  CONSTRAINT fk_inquiries_user FOREIGN KEY (user_id) REFERENCES users (id)
) ENGINE=InnoDB COMMENT='고객센터 1:1 문의';
