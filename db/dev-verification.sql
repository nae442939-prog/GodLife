-- 개발용 더미: '오늘의 인증' 화면 확인용 진행 중 챌린지. 운영 DB 에는 넣지 않는다.
--   bash db/dev-verification.sh   (이 SQL 을 넣고 더미 인증 사진 파일까지 만든다)
-- dev-dummy.sql 의 더미 회원이 먼저 있어야 한다. 다시 실행하면 이전 것을 지우고 새로 넣는다.
-- 사흘 전에 시작한 21일 챌린지에, 휴대폰 인증을 마친 실제 회원(나)도 참가자로 넣어 [오늘 인증하기]를 눌러 볼 수 있게 한다.

SET NAMES utf8mb4;
SET @title = '하루 물 2L 마시기';

-- ---------- 이전 것 지우기 ----------
DELETE v FROM verifications v
  JOIN challenge_participants p ON p.id = v.participant_id
  JOIN challenges c ON c.id = p.challenge_id
  JOIN users u ON u.id = c.host_id
  WHERE c.title = @title AND u.email LIKE '%@dummy.godlife';
DELETE m FROM chat_messages m
  JOIN challenges c ON c.id = m.challenge_id
  JOIN users u ON u.id = c.host_id
  WHERE c.title = @title AND u.email LIKE '%@dummy.godlife';
DELETE p FROM challenge_participants p
  JOIN challenges c ON c.id = p.challenge_id
  JOIN users u ON u.id = c.host_id
  WHERE c.title = @title AND u.email LIKE '%@dummy.godlife';
DELETE c FROM challenges c
  JOIN users u ON u.id = c.host_id
  WHERE c.title = @title AND u.email LIKE '%@dummy.godlife';

-- ---------- 진행 중 챌린지 (사흘 전 시작, 21일) ----------
INSERT INTO challenges
  (host_id, category_id, title, description, mode, visibility, invite_code, start_date, end_date,
   frequency_type, weekly_count, entry_fee, min_bet, max_bet, max_participants, status)
SELECT id, 5, @title,
       '3주 동안 매일 물 2L 마시기. 다 마신 물병이나 물컵이 보이게 찍어 주세요.',
       'FREE', 'PUBLIC', UPPER(SUBSTRING(REPLACE(UUID(), '-', ''), 1, 8)),
       CURDATE() - INTERVAL 3 DAY, CURDATE() + INTERVAL 17 DAY,
       'DAILY', NULL, 0, 0, 0, 20, 'ONGOING'
FROM users WHERE email = 'water@dummy.godlife';
SET @cid = LAST_INSERT_ID();

-- 더미 참가자 6명: 사흘 모두 인증함. 앞의 4명은 오늘도 인증함.
INSERT INTO challenge_participants (challenge_id, user_id, success_days, current_streak, max_streak)
SELECT @cid, u.id, d.days, d.days, d.days
FROM (
  SELECT 'water@dummy.godlife' email, 4 days, 1 ord
  UNION ALL SELECT 'walk@dummy.godlife', 4, 2
  UNION ALL SELECT 'early@dummy.godlife', 4, 3
  UNION ALL SELECT 'yoga@dummy.godlife', 4, 4
  UNION ALL SELECT 'gym@dummy.godlife', 3, 5
  UNION ALL SELECT 'book@dummy.godlife', 3, 6
) d JOIN users u ON u.email = d.email
ORDER BY d.ord;

-- 휴대폰 인증을 마친 실제 회원(더미 아님)은 모두 참가자로 넣는다. 아직 인증 0회.
INSERT INTO challenge_participants (challenge_id, user_id)
SELECT @cid, id FROM users WHERE phone_hash IS NOT NULL AND email NOT LIKE '%@dummy.godlife';

UPDATE challenges
SET participant_count = (SELECT COUNT(*) FROM challenge_participants WHERE challenge_id = @cid)
WHERE id = @cid;

-- ---------- 더미 인증 (사흘 전 ~ 어제 + 오늘 4명) ----------
-- 사진 파일은 dev-verification.sh 가 image_url 경로에 만든다.
INSERT INTO verifications (participant_id, verify_date, received_at, image_url, image_hash, status)
SELECT p.id, d.day,
       IF(d.day = CURDATE(),
          GREATEST(TIMESTAMP(CURDATE()), NOW() - INTERVAL (10 + p.id % 50) MINUTE),
          TIMESTAMP(d.day, MAKETIME(7 + p.id % 12, p.id * 7 % 60, 0))),
       CONCAT('verification/', @cid, '/dummy-', p.id, '-', DATE_FORMAT(d.day, '%Y%m%d'), '.jpg'),
       SHA2(CONCAT('dummy-', p.id, '-', d.day), 256),
       'APPROVED'
FROM challenge_participants p
  JOIN users u ON u.id = p.user_id
  JOIN (
    SELECT CURDATE() - INTERVAL 3 DAY day
    UNION ALL SELECT CURDATE() - INTERVAL 2 DAY
    UNION ALL SELECT CURDATE() - INTERVAL 1 DAY
    UNION ALL SELECT CURDATE()
  ) d
WHERE p.challenge_id = @cid AND u.email LIKE '%@dummy.godlife'
  AND (d.day < CURDATE() OR p.success_days = 4);

-- 사진 파일 만들기용 목록 (스크립트가 읽는다): 경로 \t 닉네임 \t 날짜
SELECT v.image_url, u.nickname, v.verify_date
FROM verifications v
  JOIN challenge_participants p ON p.id = v.participant_id
  JOIN users u ON u.id = p.user_id
WHERE p.challenge_id = @cid;
