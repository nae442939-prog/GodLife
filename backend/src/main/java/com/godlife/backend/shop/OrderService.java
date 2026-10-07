package com.godlife.backend.shop;

import com.godlife.backend.common.error.BusinessException;
import com.godlife.backend.common.error.ErrorCode;
import com.godlife.backend.notification.Notification;
import com.godlife.backend.notification.NotificationService;
import com.godlife.backend.phone.PhoneCipher;
import com.godlife.backend.shop.dto.ShopDtos.AdminOrder;
import com.godlife.backend.shop.dto.ShopDtos.OrderDetail;
import com.godlife.backend.shop.dto.ShopDtos.OrderItem;
import com.godlife.backend.shop.dto.ShopDtos.OrderLine;
import com.godlife.backend.shop.dto.ShopDtos.OrderRequest;
import com.godlife.backend.shop.dto.ShopDtos.OrderStatus;
import com.godlife.backend.shop.dto.ShopDtos.OrderSummary;
import com.godlife.backend.shop.dto.ShopDtos.ProductType;
import com.godlife.backend.shop.dto.ShopDtos.Shipping;
import com.godlife.backend.user.User;
import com.godlife.backend.user.UserService;
import com.godlife.backend.wallet.WalletService;
import com.godlife.backend.wallet.WalletService.ShopPayment;
import lombok.RequiredArgsConstructor;
import org.springframework.dao.DuplicateKeyException;
import org.springframework.jdbc.core.namedparam.MapSqlParameterSource;
import org.springframework.jdbc.core.namedparam.NamedParameterJdbcTemplate;
import org.springframework.jdbc.support.GeneratedKeyHolder;
import org.springframework.jdbc.support.KeyHolder;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.security.SecureRandom;
import java.sql.Timestamp;
import java.time.Clock;
import java.time.LocalDateTime;
import java.util.ArrayList;
import java.util.HashMap;
import java.util.List;
import java.util.Map;
import java.util.TreeMap;

/**
 * 포인트 상점 주문. 포인트와 재고가 함께 움직이므로 한 트랜잭션에서 잠그고 처리한다 (프로젝트 규칙 4).
 * - 주문: 상품 행을 id 순서로 잠그고(SELECT ... FOR UPDATE) 재고를 확인 · 차감한 뒤 지갑에서 포인트를 뺀다.
 *   같은 requestKey 로 다시 오면(더블클릭 · 재전송) 새로 주문하지 않고 먼저 만든 주문을 돌려준다.
 * - 결제: 보상 포인트 먼저, 모자란 만큼 충전 포인트. 출처별로 쓴 금액을 주문에 남긴다.
 * - 취소: '준비 중'이고 쿠폰이 발급되지 않은 주문만. 재고를 되돌리고 포인트를 원래 출처 그대로 돌려준다.
 * - 쿠폰 상품(이용권 · 상품권)은 배송 없이 쿠폰 번호를 바로 발급한다. 쿠폰만 산 주문은 곧바로 '수령 완료'다.
 *   (쿠폰 번호는 포트폴리오용 가상 번호다. 실제 제휴사 쿠폰을 발급하지 않는다)
 * - 잠그는 순서는 언제나 주문 → 상품(id 순) → 지갑이라 주문과 취소가 엇갈려도 교착이 생기지 않는다.
 */
@Service
@RequiredArgsConstructor
public class OrderService {

    public static final int PAGE_SIZE = 20;
    /** 쿠폰 번호 글자: 헷갈리는 글자(0/O/1/I/L)를 뺀 영숫자 */
    private static final String COUPON_CHARS = "23456789ABCDEFGHJKMNPQRSTUVWXYZ";
    private static final SecureRandom RANDOM = new SecureRandom();

    private final NamedParameterJdbcTemplate jdbc;
    private final UserService userService;
    private final WalletService walletService;
    private final NotificationService notificationService;
    private final PhoneCipher phoneCipher;
    private final Clock clock;

    private record LockedProduct(Long id, String name, ProductType type, long price, int stock, boolean onSale) {
    }

    // ---------- 주문 ----------

    /** 주문하고 결제한다. 만든(또는 같은 요청으로 이미 만든) 주문 id 를 돌려준다. */
    @Transactional
    public Long place(Long userId, OrderRequest req) {
        User user = userService.getActive(userId);
        if (!user.hasPhone()) {
            throw new BusinessException(ErrorCode.PHONE_NOT_REGISTERED);
        }
        String requestKey = userId + ":" + req.requestKey();
        List<Long> done = jdbc.queryForList("SELECT id FROM orders WHERE request_key = :key",
                new MapSqlParameterSource("key", requestKey), Long.class);
        if (!done.isEmpty()) {
            return done.get(0);
        }

        // 같은 상품이 여러 줄로 오면 합치고, 상품 id 순서로 잠근다
        Map<Long, Integer> wanted = new TreeMap<>();
        for (OrderLine line : req.items()) {
            wanted.merge(line.productId(), line.quantity(), Integer::sum);
        }
        Map<Long, LockedProduct> products = lockProducts(wanted.keySet());

        long total = 0;
        boolean needsShipping = false;
        for (Map.Entry<Long, Integer> e : wanted.entrySet()) {
            LockedProduct p = products.get(e.getKey());
            if (p == null) {
                throw new BusinessException(ErrorCode.PRODUCT_NOT_FOUND);
            }
            if (!p.onSale()) {
                throw new BusinessException(ErrorCode.PRODUCT_NOT_ON_SALE, "'" + p.name() + "'은(는) 지금 살 수 없어요.");
            }
            if (p.stock() < e.getValue()) {
                throw new BusinessException(ErrorCode.OUT_OF_STOCK, p.stock() <= 0
                        ? "'" + p.name() + "'은(는) 품절됐어요."
                        : "'%s' 재고가 %d개 남았어요.".formatted(p.name(), p.stock()));
            }
            total += p.price() * e.getValue();
            needsShipping |= p.type() == ProductType.PHYSICAL;
        }

        MapSqlParameterSource order = new MapSqlParameterSource("user", userId).addValue("key", requestKey)
                .addValue("total", total)
                .addValue("status", (needsShipping ? OrderStatus.PREPARING : OrderStatus.DELIVERED).name())
                .addValue("address", null).addValue("recipient", null).addValue("phone", null)
                .addValue("zipcode", null).addValue("address1", null).addValue("address2", null);
        if (needsShipping) {
            if (req.addressId() == null) {
                throw new BusinessException(ErrorCode.ADDRESS_REQUIRED);
            }
            // 배송 정보는 주문 시점 값으로 주문에 남긴다 (나중에 배송지를 고치거나 지워도 주문은 그대로)
            Map<String, Object> address = jdbc.queryForList("""
                    SELECT id, recipient, phone_enc, zipcode, address1, address2 FROM addresses
                    WHERE id = :id AND user_id = :user
                    """, new MapSqlParameterSource("id", req.addressId()).addValue("user", userId)).stream()
                    .findFirst().orElseThrow(() -> new BusinessException(ErrorCode.ADDRESS_NOT_FOUND));
            order.addValue("address", address.get("id")).addValue("recipient", address.get("recipient"))
                    .addValue("phone", asText(address.get("phone_enc"))).addValue("zipcode", address.get("zipcode"))
                    .addValue("address1", address.get("address1")).addValue("address2", address.get("address2"));
        }

        KeyHolder key = new GeneratedKeyHolder();
        try {
            // 어느 출처에서 얼마를 낼지는 지갑을 잠근 뒤에 정해지므로, 우선 전부 보상으로 적어 두고 결제 뒤에 고친다
            jdbc.update("""
                    INSERT INTO orders (user_id, address_id, request_key, total_points, reward_points, charged_points,
                                        status, ship_recipient, ship_phone_enc, ship_zipcode, ship_address1,
                                        ship_address2)
                    VALUES (:user, :address, :key, :total, :total, 0, :status, :recipient, :phone, :zipcode,
                            :address1, :address2)
                    """, order, key);
        } catch (DuplicateKeyException e) {
            // 같은 요청이 동시에 두 번 왔다: 먼저 온 쪽이 주문을 만든다
            throw new BusinessException(ErrorCode.DUPLICATE_REQUEST);
        }
        Long orderId = key.getKey().longValue();

        for (Map.Entry<Long, Integer> e : wanted.entrySet()) {
            LockedProduct p = products.get(e.getKey());
            MapSqlParameterSource item = new MapSqlParameterSource("order", orderId).addValue("product", p.id())
                    .addValue("quantity", e.getValue()).addValue("price", p.price());
            KeyHolder itemKey = new GeneratedKeyHolder();
            jdbc.update("""
                    INSERT INTO order_items (order_id, product_id, quantity, unit_points)
                    VALUES (:order, :product, :quantity, :price)
                    """, item, itemKey);
            jdbc.update("""
                    UPDATE products SET stock = stock - :quantity, sold_count = sold_count + :quantity
                    WHERE id = :product
                    """, item);
            if (p.type() == ProductType.COUPON) {
                issueCoupons(itemKey.getKey().longValue(), e.getValue());
            }
        }

        ShopPayment paid = walletService.payShop(userId, total, orderId);
        jdbc.update("UPDATE orders SET reward_points = :reward, charged_points = :charged WHERE id = :id",
                new MapSqlParameterSource("id", orderId).addValue("reward", paid.reward())
                        .addValue("charged", paid.charged()));

        if (req.fromCart()) {
            jdbc.update("DELETE FROM cart_items WHERE user_id = :user AND product_id IN (:ids)",
                    new MapSqlParameterSource("user", userId).addValue("ids", wanted.keySet()));
        }
        return orderId;
    }

    /** 내 주문 취소: '준비 중'이고 쿠폰이 발급되지 않은 주문만 */
    @Transactional
    public void cancel(Long userId, Long orderId) {
        cancelLocked(lockOrder(orderId, userId));
    }

    /** 배송 중인 주문을 받았다고 확인한다 (수령 완료) */
    @Transactional
    public void receive(Long userId, Long orderId) {
        Map<String, Object> order = lockOrder(orderId, userId);
        move(orderId, order, OrderStatus.SHIPPING, OrderStatus.DELIVERED, null);
    }

    // ---------- 조회 ----------

    @Transactional(readOnly = true)
    public List<OrderSummary> orders(Long userId, int page) {
        return jdbc.query("""
                SELECT o.id, o.status, o.total_points, o.ordered_at, p.name, p.image_url, p.type,
                       (SELECT COUNT(*) FROM order_items x WHERE x.order_id = o.id) AS items
                FROM orders o
                  JOIN order_items i ON i.id = (SELECT MIN(f.id) FROM order_items f WHERE f.order_id = o.id)
                  JOIN products p ON p.id = i.product_id
                WHERE o.user_id = :user
                ORDER BY o.id DESC LIMIT :size OFFSET :offset
                """, new MapSqlParameterSource("user", userId).addValue("size", PAGE_SIZE)
                        .addValue("offset", (long) Math.max(page, 0) * PAGE_SIZE),
                (rs, i) -> new OrderSummary(rs.getLong("id"), OrderStatus.valueOf(rs.getString("status")),
                        rs.getLong("total_points"), rs.getString("name"), rs.getString("image_url"),
                        ProductType.valueOf(rs.getString("type")), rs.getInt("items"),
                        rs.getTimestamp("ordered_at").toLocalDateTime()));
    }

    @Transactional(readOnly = true)
    public OrderDetail order(Long userId, Long orderId) {
        Map<String, Object> row = jdbc.queryForList("SELECT * FROM orders WHERE id = :id AND user_id = :user",
                        new MapSqlParameterSource("id", orderId).addValue("user", userId)).stream().findFirst()
                .orElseThrow(() -> new BusinessException(ErrorCode.ORDER_NOT_FOUND));
        return detail(row);
    }

    // ---------- 관리자 ----------

    /** 관리자: 주문 목록 (status 가 없으면 전체, 오래된 주문부터 — 먼저 들어온 주문을 먼저 보낸다) */
    @Transactional(readOnly = true)
    public List<AdminOrder> all(OrderStatus status) {
        return jdbc.queryForList("""
                SELECT o.*, u.nickname FROM orders o JOIN users u ON u.id = o.user_id
                WHERE (:status IS NULL OR o.status = :status)
                ORDER BY o.id LIMIT 200
                """, new MapSqlParameterSource("status", status == null ? null : status.name())).stream()
                .map(row -> new AdminOrder(((Number) row.get("user_id")).longValue(), (String) row.get("nickname"),
                        detail(row)))
                .toList();
    }

    /** 관리자: 발송 처리 (준비 중 → 배송 중). 주문한 회원에게 알린다. */
    @Transactional
    public void ship(Long orderId, String trackingNo) {
        Map<String, Object> order = lockOrder(orderId, null);
        move(orderId, order, OrderStatus.PREPARING, OrderStatus.SHIPPING, trackingNo.strip());
        notificationService.notify(((Number) order.get("user_id")).longValue(), Notification.Type.ORDER,
                "주문한 상품을 보냈어요", "운송장 번호 " + trackingNo.strip() + " · 받으면 주문 내역에서 수령 확인을 눌러 주세요.",
                "/shop/orders/" + orderId, "order-ship:" + orderId);
    }

    /** 관리자: 배송 완료 처리 (배송 중 → 수령 완료) */
    @Transactional
    public void deliver(Long orderId) {
        move(orderId, lockOrder(orderId, null), OrderStatus.SHIPPING, OrderStatus.DELIVERED, null);
    }

    /** 관리자: 준비 중인 주문 취소 (보낼 수 없을 때). 포인트와 재고를 되돌리고 회원에게 알린다. */
    @Transactional
    public void cancelByAdmin(Long orderId) {
        Map<String, Object> order = lockOrder(orderId, null);
        cancelLocked(order);
        notificationService.notify(((Number) order.get("user_id")).longValue(), Notification.Type.ORDER,
                "주문이 취소됐어요", "보낼 수 없는 사정이 생겨 주문을 취소했어요. 쓴 포인트는 그대로 돌려 드렸어요.",
                "/shop/orders/" + orderId, "order-cancel:" + orderId);
    }

    // ---------- 공통 ----------

    /** 주문 줄을 잠근다. userId 가 있으면 그 회원의 주문만 (남의 주문은 없는 것처럼 404). */
    private Map<String, Object> lockOrder(Long orderId, Long userId) {
        return jdbc.queryForList("""
                SELECT id, user_id, status, reward_points, charged_points FROM orders
                WHERE id = :id AND (:user IS NULL OR user_id = :user) FOR UPDATE
                """, new MapSqlParameterSource("id", orderId).addValue("user", userId)).stream().findFirst()
                .orElseThrow(() -> new BusinessException(ErrorCode.ORDER_NOT_FOUND));
    }

    /** 잠근 주문의 상태를 from → to 로 옮긴다. 이미 다른 상태면 거절한다. */
    private void move(Long orderId, Map<String, Object> order, OrderStatus from, OrderStatus to, String trackingNo) {
        if (!from.name().equals(order.get("status"))) {
            throw new BusinessException(ErrorCode.ORDER_STATUS_CONFLICT);
        }
        jdbc.update("UPDATE orders SET status = :to, tracking_no = COALESCE(:tracking, tracking_no) WHERE id = :id",
                new MapSqlParameterSource("id", orderId).addValue("to", to.name()).addValue("tracking", trackingNo));
    }

    private void cancelLocked(Map<String, Object> order) {
        Long orderId = ((Number) order.get("id")).longValue();
        MapSqlParameterSource params = new MapSqlParameterSource("id", orderId);
        if (!OrderStatus.PREPARING.name().equals(order.get("status"))) {
            throw new BusinessException(ErrorCode.ORDER_CANNOT_CANCEL, "준비 중인 주문만 취소할 수 있어요.");
        }
        if (hasCoupons(orderId)) {
            throw new BusinessException(ErrorCode.ORDER_CANNOT_CANCEL, "쿠폰이 발급된 주문은 취소할 수 없어요.");
        }
        jdbc.update("UPDATE orders SET status = 'CANCELED', canceled_at = :now WHERE id = :id",
                params.addValue("now", LocalDateTime.now(clock)));

        // 재고를 되돌린다 (상품 id 순서로 잠근다)
        Map<Long, Integer> quantities = new TreeMap<>();
        jdbc.query("SELECT product_id, quantity FROM order_items WHERE order_id = :id", params,
                rs -> {
                    quantities.merge(rs.getLong("product_id"), rs.getInt("quantity"), Integer::sum);
                });
        lockProducts(quantities.keySet());
        quantities.forEach((productId, quantity) -> jdbc.update("""
                UPDATE products SET stock = stock + :quantity, sold_count = GREATEST(sold_count - :quantity, 0)
                WHERE id = :product
                """, new MapSqlParameterSource("product", productId).addValue("quantity", quantity)));

        walletService.refundShop(((Number) order.get("user_id")).longValue(),
                new ShopPayment(((Number) order.get("reward_points")).longValue(),
                        ((Number) order.get("charged_points")).longValue()), orderId);
    }

    private Map<Long, LockedProduct> lockProducts(Iterable<Long> ids) {
        List<Long> list = new ArrayList<>();
        ids.forEach(list::add);
        Map<Long, LockedProduct> products = new HashMap<>();
        if (list.isEmpty()) {
            return products;
        }
        jdbc.query("""
                SELECT p.id, p.name, p.type, p.price_points, p.stock,
                       (p.status = 'ON_SALE' AND s.status = 'ACTIVE') AS on_sale
                FROM products p JOIN sponsors s ON s.id = p.sponsor_id
                WHERE p.id IN (:ids) ORDER BY p.id FOR UPDATE
                """, new MapSqlParameterSource("ids", list),
                rs -> {
                    products.put(rs.getLong("id"), new LockedProduct(rs.getLong("id"), rs.getString("name"),
                            ProductType.valueOf(rs.getString("type")), rs.getLong("price_points"),
                            rs.getInt("stock"), rs.getBoolean("on_sale")));
                });
        return products;
    }

    /** 쿠폰 번호를 수량만큼 발급한다 (GL-XXXX-XXXX-XXXX) */
    private void issueCoupons(Long orderItemId, int quantity) {
        for (int i = 0; i < quantity; i++) {
            StringBuilder code = new StringBuilder("GL");
            for (int group = 0; group < 3; group++) {
                code.append('-');
                for (int n = 0; n < 4; n++) {
                    code.append(COUPON_CHARS.charAt(RANDOM.nextInt(COUPON_CHARS.length())));
                }
            }
            jdbc.update("INSERT INTO order_coupons (order_item_id, code) VALUES (:item, :code)",
                    new MapSqlParameterSource("item", orderItemId).addValue("code", code.toString()));
        }
    }

    private boolean hasCoupons(Long orderId) {
        Long n = jdbc.queryForObject("""
                SELECT COUNT(*) FROM order_coupons c JOIN order_items i ON i.id = c.order_item_id
                WHERE i.order_id = :id
                """, new MapSqlParameterSource("id", orderId), Long.class);
        return n != null && n > 0;
    }

    /** orders 한 줄(SELECT *)로 주문 상세를 만든다 */
    private OrderDetail detail(Map<String, Object> row) {
        Long orderId = ((Number) row.get("id")).longValue();
        MapSqlParameterSource params = new MapSqlParameterSource("id", orderId);
        Map<Long, List<String>> coupons = new HashMap<>();
        jdbc.query("""
                SELECT c.order_item_id, c.code FROM order_coupons c JOIN order_items i ON i.id = c.order_item_id
                WHERE i.order_id = :id ORDER BY c.id
                """, params, rs -> {
            coupons.computeIfAbsent(rs.getLong("order_item_id"), k -> new ArrayList<>()).add(rs.getString("code"));
        });
        List<OrderItem> items = jdbc.query("""
                SELECT i.id, i.product_id, i.quantity, i.unit_points, p.name, p.image_url, p.type
                FROM order_items i JOIN products p ON p.id = i.product_id
                WHERE i.order_id = :id ORDER BY i.id
                """, params,
                (rs, n) -> new OrderItem(rs.getLong("product_id"), rs.getString("name"), rs.getString("image_url"),
                        ProductType.valueOf(rs.getString("type")), rs.getInt("quantity"), rs.getLong("unit_points"),
                        coupons.getOrDefault(rs.getLong("id"), List.of())));
        OrderStatus status = OrderStatus.valueOf((String) row.get("status"));
        Shipping shipping = row.get("ship_recipient") == null ? null
                : new Shipping((String) row.get("ship_recipient"),
                        phoneCipher.decryptFormatted(asText(row.get("ship_phone_enc"))),
                        (String) row.get("ship_zipcode"), (String) row.get("ship_address1"),
                        (String) row.get("ship_address2"));
        return new OrderDetail(orderId, status, ((Number) row.get("total_points")).longValue(),
                ((Number) row.get("reward_points")).longValue(), ((Number) row.get("charged_points")).longValue(),
                (String) row.get("tracking_no"), time(row.get("ordered_at")), time(row.get("canceled_at")),
                status == OrderStatus.PREPARING && coupons.isEmpty(), shipping, items);
    }

    private static LocalDateTime time(Object value) {
        if (value == null) {
            return null;
        }
        return value instanceof Timestamp t ? t.toLocalDateTime() : (LocalDateTime) value;
    }

    private static String asText(Object value) {
        return value == null ? null : value.toString();
    }
}
