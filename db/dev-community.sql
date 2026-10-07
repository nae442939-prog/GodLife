-- 개발용 더미 데이터: 커뮤니티 목록/상세 화면 확인용. 운영 DB 에는 넣지 않는다.
--   mysql -u godlife_user -p godlife < db/dev-community.sql
-- dev-dummy.sql 의 더미 회원(@dummy.godlife)이 쓴 글 · 댓글 · 좋아요를 넣는다. (dev-dummy.sql 을 먼저 실행해야 한다)
-- 다시 실행하면 더미 회원이 쓴 이전 글을 지우고 새로 넣는다. 날짜는 실행한 시각 기준이다.

SET NAMES utf8mb4;

-- ---------- 이전 더미 지우기 (댓글 · 좋아요 · 사진은 글과 함께 지워진다) ----------
DELETE r FROM community_reports r
  JOIN posts p ON r.target_type = 'POST' AND p.id = r.target_id
  JOIN users u ON u.id = p.user_id WHERE u.email LIKE '%@dummy.godlife';
DELETE l FROM post_comment_likes l
  JOIN users u ON u.id = l.user_id WHERE u.email LIKE '%@dummy.godlife';
DELETE c FROM post_comments c
  JOIN users u ON u.id = c.user_id WHERE u.email LIKE '%@dummy.godlife';
DELETE l FROM post_likes l
  JOIN users u ON u.id = l.user_id WHERE u.email LIKE '%@dummy.godlife';
DELETE p FROM posts p
  JOIN users u ON u.id = p.user_id WHERE u.email LIKE '%@dummy.godlife';

-- ---------- 글 ----------
INSERT INTO posts (user_id, topic, title, content, challenge_title, verify_date, verify_result, created_at)
SELECT u.id, d.topic, d.title, d.content, d.challenge_title,
       IF(d.verify_result IS NULL, NULL, DATE(NOW() - INTERVAL d.hours_ago HOUR)), d.verify_result,
       NOW() - INTERVAL d.hours_ago HOUR
FROM (
  SELECT '아침러너' AS nickname, 'REVIEW' AS topic, '러닝 챌린지 3주차, 확실히 아침에 덜 피곤해요' AS title,
         '처음 일주일은 진짜 힘들었는데 3주차가 되니까 알람 없이도 눈이 떠져요.\n오늘도 5km 뛰고 인증 완료!\n\n같이 뛰는 분들 덕분에 안 빠지게 되네요. 다들 화이팅이에요.' AS content,
         '아침 6시 기상 러닝' AS challenge_title, 'SUCCESS' AS verify_result, 2 AS hours_ago
  UNION ALL SELECT '토익900', 'TIP', '인증샷 팁 - 이렇게 찍으면 한 번에 통과돼요',
         '공부 인증은 책이랑 필기한 손이 같이 나오게 찍으면 잘 통과되더라고요.\n\n1. 밝은 곳에서 찍기\n2. 책/노트가 화면 가운데 오게\n3. 너무 가까이 찍지 않기\n\n어두운 스탠드 조명에서 찍으면 확인 중으로 넘어갈 때가 있었어요.',
         NULL, NULL, 5
  UNION ALL SELECT '헬린이', 'QUESTION', '주 3회 챌린지는 무슨 요일에 해도 되나요?',
         '헬스 주 3회 챌린지에 들어갔는데요, 꼭 월수금처럼 정해진 요일에 해야 하나요?\n아니면 그 주 안에 3번만 채우면 되는 건지 궁금합니다.',
         NULL, NULL, 9
  UNION ALL SELECT '책벌레', 'REVIEW', '하루 30쪽 읽기, 오늘은 아직 못 읽었네요',
         '퇴근이 늦어서 아직 책을 못 폈어요. 자기 전에 꼭 읽고 인증 올리겠습니다.\n이번 달 벌써 두 권째인데, 혼자였으면 절대 못 했을 거예요.',
         '하루 30쪽 독서', 'FAIL', 14
  UNION ALL SELECT '집밥요정', 'FREE', '챌린지 덕분에 배달 앱을 지웠어요',
         '집밥 챌린지 2주 했더니 배달비 아낀 돈이 꽤 돼요.\n오늘 저녁은 된장찌개랑 계란말이! 요리 인증 사진 찍는 재미도 있네요.',
         NULL, NULL, 26
  UNION ALL SELECT '새벽형인간', 'TIP', '포기하고 싶을 때 저는 이렇게 버텨요',
         '1. 전날 밤에 준비물을 미리 꺼내 둔다\n2. 오늘 딱 5분만 한다고 생각한다\n3. 인증하고 채팅방에 한마디 남긴다\n\n특히 3번이 효과가 좋았어요. 누가 보고 있다고 생각하면 안 빠지게 됩니다.',
         NULL, NULL, 31
  UNION ALL SELECT '코딩하는곰', 'QUESTION', '포인트 챌린지 처음인데 얼마 정도 거는 게 좋을까요?',
         '무료 챌린지는 두 번 완주했고 이번에 포인트 챌린지에 도전해 보려고 해요.\n처음엔 얼마 정도로 시작하셨는지 궁금합니다. 너무 적으면 긴장감이 없을 것 같고요.',
         NULL, NULL, 50
  UNION ALL SELECT '물두잔', 'FREE', '물 2L 마시기 30일 완주했습니다',
         '피부가 좋아졌다는 말을 처음 들어 봤어요.\n별거 아닌 습관인데 한 달을 채우니까 뿌듯하네요. 다음은 스트레칭 챌린지 갑니다.',
         NULL, NULL, 75
  UNION ALL SELECT '산책왕', 'REVIEW', '점심 산책 20분, 오후 졸음이 사라졌어요',
         '점심 먹고 회사 주변을 한 바퀴 도는 걸로 시작했는데 벌써 열흘째예요.\n오늘도 걷고 와서 인증했습니다.',
         '점심 산책 20분', 'SUCCESS', 98
) d
JOIN users u ON u.nickname = d.nickname AND u.email LIKE '%@dummy.godlife'
-- 최신순은 글 id 순이라 오래된 글부터 넣는다
ORDER BY d.hours_ago DESC;

-- ---------- 댓글 ----------
INSERT INTO post_comments (post_id, user_id, content, created_at)
SELECT p.id, u.id, d.content, p.created_at + INTERVAL d.minutes_after MINUTE
FROM (
  SELECT '러닝 챌린지 3주차, 확실히 아침에 덜 피곤해요' AS title, '새벽형인간' AS nickname,
         '3주차면 이제 습관이 됐네요. 저도 내일 같이 뜁니다!' AS content, 12 AS minutes_after
  UNION ALL SELECT '러닝 챌린지 3주차, 확실히 아침에 덜 피곤해요', '헬린이', '와 5km... 저는 아직 2km도 힘들어요', 35
  UNION ALL SELECT '인증샷 팁 - 이렇게 찍으면 한 번에 통과돼요', '책벌레', '독서 인증도 책 표지보다 펼친 쪽이 잘 되더라고요', 20
  UNION ALL SELECT '인증샷 팁 - 이렇게 찍으면 한 번에 통과돼요', '코딩하는곰', '꿀팁 감사합니다. 어제 확인 중으로 넘어가서 당황했어요', 48
  UNION ALL SELECT '인증샷 팁 - 이렇게 찍으면 한 번에 통과돼요', '아침러너', '운동은 운동화나 기구가 같이 나오면 잘 돼요', 80
  UNION ALL SELECT '주 3회 챌린지는 무슨 요일에 해도 되나요?', '토익900', '그 주 안에 3번만 채우면 돼요. 한 주는 챌린지 시작일부터 7일씩이에요!', 9
  UNION ALL SELECT '포기하고 싶을 때 저는 이렇게 버텨요', '집밥요정', '5분만 한다고 생각하기, 이거 진짜 효과 있어요', 40
  UNION ALL SELECT '포기하고 싶을 때 저는 이렇게 버텨요', '물두잔', '저장해 둡니다', 95
  UNION ALL SELECT '포인트 챌린지 처음인데 얼마 정도 거는 게 좋을까요?', '새벽형인간', '저는 1,000P 로 시작했어요. 잃으면 아쉬운 정도가 딱 좋더라고요', 30
  UNION ALL SELECT '물 2L 마시기 30일 완주했습니다', '산책왕', '완주 축하해요!', 22
) d
JOIN posts p ON p.title = d.title
JOIN users pu ON pu.id = p.user_id AND pu.email LIKE '%@dummy.godlife'
JOIN users u ON u.nickname = d.nickname AND u.email LIKE '%@dummy.godlife';

-- ---------- 좋아요: 글마다 더미 회원 몇 명씩 (글 id + 회원 id 로 고르게 섞는다) ----------
INSERT INTO post_likes (post_id, user_id)
SELECT p.id, u.id
FROM posts p
JOIN users pu ON pu.id = p.user_id AND pu.email LIKE '%@dummy.godlife'
JOIN users u ON u.email LIKE '%@dummy.godlife' AND u.id <> p.user_id
WHERE (p.id * 7 + u.id * 3) % 10 < CASE p.topic WHEN 'TIP' THEN 8 WHEN 'REVIEW' THEN 5 ELSE 3 END;

-- ---------- 글의 좋아요 · 댓글 수 맞추기 ----------
UPDATE posts p
JOIN users u ON u.id = p.user_id AND u.email LIKE '%@dummy.godlife'
SET p.like_count = (SELECT COUNT(*) FROM post_likes l WHERE l.post_id = p.id),
    p.comment_count = (SELECT COUNT(*) FROM post_comments c WHERE c.post_id = p.id AND c.status = 'VISIBLE');
