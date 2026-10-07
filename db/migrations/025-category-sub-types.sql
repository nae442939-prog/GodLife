-- '기타' 카테고리의 세부 종류: 일찍 일어나기 · 산책 · 물 마시기 · 청소/정리 · 식물 가꾸기.
-- 기타는 사진 모양이 제각각이라 한 라벨로는 AI 가 가려낼 수 없어서, 개설자가 세부 종류를 고르면 그 라벨로 판정한다.
-- 세부 종류를 고르지 않은 기타 챌린지('그 밖', sub_type_id = NULL)는 지금처럼 같은 사진 재사용만 본다.
-- ai_label 은 ai-server 분류 모델(mobilenetv2-v2)의 클래스 라벨과 같아야 한다.
-- 이미 만든 DB 에 적용한다. (새로 만드는 DB 는 01-schema.sql · 02-seed.sql 에 이미 반영됨)
--   mysql -u godlife_user -p godlife < db/migrations/025-category-sub-types.sql

SET NAMES utf8mb4;

CREATE TABLE IF NOT EXISTS category_sub_types (
  id          INT         NOT NULL AUTO_INCREMENT,
  category_id INT         NOT NULL,
  name        VARCHAR(30) NOT NULL COMMENT '일찍 일어나기 · 산책 …',
  ai_label    VARCHAR(50) NOT NULL COMMENT 'AI 분류 모델 클래스 라벨과 1:1 매핑',
  sort_order  INT         NOT NULL DEFAULT 0,
  is_active   BOOLEAN     NOT NULL DEFAULT TRUE,
  PRIMARY KEY (id),
  UNIQUE KEY uk_category_sub_types_name (category_id, name),
  UNIQUE KEY uk_category_sub_types_ai_label (ai_label),
  CONSTRAINT fk_category_sub_types_category FOREIGN KEY (category_id) REFERENCES categories (id)
) ENGINE=InnoDB COMMENT='카테고리 세부 종류 (지금은 기타만)';

INSERT IGNORE INTO category_sub_types (id, category_id, name, ai_label, sort_order) VALUES
  (1, 5, '일찍 일어나기', 'wake_up', 1),
  (2, 5, '산책',         'walk',    2),
  (3, 5, '물 마시기',     'water',   3),
  (4, 5, '청소 · 정리',   'clean',   4),
  (5, 5, '식물 가꾸기',   'plant',   5);

ALTER TABLE challenges
  ADD COLUMN sub_type_id INT NULL COMMENT '카테고리 세부 종류 (기타만, 고르지 않으면 NULL)' AFTER category_id,
  ADD CONSTRAINT fk_challenges_sub_type FOREIGN KEY (sub_type_id) REFERENCES category_sub_types (id);
