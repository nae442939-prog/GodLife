-- 개발용: 대표 사진이 없는 챌린지에 종류별 더미 사진(db/dev-covers/*.jpg)을 붙인다. 운영 DB 에는 쓰지 않는다.
-- 사진 파일은 dev-covers.sh 가 backend/uploads/cover/seed/ 로 복사한다. 여러 챌린지가 같은 파일을 같이 쓴다.
-- 개설자가 직접 올린 사진(cover/{챌린지 id}/...)은 건드리지 않는다. 여러 번 돌려도 결과가 같다.
-- 세부 종류 → 제목에 든 말 → 카테고리 순서로 고른다. 맞는 사진이 없으면(세부 종류 없는 '기타') 그대로 둔다.

SET NAMES utf8mb4;

UPDATE challenges c
  JOIN categories cat ON cat.id = c.category_id
  LEFT JOIN category_sub_types st ON st.id = c.sub_type_id
SET c.cover_key = CONCAT('cover/seed/', CASE
    WHEN st.name = '일찍 일어나기' THEN 'morning'
    WHEN st.name = '산책' THEN 'walk'
    WHEN st.name = '물 마시기' THEN 'water'
    WHEN st.name = '청소 · 정리' THEN 'clean'
    WHEN st.name = '식물 가꾸기' THEN 'plant'
    WHEN c.title REGEXP '달리기|러닝|조깅' THEN 'run'
    WHEN c.title REGEXP '스트레칭|홈트|요가' THEN 'stretch'
    WHEN c.title REGEXP '샐러드|채소' THEN 'salad'
    WHEN c.title REGEXP '알고리즘|코딩|개발' THEN 'code'
    WHEN cat.name = '운동' THEN 'gym'
    WHEN cat.name = '공부' THEN 'study'
    WHEN cat.name = '독서' THEN 'book'
    WHEN cat.name = '요리' THEN 'cook'
  END, '.jpg')
WHERE (c.cover_key IS NULL OR c.cover_key LIKE 'cover/seed/%')
  AND (st.name IN ('일찍 일어나기', '산책', '물 마시기', '청소 · 정리', '식물 가꾸기')
       OR cat.name IN ('운동', '공부', '독서', '요리'));
