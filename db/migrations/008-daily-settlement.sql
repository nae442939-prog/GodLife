-- 매일 결과: 포인트 챌린지는 참가 포인트를 하루(주 N회는 한 주) 몫으로 나누고, 기간이 끝날 때마다 결과만 기록한다.
-- 그날 인증한 사람은 자기 몫을 돌려받을 예정, 못 한 사람의 몫은 그날 성공한 사람들이 나눠 받을 예정.
-- 실제 지급은 챌린지가 끝날 때 이 결과를 모두 더해 한 번에 한다 (settlements · settlement_items).
-- 챌린지·기간(period_start)당 한 번만 기록한다 (유니크 → 스케줄러가 두 번 돌아도 중복 없음).
-- 이미 만든 DB 에 적용한다. (새로 만드는 DB 는 01-schema.sql 에 이미 반영됨)
--   mysql -u godlife_user -p godlife < db/migrations/008-daily-settlement.sql

CREATE TABLE daily_settlements (
  id             BIGINT   NOT NULL AUTO_INCREMENT,
  challenge_id   BIGINT   NOT NULL,
  period_start   DATE     NOT NULL COMMENT '매일 챌린지는 그날, 주 N회는 그 주 첫날 (시작일부터 7일씩)',
  period_end     DATE     NOT NULL,
  success_count  INT      NOT NULL COMMENT '그 기간 목표를 채운 사람 수',
  fail_count     INT      NOT NULL COMMENT '못 채운 사람 수 (포기한 사람 포함)',
  forfeited_pool BIGINT   NOT NULL COMMENT '못 채운 사람들이 잃은 포인트 합',
  reward_share   BIGINT   NOT NULL COMMENT '성공한 사람 한 명이 받을 보상 (상한 적용 후, 끝날 때 지급)',
  distributed    BIGINT   NOT NULL COMMENT '나눠 줄 보상 합 (나머지·상한 초과분은 나누지 않음)',
  settled_at     DATETIME NOT NULL,
  PRIMARY KEY (id),
  UNIQUE KEY uk_daily_settlement (challenge_id, period_start),
  CONSTRAINT fk_daily_settlement_challenge FOREIGN KEY (challenge_id) REFERENCES challenges (id),
  CONSTRAINT ck_daily_settlement CHECK (success_count >= 0 AND fail_count >= 0 AND forfeited_pool >= 0
    AND reward_share >= 0 AND distributed >= 0 AND distributed <= forfeited_pool)
) ENGINE=InnoDB COMMENT='매일(주) 결과 (지급은 챌린지 종료 시 settlements 로 한 번에)';
