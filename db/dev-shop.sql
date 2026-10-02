-- 개발용 더미 데이터: 포인트 상점 화면 확인용 스폰서 · 상품. 운영 DB 에는 넣지 않는다.
--   mysql -u godlife_user -p godlife < db/dev-shop.sql
-- 여러 번 실행해도 같은 이름의 스폰서 · 상품은 다시 넣지 않는다 (주문이 가리키는 상품을 지우지 않으려고 지우고 넣는 방식을 쓰지 않는다).
-- 상품 사진은 넣지 않는다 → 화면이 종류별 기본 그림을 보여 준다. 사진은 관리자 화면(/admin/shop)에서 올릴 수 있다.

SET NAMES utf8mb4;

INSERT INTO sponsors (name, contact_email)
SELECT d.name, d.email
FROM (
  SELECT '핏라이프' AS name, 'partner@fitlife.dummy' AS email
  UNION ALL SELECT '모닝페이퍼', 'hello@morningpaper.dummy'
  UNION ALL SELECT '데일리홈', 'biz@dailyhome.dummy'
  UNION ALL SELECT '갓생 제휴', 'partner@godlife.dummy'
) d
WHERE NOT EXISTS (SELECT 1 FROM sponsors s WHERE s.name = d.name);

INSERT INTO products (sponsor_id, category_id, type, name, description, price_points, stock, sold_count)
SELECT s.id, c.id, d.type, d.name, d.description, d.price, d.stock, d.sold
FROM (
  SELECT '핏라이프' AS sponsor, '운동용품' AS category, 'PHYSICAL' AS type, '미끄럼 방지 요가 매트 6mm' AS name,
         '집에서 스트레칭과 홈트레이닝을 할 때 쓰기 좋은 6mm 두께 매트예요.\n\n- 크기: 183 × 61cm\n- 미끄럼 방지 양면 엠보싱\n- 어깨끈 포함' AS description,
         12000 AS price, 30 AS stock, 41 AS sold
  UNION ALL SELECT '핏라이프', '운동용품', 'PHYSICAL', '러닝 암밴드 (휴대폰 수납)',
         '아침 러닝 인증 사진을 찍을 휴대폰을 팔에 고정해 줘요.\n\n- 6.7인치까지 수납\n- 땀에 강한 네오프렌 소재', 5000, 50, 27
  UNION ALL SELECT '핏라이프', '운동용품', 'PHYSICAL', '손목 보호 헬스 스트랩',
         '무게를 들 때 손목을 잡아 주는 스트랩 한 쌍이에요.', 4000, 0, 60
  UNION ALL SELECT '핏라이프', '운동용품', 'PHYSICAL', '보틀 1L (눈금 표시)',
         '하루 물 2L 챌린지에 딱 맞는 1L 보틀. 시간대별 눈금이 그려져 있어요.', 7000, 40, 35
  UNION ALL SELECT '모닝페이퍼', '문구류', 'PHYSICAL', '갓생 플래너 (6개월)',
         '하루 계획과 인증 여부를 적는 6개월 플래너예요.\n\n- 날짜 없는 만년형\n- 습관 트래커 24쪽', 9000, 25, 52
  UNION ALL SELECT '모닝페이퍼', '문구류', 'PHYSICAL', '젤 펜 5색 세트',
         '필기감이 부드러운 0.5mm 젤 펜 다섯 가지 색 세트.', 3000, 80, 18
  UNION ALL SELECT '모닝페이퍼', '문구류', 'PHYSICAL', '독서 기록 노트',
         '읽은 책의 문장과 느낀 점을 적는 노트. 독서 챌린지 인증에 쓰기 좋아요.', 4500, 35, 22
  UNION ALL SELECT '데일리홈', '생활용품', 'PHYSICAL', '무드등 겸용 기상 알람 시계',
         '일어날 시간에 맞춰 천천히 밝아지는 알람 시계예요. 새벽 기상 챌린지에 추천해요.', 18000, 12, 15
  UNION ALL SELECT '데일리홈', '생활용품', 'PHYSICAL', '밀프렙 도시락 용기 3개 세트',
         '집밥 챌린지를 위한 전자레인지용 도시락 용기 세트.', 8000, 20, 9
  UNION ALL SELECT '갓생 제휴', '이용권', 'COUPON', '헬스장 1일 이용권',
         '제휴 헬스장에서 하루 동안 쓸 수 있는 이용권이에요.\n결제하면 쿠폰 번호가 주문 내역에 바로 나와요.', 6000, 100, 73
  UNION ALL SELECT '갓생 제휴', '이용권', 'COUPON', '스터디카페 4시간 이용권',
         '제휴 스터디카페 4시간 이용권. 공부 챌린지 인증하러 가 보세요.', 5000, 100, 44
  UNION ALL SELECT '갓생 제휴', '이용권', 'COUPON', '요가 클래스 1회 체험권',
         '제휴 요가원 원데이 클래스 체험권이에요.', 10000, 30, 12
  UNION ALL SELECT '갓생 제휴', '상품권', 'COUPON', '도서 상품권 5,000원',
         '온라인 서점에서 쓸 수 있는 도서 상품권이에요.\n결제하면 쿠폰 번호가 주문 내역에 바로 나와요.', 5000, 200, 88
  UNION ALL SELECT '갓생 제휴', '상품권', 'COUPON', '도서 상품권 10,000원',
         '온라인 서점에서 쓸 수 있는 도서 상품권이에요.', 10000, 200, 39
) d
JOIN sponsors s ON s.name = d.sponsor
JOIN product_categories c ON c.name = d.category
WHERE NOT EXISTS (SELECT 1 FROM products p WHERE p.name = d.name);
