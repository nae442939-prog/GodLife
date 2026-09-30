-- 포인트 출처 분리 (CLAUDE.md 규칙 2): 충전(CHARGED) / 보상(REWARD) / 상점(SHOP)
-- 충전 포인트만 결제 취소로 환불할 수 있고, 보상 포인트는 상점에서만 쓴다. 한 잔액으로 합치지 않는다.
-- 이미 만든 DB 에 적용한다. (새로 만드는 DB 는 01-schema.sql 에 이미 반영됨)
--   mysql -u godlife_user -p godlife < db/migrations/006-point-source.sql

ALTER TABLE wallets
  MODIFY balance BIGINT NOT NULL DEFAULT 0 COMMENT '전체 잔액 캐시 (= charged + reward). 진실의 원천은 point_transactions. SELECT ... FOR UPDATE 락 앵커',
  ADD COLUMN charged_balance BIGINT NOT NULL DEFAULT 0 COMMENT '직접 충전한 포인트. 쓰지 않은 만큼만 결제 취소 환불 가능' AFTER balance,
  ADD COLUMN reward_balance BIGINT NOT NULL DEFAULT 0 COMMENT '챌린지 보상·이벤트 포인트. 상점에서만 사용, 환불·현금화 불가' AFTER charged_balance,
  ADD CONSTRAINT ck_wallets_sources CHECK (charged_balance >= 0 AND reward_balance >= 0 AND balance = charged_balance + reward_balance);

ALTER TABLE point_transactions
  ADD COLUMN source ENUM('CHARGED','REWARD','SHOP') NOT NULL COMMENT '어느 출처의 포인트가 움직였는지. 환불 로직은 CHARGED 만 본다' AFTER type,
  MODIFY balance_after BIGINT NOT NULL COMMENT '거래 후 그 출처(source)의 잔액',
  ADD KEY idx_ptx_wallet_type_created (wallet_id, type, created_at);
