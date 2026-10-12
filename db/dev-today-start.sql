-- 체험용 더미 데이터: '오늘 시작'하는 무료 챌린지를 카테고리별로 넣는다. 운영 DB 에는 넣지 않는다.
--   mysql -u godlife_user -p godlife < db/dev-today-start.sql
-- 링크를 받은 사람이 가입하자마자 참여해서 그날 바로 카메라 인증(AI 판정)까지 해 볼 수 있게 하는 용도다.
--   · 참여는 시작일 당일까지 되고, 인증은 시작일부터 된다 → 시작일이 '오늘'이어야 둘 다 된다.
--   · DEMO_KEEP_DATES=true 면 서버가 더미 챌린지 날짜를 매일 하루씩 밀어서, 이 챌린지들은 늘 '오늘 시작'으로 남는다.
-- 먼저 db/dev-dummy.sql 로 더미 회원(@dummy.godlife)이 들어 있어야 한다. 같은 제목이 이미 있으면 건너뛴다.

SET NAMES utf8mb4;

INSERT INTO challenges
  (host_id, category_id, sub_type_id, title, description, mode, visibility, invite_code, start_date, end_date,
   frequency_type, weekly_count, entry_fee, min_bet, max_bet, max_participants, verify_from, verify_until,
   partial_refund, status)
SELECT u.id, d.category_id, d.sub_type_id, d.title, d.description, 'FREE',
       'PUBLIC', UPPER(SUBSTRING(REPLACE(UUID(), '-', ''), 1, 8)),
       CURDATE(), CURDATE() + INTERVAL 29 DAY,
       'DAILY', NULL, 0, 0, 0, 100, NULL, NULL, FALSE, 'ONGOING'
FROM (
  SELECT 'gym@dummy.godlife' email, 1 category_id, NULL sub_type_id, '하루 10분 스트레칭' title,
         '하루 10분만 몸을 풀어요. 운동하는 모습이나 요가 매트, 운동 기구를 찍어 인증해요.' description
  UNION ALL SELECT 'toeic@dummy.godlife', 2, NULL, '하루 30분 공부 인증',
         '딱 30분만 집중해요. 펼친 책이나 노트, 필기하는 책상을 찍어 주세요.'
  UNION ALL SELECT 'early@dummy.godlife', 3, NULL, '자기 전 독서 10쪽',
         '자기 전에 10쪽만 읽어요. 읽고 있는 책 페이지를 찍어 인증해요.'
  UNION ALL SELECT 'water@dummy.godlife', 5, 3, '물 한 잔으로 하루 시작',
         '일어나서 물 한 잔. 물이 담긴 컵이나 텀블러를 찍어 주세요.'
) d
JOIN users u ON u.id = COALESCE((SELECT id FROM users WHERE email = d.email),
                               (SELECT MIN(id) FROM users WHERE email LIKE '%@dummy.godlife'))
WHERE NOT EXISTS (SELECT 1 FROM challenges c WHERE c.title = d.title);

-- 참가자: 더미 회원들을 챌린지마다 다르게 참여시킨다 (참가자가 없던 모집 중 더미 챌린지도 같이 채운다)
INSERT INTO challenge_participants (challenge_id, user_id, deposit_amount)
SELECT c.id, u.id, c.entry_fee
FROM challenges c
JOIN users host ON host.id = c.host_id AND host.email LIKE '%@dummy.godlife'
JOIN users u ON u.email LIKE '%@dummy.godlife'
WHERE c.status IN ('RECRUITING', 'ONGOING')
  AND NOT EXISTS (SELECT 1 FROM challenge_participants x WHERE x.challenge_id = c.id)
  AND (c.id + u.id * 2) % 3 <> 0;

-- 참가자 수를 실제 참가자와 맞춘다 (더미 챌린지만)
UPDATE challenges c
JOIN users host ON host.id = c.host_id AND host.email LIKE '%@dummy.godlife'
SET c.participant_count = (SELECT COUNT(*) FROM challenge_participants p
                           WHERE p.challenge_id = c.id AND p.status IN ('ACTIVE', 'COMPLETED', 'FAILED'))
WHERE c.status IN ('RECRUITING', 'ONGOING');

SELECT c.id, c.title, c.status, c.start_date, c.participant_count
FROM challenges c
WHERE c.start_date = CURDATE() AND c.mode = 'FREE' AND c.visibility = 'PUBLIC'
ORDER BY c.id;
