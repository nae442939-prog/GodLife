-- 개발용 더미 데이터: 챌린지 목록/상세 화면 확인용. 운영 DB 에는 넣지 않는다.
--   mysql -u godlife_user -p godlife < db/dev-dummy.sql
-- 더미 회원은 이메일이 @dummy.godlife 로 끝난다. 다시 실행하면 이전 더미를 지우고 새로 넣는다.
-- 날짜는 실행한 날(CURDATE) 기준이라 언제 넣어도 '모집 중'으로 보인다.

SET NAMES utf8mb4;

-- ---------- 이전 더미 지우기 ----------
DELETE m FROM chat_messages m
  JOIN users u ON u.id = m.sender_id WHERE u.email LIKE '%@dummy.godlife';
DELETE m FROM chat_messages m
  JOIN challenges c ON c.id = m.challenge_id
  JOIN users u ON u.id = c.host_id WHERE u.email LIKE '%@dummy.godlife';
DELETE v FROM verifications v
  JOIN challenge_participants p ON p.id = v.participant_id
  JOIN users u ON u.id = p.user_id WHERE u.email LIKE '%@dummy.godlife';
DELETE v FROM verifications v
  JOIN challenge_participants p ON p.id = v.participant_id
  JOIN challenges c ON c.id = p.challenge_id
  JOIN users u ON u.id = c.host_id WHERE u.email LIKE '%@dummy.godlife';
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

-- ---------- 오픈채팅: 챌린지별로 참가자들이 나눈 대화 ----------
-- ago = 몇 분 전에 보냈는지. 오래된 것부터 넣어서 id 순서 = 시간 순서가 되게 한다.
CREATE TEMPORARY TABLE dummy_chat (title VARCHAR(100), email VARCHAR(100), ago INT, content VARCHAR(500));
INSERT INTO dummy_chat VALUES
  ('저녁 산책 30분', 'walk@dummy.godlife',   1440, '산책 챌린지 열었어요! 다들 반가워요 🙌'),
  ('저녁 산책 30분', 'early@dummy.godlife',  1430, '저 들어왔어요. 퇴근하고 동네 한 바퀴 도는 게 목표예요'),
  ('저녁 산책 30분', 'water@dummy.godlife',  1415, '인증 사진은 길이나 하늘 찍으면 되는 거죠?'),
  ('저녁 산책 30분', 'walk@dummy.godlife',   1410, '네! 밖이라는 게 보이면 돼요. 가로등이나 공원 벤치 같은 거요'),
  ('저녁 산책 30분', 'yoga@dummy.godlife',   300,  '오늘 한강 쪽 갔는데 바람 완전 좋았어요'),
  ('저녁 산책 30분', 'early@dummy.godlife',  290,  '부럽다… 저는 비 와서 우산 쓰고 20분 걸었어요 ☔'),
  ('저녁 산책 30분', 'water@dummy.godlife',  120,  '팁: 이어폰 끼고 팟캐스트 들으면 30분 금방 가요'),
  ('저녁 산책 30분', 'walk@dummy.godlife',   45,   '오늘도 다들 잊지 말고 인증해요! 연속 기록 깨지지 않게 🔥'),
  ('매일 30분 달리기', 'runner@dummy.godlife', 720, '러닝 앱 기록 화면 캡처로 인증해 주세요!'),
  ('매일 30분 달리기', 'gym@dummy.godlife',    700, '처음이라 5km는 무리고 3km부터 할게요 ㅎㅎ'),
  ('매일 30분 달리기', 'runner@dummy.godlife', 695, '충분해요! 거리보다 매일 나가는 게 중요해요'),
  ('매일 30분 달리기', 'salad@dummy.godlife',  60,  '아침 공기 최고… 오늘 페이스 6분대 찍었어요'),
  ('토익 스터디 인증', 'study@dummy.godlife',  900, 'RC 파트5 하루 30문제씩 풀어요. 문제집 페이지 보이게 찍어 주세요'),
  ('토익 스터디 인증', 'code@dummy.godlife',   880, 'LC는 쉐도잉 녹음 화면으로 인증해도 될까요?'),
  ('토익 스터디 인증', 'study@dummy.godlife',  870, '좋아요! 공부한 흔적만 보이면 됩니다'),
  ('토익 스터디 인증', 'memo@dummy.godlife',   30,  '오늘 모의고사 780 나왔어요. 900까지 가봅시다'),
  ('주 3회 집밥 해먹기', 'cook@dummy.godlife',  400, '오늘은 김치볶음밥! 계란 올리면 사진이 예뻐요 🍳'),
  ('주 3회 집밥 해먹기', 'salad@dummy.godlife', 380, '장보기 리스트 공유해 주실 분 ㅠㅠ'),
  ('주 3회 집밥 해먹기', 'cook@dummy.godlife',  375, '두부, 계란, 양파, 대파만 있으면 일주일 버텨요'),
  ('아침 6시 기상', 'early@dummy.godlife', 200, '06:30 전에 창밖 사진이에요. 알람은 침대에서 먼 곳에 두세요!'),
  ('아침 6시 기상', 'yoga@dummy.godlife',  190, '일어나자마자 물 한 잔 마시니까 덜 졸려요'),
  ('하루 20페이지 읽기', 'book@dummy.godlife', 500, '요즘 읽는 책 추천해 주세요 📚'),
  ('하루 20페이지 읽기', 'memo@dummy.godlife', 480, '「아주 작은 습관의 힘」 이 챌린지랑 딱 맞아요');

-- 말한 사람은 그 챌린지 참가자여야 채팅에 있을 수 있으니, 아직 아니면 참가자로 넣는다.
INSERT IGNORE INTO challenge_participants (challenge_id, user_id, deposit_amount)
SELECT DISTINCT c.id, u.id, c.entry_fee
FROM dummy_chat d
JOIN challenges c ON c.title = d.title
JOIN users h ON h.id = c.host_id AND h.email LIKE '%@dummy.godlife'
JOIN users u ON u.email = d.email;

INSERT INTO chat_messages (challenge_id, sender_id, content, created_at)
SELECT c.id, u.id, d.content, NOW(3) - INTERVAL d.ago MINUTE
FROM dummy_chat d
JOIN challenges c ON c.title = d.title
JOIN users h ON h.id = c.host_id AND h.email LIKE '%@dummy.godlife'
JOIN users u ON u.email = d.email
ORDER BY d.ago DESC;

DROP TEMPORARY TABLE dummy_chat;

-- 참가자 수 카운터를 실제 참가자 수에 맞춘다.
UPDATE challenges c
JOIN users h ON h.id = c.host_id AND h.email LIKE '%@dummy.godlife'
SET c.participant_count = (SELECT COUNT(*) FROM challenge_participants p
                           WHERE p.challenge_id = c.id AND p.status = 'ACTIVE');
