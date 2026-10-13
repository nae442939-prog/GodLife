-- 챌린지 대표 사진: 개설자가 올린 사진 한 장을 목록 · 홈 카드에 보여 준다 (포인트 상점의 상품 사진처럼).
-- cover_key 는 ImageStore 파일 키("cover/{challengeId}/{uuid}.jpg"). 더미 챌린지는 "cover/seed/{이름}.jpg" 를 같이 쓴다.
-- 사진을 올리지 않은 챌린지는 NULL → 화면이 지금처럼 종류 아이콘을 보여 준다.
-- 이미 만든 DB 에 적용한다. (새로 만드는 DB 는 01-schema.sql 에 이미 반영됨)
--   mysql -u godlife_user -p godlife < db/migrations/026-challenge-cover.sql

SET NAMES utf8mb4;

ALTER TABLE challenges
  ADD COLUMN cover_key VARCHAR(255) NULL COMMENT '대표 사진 파일 키 (없으면 NULL)' AFTER description;
