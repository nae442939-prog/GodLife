-- GodLife 기준 데이터 (개발용 초기값). 01-schema.sql 실행 뒤에 실행한다.
--   mysql -u godlife_user -p godlife < db/02-seed.sql
-- 아래 한도/카테고리 값은 임시 기본값이며 서비스 정책에 맞게 바꿔도 된다.

-- 티어: users.tier_id 기본값 1 = BRONZE (신규/저티어는 가장 낮은 베팅 상한)
INSERT INTO tiers (id, name, min_score, daily_bet_limit, monthly_bet_limit, high_stake_allowed) VALUES
  (1, 'BRONZE',       0,   1000,    10000, FALSE),
  (2, 'SILVER',     100,   3000,    30000, FALSE),
  (3, 'GOLD',       300,  10000,   100000, FALSE),
  (4, 'PLATINUM',   700,  30000,   300000, TRUE),
  (5, 'DIAMOND',   1500, 100000,  1000000, TRUE);

-- 챌린지 카테고리: ai_label 은 ai-server 분류 모델의 클래스 라벨과 반드시 일치해야 한다.
-- 'other'(기타)는 운동·공부·독서·요리 밖의 생활 습관(일찍 일어나기, 밖에 나가 산책하기 등)이다.
-- 사진 모양이 제각각이라 AI 학습은 세부 라벨(예: wake_up, walk)로 나눠서 한다. (2차 AI 인증 때 세부 종류 선택 추가)
INSERT INTO categories (id, name, ai_label) VALUES
  (1, '운동', 'exercise'),
  (2, '공부', 'study'),
  (3, '독서', 'reading'),
  (4, '요리', 'cooking'),
  (5, '기타', 'other');

-- 포인트 상점 상품 카테고리 (이용권 · 상품권은 배송 없이 쿠폰 번호를 발급하는 상품)
INSERT IGNORE INTO product_categories (name) VALUES ('운동용품'), ('문구류'), ('생활용품'), ('이용권'), ('상품권');
