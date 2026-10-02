-- 결제 연동 (토스페이먼츠, 테스트 모드 전용 — CLAUDE.md 규칙 3).
-- 충전은 PG 결제 승인을 거쳐 들어오고, 충전 포인트 환불은 결제 건별로 PG 결제 취소(부분 취소)를 불러 돌려준다.
-- - payment_key: 승인 전(READY)에는 아직 없어서 NULL 을 허용한다.
-- - canceled_amount: 그 결제에서 이미 취소(환불)된 금액 합. amount - canceled_amount 가 더 환불할 수 있는 금액이다.
-- - 원장은 ref_type = 'payment' 로 결제를 가리킨다.
-- 이미 만든 DB 에 적용한다. (새로 만드는 DB 는 01-schema.sql 에 이미 반영됨)
--   mysql -u godlife_user -p godlife < db/migrations/019-payment.sql

SET NAMES utf8mb4;

ALTER TABLE payments
  MODIFY payment_key VARCHAR(200) NULL COMMENT 'PG 결제키/토큰만 저장. 카드정보 컬럼 없음. 승인 전에는 NULL',
  ADD COLUMN canceled_amount BIGINT NOT NULL DEFAULT 0 COMMENT '결제 취소(충전 포인트 환불)된 금액 합' AFTER points_granted,
  DROP CHECK ck_payments_amount;

ALTER TABLE payments
  ADD CONSTRAINT ck_payments_amount CHECK (amount > 0 AND points_granted >= 0
    AND canceled_amount >= 0 AND canceled_amount <= amount);
