-- 시연용: 관리자 계정 지갑에 보상 포인트 1,000,000P 를 넣는다 (포인트 상점 · 시연 확인용). 운영 DB 에는 넣지 않는다.
--   mysql -u godlife_user -p godlife < db/dev-admin-points.sql
-- 원장 규칙을 그대로 따른다: point_transactions 에 ADJUST / REWARD 한 줄을 남기고 같은 트랜잭션에서 지갑 잔액을 맞춘다.
-- 보상 포인트라 환불 · 현금화 대상이 아니다 (충전 포인트는 결제 기록 없이 넣지 않는다 → 필요하면 테스트 결제로 충전).
-- 멱등성 키가 관리자마다 하나라서 여러 번 돌려도 한 번만 들어간다.

SET NAMES utf8mb4;
START TRANSACTION;

-- 지갑이 없는 관리자는 지갑부터 만든다
INSERT INTO wallets (user_id)
SELECT u.id FROM users u
WHERE u.role = 'ADMIN' AND u.status = 'ACTIVE'
  AND NOT EXISTS (SELECT 1 FROM wallets w WHERE w.user_id = u.id);

-- 관리자 지갑을 잠근다 (동시에 다른 거래가 잔액을 바꾸지 못하게)
SELECT w.id FROM wallets w JOIN users u ON u.id = w.user_id WHERE u.role = 'ADMIN' AND u.status = 'ACTIVE' FOR UPDATE;

-- 아직 받지 않은 관리자 지갑
CREATE TEMPORARY TABLE grant_targets AS
SELECT w.id AS wallet_id, w.reward_balance + 1000000 AS reward_after, CONCAT('dev-admin-grant-', u.id) AS idem
FROM wallets w JOIN users u ON u.id = w.user_id
WHERE u.role = 'ADMIN' AND u.status = 'ACTIVE'
  AND NOT EXISTS (SELECT 1 FROM point_transactions t WHERE t.idempotency_key = CONCAT('dev-admin-grant-', u.id));

INSERT INTO point_transactions (wallet_id, type, source, amount, balance_after, ref_type, ref_id, idempotency_key)
SELECT wallet_id, 'ADJUST', 'REWARD', 1000000, reward_after, NULL, NULL, idem FROM grant_targets;

UPDATE wallets w JOIN grant_targets g ON g.wallet_id = w.id
SET w.reward_balance = w.reward_balance + 1000000,
    w.balance = w.balance + 1000000;

SELECT COUNT(*) AS granted_wallets FROM grant_targets;
DROP TEMPORARY TABLE grant_targets;
COMMIT;
