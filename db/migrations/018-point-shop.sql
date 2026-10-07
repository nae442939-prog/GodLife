-- 포인트 상점: 실물 상품(배송)과 쿠폰 상품(이용권 · 상품권, 배송 없이 쿠폰 번호 발급), 찜, 장바구니, 주문, 주문 취소.
-- 결제는 보상 포인트를 먼저 쓰고 모자란 만큼 충전 포인트로 채운다. 한 주문에 원장이 두 줄(보상 · 충전)일 수 있어
-- orders.transaction_id 를 없애고, 출처별로 쓴 금액(reward_points · charged_points)을 주문에 남긴다
-- (주문을 취소하면 이 값대로 원래 출처에 돌려준다). 원장은 ref_type = 'order' 로 주문을 가리킨다.
-- 이미 만든 DB 에 적용한다. (새로 만드는 DB 는 01-schema.sql · 02-seed.sql 에 이미 반영됨)
--   mysql -u godlife_user -p godlife < db/migrations/018-point-shop.sql

SET NAMES utf8mb4;

ALTER TABLE point_transactions
  MODIFY type ENUM('CHARGE','CHARGE_CANCEL','ENTRY_FEE','REFUND','REWARD','PURCHASE','PURCHASE_CANCEL','SEASON_BONUS','ADJUST') NOT NULL
    COMMENT 'CHARGE_CANCEL = 충전 포인트 환불(결제 취소). REFUND = 챌린지 참가비 환급. PURCHASE_CANCEL = 상점 주문 취소로 돌려받음';

ALTER TABLE products
  ADD COLUMN type ENUM('PHYSICAL','COUPON') NOT NULL DEFAULT 'PHYSICAL'
    COMMENT 'PHYSICAL = 배송받는 실물, COUPON = 이용권 · 상품권 (배송 없이 쿠폰 번호 발급)' AFTER category_id,
  MODIFY image_url VARCHAR(500) NOT NULL DEFAULT '' COMMENT '상품 사진 주소. 비어 있으면 화면이 기본 그림을 보여 준다',
  ADD COLUMN sold_count INT NOT NULL DEFAULT 0 COMMENT '판매 수량 (인기순 정렬용)' AFTER stock,
  ADD COLUMN created_at DATETIME NOT NULL DEFAULT CURRENT_TIMESTAMP;

ALTER TABLE addresses
  MODIFY phone_enc VARCHAR(100) NOT NULL COMMENT '받는 사람 연락처의 암호화 값 (AES-GCM, 본인에게만 다시 보여 준다)';

ALTER TABLE orders
  DROP FOREIGN KEY fk_orders_tx,
  DROP FOREIGN KEY fk_orders_address,
  DROP KEY uk_orders_tx,
  DROP COLUMN transaction_id;

ALTER TABLE orders
  MODIFY address_id BIGINT NULL COMMENT '고른 배송지 (지우면 NULL, 쿠폰만 산 주문은 처음부터 NULL). 실제 배송 정보는 ship_* 에 주문 시점 값으로 남긴다',
  ADD COLUMN reward_points  BIGINT NOT NULL DEFAULT 0 COMMENT '보상 포인트로 낸 금액' AFTER total_points,
  ADD COLUMN charged_points BIGINT NOT NULL DEFAULT 0 COMMENT '충전 포인트로 낸 금액' AFTER reward_points,
  ADD COLUMN ship_recipient VARCHAR(50)  NULL COMMENT '주문 시점의 받는 사람',
  ADD COLUMN ship_phone_enc VARCHAR(100) NULL COMMENT '주문 시점의 연락처 (암호화)',
  ADD COLUMN ship_zipcode   CHAR(5)      NULL,
  ADD COLUMN ship_address1  VARCHAR(200) NULL,
  ADD COLUMN ship_address2  VARCHAR(200) NULL,
  ADD COLUMN canceled_at    DATETIME     NULL,
  ADD CONSTRAINT fk_orders_address FOREIGN KEY (address_id) REFERENCES addresses (id) ON DELETE SET NULL,
  ADD CONSTRAINT ck_orders_sources CHECK (reward_points >= 0 AND charged_points >= 0 AND total_points = reward_points + charged_points);

CREATE TABLE order_coupons (
  id            BIGINT      NOT NULL AUTO_INCREMENT,
  order_item_id BIGINT      NOT NULL,
  code          VARCHAR(30) NOT NULL COMMENT '발급한 쿠폰 번호 (포트폴리오용 가상 번호)',
  created_at    DATETIME    NOT NULL DEFAULT CURRENT_TIMESTAMP,
  PRIMARY KEY (id),
  UNIQUE KEY uk_order_coupons_code (code),
  KEY idx_order_coupons_item (order_item_id),
  CONSTRAINT fk_order_coupons_item FOREIGN KEY (order_item_id) REFERENCES order_items (id)
) ENGINE=InnoDB COMMENT='쿠폰 상품(이용권 · 상품권)을 사면 수량만큼 발급하는 쿠폰 번호';

CREATE TABLE cart_items (
  user_id    BIGINT   NOT NULL,
  product_id BIGINT   NOT NULL,
  quantity   INT      NOT NULL,
  created_at DATETIME NOT NULL DEFAULT CURRENT_TIMESTAMP,
  PRIMARY KEY (user_id, product_id),
  KEY idx_cart_items_product (product_id),
  CONSTRAINT fk_cart_items_user FOREIGN KEY (user_id) REFERENCES users (id) ON DELETE CASCADE,
  CONSTRAINT fk_cart_items_product FOREIGN KEY (product_id) REFERENCES products (id) ON DELETE CASCADE,
  CONSTRAINT ck_cart_items_quantity CHECK (quantity BETWEEN 1 AND 99)
) ENGINE=InnoDB COMMENT='장바구니';

INSERT IGNORE INTO product_categories (name) VALUES ('운동용품'), ('문구류'), ('생활용품'), ('이용권'), ('상품권');

-- 주문 알림 (배송 시작 · 관리자 취소)
ALTER TABLE notifications
  MODIFY type ENUM('SETTLEMENT','VERIFY_REMINDER','COMMENT','REPORT_RESULT','REPORT_ALERT',
                   'FOLLOW','MESSAGE_REQUEST','INQUIRY_ANSWER','VERIFY_REJECTED','ORDER') NOT NULL;
