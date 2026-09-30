-- 충전 취소(환불): 충전 포인트 중 쓰지 않은 금액만 결제 취소로 환불한다 (CLAUDE.md 규칙 2).
-- 보상 포인트는 환불·현금화하지 않는다. 결제 연동 전에는 원장 기록만 하고, 연동 후에는 PG 결제 취소 API 를 부른다.
-- 이미 만든 DB 에 적용한다. (새로 만드는 DB 는 01-schema.sql 에 이미 반영됨)
--   mysql -u godlife_user -p godlife < db/migrations/007-charge-cancel.sql

ALTER TABLE point_transactions
  MODIFY type ENUM('CHARGE','CHARGE_CANCEL','ENTRY_FEE','REFUND','REWARD','PURCHASE','SEASON_BONUS','ADJUST') NOT NULL
    COMMENT 'CHARGE_CANCEL = 충전 포인트 환불(결제 취소). REFUND = 챌린지 참가비 환급';
