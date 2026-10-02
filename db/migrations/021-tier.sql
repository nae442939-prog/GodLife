-- 칭호(티어) 승급 · 강등.
-- 점수 = 인증 성공 1회 +10, 챌린지 완주 +50, 챌린지 실패 -30, 중간 포기 -50 (0점 아래로는 내려가지 않는다).
-- 칭호는 언제나 지금 점수에 맞춘다 → 점수가 오르면 승급, 실패 · 포기로 내려가면 강등.
-- 혜택은 챌린지에 걸 수 있는 포인트 한도(하루 · 한 달)와 고액 챌린지(참가 포인트 30,000P 이상, 플래티넘부터) 참여.
-- 브론즈 한도는 지금까지 모든 회원에게 쓰던 값(하루 3만 · 월 20만)과 같다. 가입 30일 이내의 낮은 한도는 그대로 따로 적용한다.
-- 이미 만든 DB 에 적용한다. (새로 만드는 DB 는 01-schema.sql · 02-seed.sql 에 이미 반영됨)
--   mysql -u godlife_user -p godlife < db/migrations/021-tier.sql

SET NAMES utf8mb4;

UPDATE tiers SET min_score = 0,    daily_bet_limit = 30000,  monthly_bet_limit = 200000,  high_stake_allowed = FALSE WHERE id = 1;
UPDATE tiers SET min_score = 100,  daily_bet_limit = 40000,  monthly_bet_limit = 300000,  high_stake_allowed = FALSE WHERE id = 2;
UPDATE tiers SET min_score = 300,  daily_bet_limit = 50000,  monthly_bet_limit = 400000,  high_stake_allowed = FALSE WHERE id = 3;
UPDATE tiers SET min_score = 700,  daily_bet_limit = 70000,  monthly_bet_limit = 600000,  high_stake_allowed = TRUE  WHERE id = 4;
UPDATE tiers SET min_score = 1500, daily_bet_limit = 100000, monthly_bet_limit = 1000000, high_stake_allowed = TRUE  WHERE id = 5;

ALTER TABLE notifications
  MODIFY type ENUM('SETTLEMENT','VERIFY_REMINDER','COMMENT','REPORT_RESULT','REPORT_ALERT',
                   'FOLLOW','MESSAGE_REQUEST','INQUIRY_ANSWER','VERIFY_REJECTED','ORDER','TIER') NOT NULL;
