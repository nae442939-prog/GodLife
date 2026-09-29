-- 개발용 더미 데이터: 챌린지 목록/상세 화면 확인용. 운영 DB 에는 넣지 않는다.
--   mysql -u godlife_user -p godlife < db/dev-dummy.sql
-- 더미 회원은 이메일이 @dummy.godlife 로 끝난다. 다시 실행하면 이전 더미를 지우고 새로 넣는다.
-- 날짜는 실행한 날(CURDATE) 기준이라 언제 넣어도 '모집 중'으로 보인다.

SET NAMES utf8mb4;

-- ---------- 이전 더미 지우기 ----------
DELETE p FROM challenge_participants p
  JOIN users u ON u.id = p.user_id WHERE u.email LIKE '%@dummy.godlife';
DELETE p FROM challenge_participants p
  JOIN challenges c ON c.id = p.challenge_id
  JOIN users u ON u.id = c.host_id WHERE u.email LIKE '%@dummy.godlife';
DELETE c FROM challenges c
  JOIN users u ON u.id = c.host_id WHERE u.email LIKE '%@dummy.godlife';
DELETE FROM users WHERE email LIKE '%@dummy.godlife';

-- ---------- 더미 회원 12명 (비밀번호·휴대폰 없음 → 로그인 불가, 화면 표시용) ----------
INSERT INTO users (email, nickname) VALUES
  ('runner@dummy.godlife',   '아침러너'),
  ('study@dummy.godlife',    '토익900'),
  ('book@dummy.godlife',     '책벌레'),
  ('cook@dummy.godlife',     '집밥요정'),
  ('early@dummy.godlife',    '새벽형인간'),
  ('walk@dummy.godlife',     '산책왕'),
  ('gym@dummy.godlife',      '헬린이'),
  ('code@dummy.godlife',     '코딩하는곰'),
  ('water@dummy.godlife',    '물두잔'),
  ('yoga@dummy.godlife',     '요가하는날'),
  ('memo@dummy.godlife',     '필사러'),
  ('salad@dummy.godlife',    '샐러드한접시');

-- ---------- 챌린지 10개 (운동·공부·독서·요리·기타 / 무료·포인트) ----------
-- '한 달 3권 완독' 만 비공개(초대 링크 전용) 예시. 초대 코드는 더미라 UUID 앞 8자리로 채운다.
INSERT INTO challenges
  (host_id, category_id, title, description, mode, visibility, invite_code, start_date, end_date,
   frequency_type, weekly_count, entry_fee, min_bet, max_bet, max_participants, verify_from, verify_until,
   partial_refund)
SELECT u.id, d.category_id, d.title, d.description, d.mode,
       IF(d.title = '한 달 3권 완독', 'PRIVATE', 'PUBLIC'), UPPER(SUBSTRING(REPLACE(UUID(), '-', ''), 1, 8)),
       CURDATE() + INTERVAL d.start_in DAY, CURDATE() + INTERVAL (d.start_in + d.days - 1) DAY,
       d.frequency_type, d.weekly_count, d.fee, d.fee, d.fee, d.max_p, d.v_from, d.v_until, d.partial
FROM (
  SELECT 'runner@dummy.godlife' email, 1 category_id, '매일 30분 달리기' title,
         '2주 동안 매일 러닝 인증하고 건강한 습관 만들어요. 러닝 앱 기록 화면이나 운동화가 보이게 찍어 주세요.' description,
         'FREE' mode, 1 start_in, 14 days, 'DAILY' frequency_type, NULL weekly_count, 0 fee, 30 max_p,
         NULL v_from, NULL v_until, FALSE partial
  UNION ALL SELECT 'study@dummy.godlife', 2, '토익 스터디 인증',
         '4주간 매일 학습 인증, 포인트 걸고 진짜 갓생 살기. 문제집 푼 페이지를 찍어 주세요.',
         'BET', 2, 28, 'DAILY', NULL, 2000, 50, NULL, NULL, TRUE
  UNION ALL SELECT 'book@dummy.godlife', 3, '하루 20페이지 읽기',
         '3주 동안 매일 독서 인증하고 완독까지 가봐요. 읽은 페이지 번호가 보이게 찍어 주세요.',
         'FREE', 1, 21, 'DAILY', NULL, 0, 40, NULL, NULL, FALSE
  UNION ALL SELECT 'cook@dummy.godlife', 4, '주 3회 집밥 해먹기',
         '배달 대신 직접 만든 한 끼. 완성된 요리 사진을 올려 주세요.',
         'FREE', 3, 21, 'WEEKLY_N', 3, 0, 20, NULL, NULL, FALSE
  UNION ALL SELECT 'early@dummy.godlife', 5, '아침 6시 기상',
         '하루를 일찍 시작해요. 06:30 전에 창밖 아침 풍경이나 시계를 찍어 인증해요.',
         'BET', 1, 14, 'DAILY', NULL, 1000, 25, '05:00:00', '06:30:00', FALSE
  UNION ALL SELECT 'walk@dummy.godlife', 5, '저녁 산책 30분',
         '하루 한 번 밖에 나가 걷기. 산책길이나 공원 풍경을 찍어 주세요.',
         'FREE', 2, 14, 'DAILY', NULL, 0, 50, NULL, NULL, FALSE
  UNION ALL SELECT 'gym@dummy.godlife', 1, '헬스장 주 4회 출석',
         '한 달 동안 주 4회 헬스장 가기. 운동 기구나 헬스장 입구가 보이게 찍어 주세요.',
         'BET', 4, 30, 'WEEKLY_N', 4, 5000, 15, NULL, NULL, TRUE
  UNION ALL SELECT 'code@dummy.godlife', 2, '매일 알고리즘 1문제',
         '코딩 테스트 대비, 매일 한 문제씩. 제출 결과 화면을 찍어 주세요.',
         'FREE', 1, 31, 'DAILY', NULL, 0, 60, NULL, NULL, FALSE
  UNION ALL SELECT 'memo@dummy.godlife', 3, '한 달 3권 완독',
         '한 달에 책 세 권. 읽은 부분을 필사하거나 책 표지와 함께 찍어 주세요.',
         'BET', 5, 30, 'WEEKLY_N', 5, 3000, 20, NULL, NULL, FALSE
  UNION ALL SELECT 'salad@dummy.godlife', 4, '점심 샐러드 챙겨 먹기',
         '평일 점심은 가볍게. 직접 만든 샐러드 사진으로 인증해요.',
         'FREE', 2, 10, 'WEEKLY_N', 5, 0, 30, NULL, NULL, FALSE
) d JOIN users u ON u.email = d.email;

-- ---------- 참가자: 더미 회원들을 챌린지마다 다르게 참여시킨다 ----------
-- (챌린지 id + 회원 id) 를 섞어서 일부만 고른다. 개설자도 참여할 수 있다.
INSERT INTO challenge_participants (challenge_id, user_id, deposit_amount)
SELECT c.id, u.id, c.entry_fee
FROM challenges c
JOIN users h ON h.id = c.host_id AND h.email LIKE '%@dummy.godlife'
JOIN users u ON u.email LIKE '%@dummy.godlife'
WHERE (c.id * 7 + u.id * 3) % 5 <> 0
  AND (c.id + u.id) % (1 + c.id % 3) = 0;

-- 참가자 수 카운터를 실제 참가자 수에 맞춘다.
UPDATE challenges c
JOIN users h ON h.id = c.host_id AND h.email LIKE '%@dummy.godlife'
SET c.participant_count = (SELECT COUNT(*) FROM challenge_participants p
                           WHERE p.challenge_id = c.id AND p.status = 'ACTIVE');
