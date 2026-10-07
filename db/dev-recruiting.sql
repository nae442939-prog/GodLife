-- 개발용 더미 데이터: '모집 중' 챌린지 6개를 더 넣는다 (기타 카테고리 세부 종류 포함). 운영 DB 에는 넣지 않는다.
--   mysql -u godlife_user -p godlife < db/dev-recruiting.sql
-- 먼저 db/dev-dummy.sql 로 더미 회원(@dummy.godlife)이 들어 있어야 한다. 같은 제목이 이미 있으면 건너뛴다.
-- 날짜는 실행한 날(CURDATE) 기준이고, DEMO_KEEP_DATES=true 면 서버가 매일 날짜를 밀어 줘서 계속 모집 중으로 남는다.

SET NAMES utf8mb4;

INSERT INTO challenges
  (host_id, category_id, sub_type_id, title, description, mode, visibility, invite_code, start_date, end_date,
   frequency_type, weekly_count, entry_fee, min_bet, max_bet, max_participants, verify_from, verify_until,
   partial_refund)
SELECT u.id, d.category_id, d.sub_type_id, d.title, d.description, d.mode,
       'PUBLIC', UPPER(SUBSTRING(REPLACE(UUID(), '-', ''), 1, 8)),
       CURDATE() + INTERVAL d.start_in DAY, CURDATE() + INTERVAL (d.start_in + d.days - 1) DAY,
       d.frequency_type, d.weekly_count, d.fee, d.fee, d.fee, d.max_p, d.v_from, d.v_until, FALSE
FROM (
  SELECT 'early@dummy.godlife' email, 5 category_id, 1 sub_type_id, '미라클 모닝 5시 30분' title,
         '2주 동안 5시 30분에 일어나요. 알람 시계나 정리한 이불을 06:00 전에 찍어 인증해요.' description,
         'BET' mode, 3 start_in, 14 days, 'DAILY' frequency_type, NULL weekly_count, 2000 fee, 30 max_p,
         '05:00:00' v_from, '06:00:00' v_until
  UNION ALL SELECT 'walk@dummy.godlife', 5, 2, '점심시간 동네 한 바퀴',
         '점심 먹고 15분만 걸어요. 걷는 길이나 공원 풍경을 찍어 주세요.',
         'FREE', 2, 21, 'DAILY', NULL, 0, 40, NULL, NULL
  UNION ALL SELECT 'water@dummy.godlife', 5, 3, '하루 물 8잔 마시기',
         '물 마시는 습관 만들기. 물이 담긴 컵이나 텀블러를 찍어 주세요.',
         'FREE', 4, 14, 'DAILY', NULL, 0, 50, NULL, NULL
  UNION ALL SELECT 'yoga@dummy.godlife', 5, 4, '자기 전 10분 방 정리',
         '하루 끝에 책상과 방을 정리해요. 정리한 모습을 찍어 인증해요.',
         'FREE', 5, 14, 'DAILY', NULL, 0, 30, NULL, NULL
  UNION ALL SELECT 'salad@dummy.godlife', 5, 5, '반려식물 물 주기 기록',
         '주 3회 화분을 돌봐요. 물 준 화분이나 새로 난 잎을 찍어 주세요.',
         'FREE', 6, 28, 'WEEKLY_N', 3, 0, 25, NULL, NULL
  UNION ALL SELECT 'gym@dummy.godlife', 1, NULL, '퇴근 후 홈트 20분',
         '3주 동안 매일 홈트. 요가 매트나 운동 기록 화면을 찍어 주세요.',
         'BET', 7, 21, 'DAILY', NULL, 3000, 20, NULL, NULL
) d JOIN users u ON u.email = d.email
WHERE NOT EXISTS (SELECT 1 FROM challenges c WHERE c.title = d.title);

-- 참가자: 더미 회원들을 챌린지마다 다르게 참여시킨다 (이미 참여한 사람은 건너뛴다)
INSERT INTO challenge_participants (challenge_id, user_id, deposit_amount)
SELECT c.id, u.id, c.entry_fee
FROM challenges c
JOIN users u ON u.email LIKE '%@dummy.godlife'
WHERE c.title IN ('미라클 모닝 5시 30분', '점심시간 동네 한 바퀴', '하루 물 8잔 마시기', '자기 전 10분 방 정리',
                  '반려식물 물 주기 기록', '퇴근 후 홈트 20분')
  AND (c.id + u.id * 2) % 3 <> 0
  AND NOT EXISTS (SELECT 1 FROM challenge_participants p WHERE p.challenge_id = c.id AND p.user_id = u.id);
