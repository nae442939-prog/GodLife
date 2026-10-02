package com.godlife.backend.shop;

import com.godlife.backend.challenge.dto.PageResponse;
import com.godlife.backend.common.error.BusinessException;
import com.godlife.backend.common.error.ErrorCode;
import com.godlife.backend.common.upload.ImageStore;
import com.godlife.backend.phone.PhoneCipher;
import com.godlife.backend.shop.dto.ShopDtos.Address;
import com.godlife.backend.shop.dto.ShopDtos.AddressRequest;
import com.godlife.backend.shop.dto.ShopDtos.CartItem;
import com.godlife.backend.shop.dto.ShopDtos.Category;
import com.godlife.backend.shop.dto.ShopDtos.Product;
import com.godlife.backend.shop.dto.ShopDtos.ProductType;
import lombok.RequiredArgsConstructor;
import org.springframework.jdbc.core.RowMapper;
import org.springframework.jdbc.core.namedparam.MapSqlParameterSource;
import org.springframework.jdbc.core.namedparam.NamedParameterJdbcTemplate;
import org.springframework.jdbc.support.GeneratedKeyHolder;
import org.springframework.jdbc.support.KeyHolder;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.nio.file.Files;
import java.nio.file.Path;
import java.util.List;
import java.util.regex.Pattern;

/**
 * 포인트 상점: 상품 보기 · 찜 · 장바구니 · 배송지. (주문과 결제는 {@link OrderService})
 * - 상품은 누구나 볼 수 있다. 숨긴 상품과 정지된 스폰서의 상품은 보이지 않는다.
 * - 품절 = 재고가 없거나 관리자가 품절로 표시한 상품. 목록에는 나오지만 살 수 없다.
 * - 배송지의 연락처는 암호화해 저장하고 본인에게만 다시 보여 준다 (CLAUDE.md 규칙 5).
 */
@Service
@RequiredArgsConstructor
public class ShopService {

    public static final int PAGE_SIZE = 12;
    public static final int MAX_ADDRESSES = 10;
    public static final int MAX_CART_ITEMS = 30;
    /** 상품 사진 주소: /api/shop/images/{productId}/{uuid}.jpg ↔ 파일 키 product/{productId}/{uuid}.jpg */
    public static final String IMAGE_URL_PREFIX = "/api/shop/images/";
    static final String IMAGE_KEY_PREFIX = "product/";
    private static final Pattern IMAGE_NAME = Pattern.compile("[0-9a-f-]{36}\\.jpg");

    /** 정렬: popular(많이 팔린 순, 기본) / latest(새 상품 순) / price_asc(낮은 가격순) / price_desc(높은 가격순) */
    public enum SortOption {
        POPULAR("p.sold_count DESC, p.id DESC"),
        LATEST("p.id DESC"),
        PRICE_ASC("p.price_points, p.id DESC"),
        PRICE_DESC("p.price_points DESC, p.id DESC");

        private final String orderBy;

        SortOption(String orderBy) {
            this.orderBy = orderBy;
        }
    }

    private static final String PRODUCT_COLUMNS = """
            p.id, p.name, p.type, p.image_url, p.price_points, p.stock, p.status, p.description,
            c.id AS cid, c.name AS cname, s.name AS sname,
            EXISTS (SELECT 1 FROM wishlists w WHERE w.user_id = :viewer AND w.product_id = p.id) AS wished
            """;
    private static final String PRODUCT_FROM = """
            FROM products p
              JOIN product_categories c ON c.id = p.category_id
              JOIN sponsors s ON s.id = p.sponsor_id
            WHERE p.status <> 'HIDDEN' AND s.status = 'ACTIVE'
            """;

    private final NamedParameterJdbcTemplate jdbc;
    private final PhoneCipher phoneCipher;
    private final ImageStore imageStore;

    // ---------- 상품 ----------

    @Transactional(readOnly = true)
    public List<Category> categories() {
        return jdbc.query("SELECT id, name FROM product_categories ORDER BY id",
                (rs, i) -> new Category(rs.getInt("id"), rs.getString("name")));
    }

    /** 상품 목록. viewerId 는 비로그인이면 null. */
    @Transactional(readOnly = true)
    public PageResponse<Product> products(Long viewerId, Integer categoryId, String keyword, SortOption sort,
                                          int page) {
        int pageNo = Math.max(page, 0);
        MapSqlParameterSource params = new MapSqlParameterSource("viewer", viewerId)
                .addValue("category", categoryId).addValue("q", likePattern(keyword))
                .addValue("size", PAGE_SIZE).addValue("offset", (long) pageNo * PAGE_SIZE);
        String where = PRODUCT_FROM + """
                  AND (:category IS NULL OR p.category_id = :category)
                  AND (:q IS NULL OR p.name LIKE :q ESCAPE '!')
                """;
        Long total = jdbc.queryForObject("SELECT COUNT(*) " + where, params, Long.class);
        long count = total == null ? 0 : total;
        List<Product> items = jdbc.query("SELECT " + PRODUCT_COLUMNS + where + " ORDER BY " + sort.orderBy
                + " LIMIT :size OFFSET :offset", params, product(false));
        return new PageResponse<>(items, pageNo, (int) ((count + PAGE_SIZE - 1) / PAGE_SIZE), count);
    }

    @Transactional(readOnly = true)
    public Product product(Long viewerId, Long productId) {
        return jdbc.query("SELECT " + PRODUCT_COLUMNS + PRODUCT_FROM + " AND p.id = :id",
                        new MapSqlParameterSource("viewer", viewerId).addValue("id", productId), product(true))
                .stream().findFirst().orElseThrow(() -> new BusinessException(ErrorCode.PRODUCT_NOT_FOUND));
    }

    /** 올린 상품 사진 파일 (누구나). 주소 모양이 다르거나 파일이 없으면 404 */
    public Path imageFile(Long productId, String name) {
        if (!IMAGE_NAME.matcher(name).matches()) {
            throw new BusinessException(ErrorCode.PRODUCT_NOT_FOUND, "사진을 찾을 수 없어요.");
        }
        Path path = imageStore.resolve(IMAGE_KEY_PREFIX + productId + "/" + name);
        if (!Files.isRegularFile(path)) {
            throw new BusinessException(ErrorCode.PRODUCT_NOT_FOUND, "사진을 찾을 수 없어요.");
        }
        return path;
    }

    // ---------- 찜 ----------

    /** 찜하기 / 찜 풀기. 여러 번 눌러도 결과가 같다. */
    @Transactional
    public void wish(Long userId, Long productId, boolean on) {
        MapSqlParameterSource params = new MapSqlParameterSource("user", userId).addValue("id", productId);
        if (!on) {
            jdbc.update("DELETE FROM wishlists WHERE user_id = :user AND product_id = :id", params);
            return;
        }
        product(userId, productId); // 보이는 상품만 찜할 수 있다
        jdbc.update("INSERT IGNORE INTO wishlists (user_id, product_id) VALUES (:user, :id)", params);
    }

    /** 내가 찜한 상품 (최근에 찜한 것부터) */
    @Transactional(readOnly = true)
    public List<Product> wishlist(Long userId) {
        return jdbc.query("SELECT " + PRODUCT_COLUMNS + """
                FROM wishlists mine
                  JOIN products p ON p.id = mine.product_id
                  JOIN product_categories c ON c.id = p.category_id
                  JOIN sponsors s ON s.id = p.sponsor_id
                WHERE mine.user_id = :viewer AND p.status <> 'HIDDEN' AND s.status = 'ACTIVE'
                ORDER BY mine.created_at DESC, p.id DESC
                """, new MapSqlParameterSource("viewer", userId), product(false));
    }

    // ---------- 장바구니 ----------

    @Transactional(readOnly = true)
    public List<CartItem> cart(Long userId) {
        return jdbc.query("""
                SELECT p.id, p.name, p.image_url, p.type, p.price_points, p.stock, ci.quantity,
                       (p.status = 'ON_SALE' AND s.status = 'ACTIVE' AND p.stock >= ci.quantity) AS available
                FROM cart_items ci
                  JOIN products p ON p.id = ci.product_id
                  JOIN sponsors s ON s.id = p.sponsor_id
                WHERE ci.user_id = :user AND p.status <> 'HIDDEN'
                ORDER BY ci.created_at, p.id
                """, new MapSqlParameterSource("user", userId),
                (rs, i) -> new CartItem(rs.getLong("id"), rs.getString("name"), rs.getString("image_url"),
                        ProductType.valueOf(rs.getString("type")), rs.getLong("price_points"), rs.getInt("quantity"),
                        rs.getInt("stock"), rs.getBoolean("available")));
    }

    /** 장바구니에 담기 / 수량 바꾸기 (그 상품의 수량을 quantity 로 맞춘다) */
    @Transactional
    public void putCart(Long userId, Long productId, int quantity) {
        Product product = product(userId, productId);
        if (product.soldOut()) {
            throw new BusinessException(ErrorCode.OUT_OF_STOCK, "품절된 상품이에요.");
        }
        if (quantity > product.stock()) {
            throw new BusinessException(ErrorCode.OUT_OF_STOCK, "재고가 %d개 남았어요.".formatted(product.stock()));
        }
        MapSqlParameterSource params = new MapSqlParameterSource("user", userId).addValue("id", productId)
                .addValue("quantity", quantity);
        Long kinds = jdbc.queryForObject(
                "SELECT COUNT(*) FROM cart_items WHERE user_id = :user AND product_id <> :id", params, Long.class);
        if (kinds != null && kinds >= MAX_CART_ITEMS) {
            throw new BusinessException(ErrorCode.VALIDATION_ERROR,
                    "장바구니에는 " + MAX_CART_ITEMS + "가지 상품까지 담을 수 있어요.");
        }
        jdbc.update("""
                INSERT INTO cart_items (user_id, product_id, quantity) VALUES (:user, :id, :quantity)
                ON DUPLICATE KEY UPDATE quantity = VALUES(quantity)
                """, params);
    }

    @Transactional
    public void removeCart(Long userId, Long productId) {
        jdbc.update("DELETE FROM cart_items WHERE user_id = :user AND product_id = :id",
                new MapSqlParameterSource("user", userId).addValue("id", productId));
    }

    // ---------- 배송지 ----------

    /** 내 배송지 (기본 배송지가 먼저) */
    @Transactional(readOnly = true)
    public List<Address> addresses(Long userId) {
        return jdbc.query("""
                SELECT id, recipient, phone_enc, zipcode, address1, address2, is_default
                FROM addresses WHERE user_id = :user ORDER BY is_default DESC, id DESC
                """, new MapSqlParameterSource("user", userId),
                (rs, i) -> new Address(rs.getLong("id"), rs.getString("recipient"),
                        phoneCipher.decryptFormatted(rs.getString("phone_enc")), rs.getString("zipcode"),
                        rs.getString("address1"), rs.getString("address2"), rs.getBoolean("is_default")));
    }

    /** 배송지 추가. 첫 배송지는 기본 배송지가 된다. */
    @Transactional
    public Long addAddress(Long userId, AddressRequest req) {
        MapSqlParameterSource params = addressParams(userId, req);
        // 회원 줄을 잠가 동시에 넣어도 개수 · 기본 배송지가 어긋나지 않게 한다
        jdbc.queryForList("SELECT id FROM users WHERE id = :user FOR UPDATE", params, Long.class);
        Long count = jdbc.queryForObject("SELECT COUNT(*) FROM addresses WHERE user_id = :user", params, Long.class);
        long existing = count == null ? 0 : count;
        if (existing >= MAX_ADDRESSES) {
            throw new BusinessException(ErrorCode.VALIDATION_ERROR,
                    "배송지는 " + MAX_ADDRESSES + "개까지 저장할 수 있어요.");
        }
        boolean makeDefault = req.isDefault() || existing == 0;
        if (makeDefault) {
            jdbc.update("UPDATE addresses SET is_default = FALSE WHERE user_id = :user", params);
        }
        KeyHolder key = new GeneratedKeyHolder();
        jdbc.update("""
                INSERT INTO addresses (user_id, recipient, phone_enc, zipcode, address1, address2, is_default)
                VALUES (:user, :recipient, :phone, :zipcode, :address1, :address2, :isDefault)
                """, params.addValue("isDefault", makeDefault), key);
        return key.getKey().longValue();
    }

    @Transactional
    public void updateAddress(Long userId, Long addressId, AddressRequest req) {
        MapSqlParameterSource params = addressParams(userId, req).addValue("id", addressId);
        if (req.isDefault()) {
            jdbc.update("UPDATE addresses SET is_default = FALSE WHERE user_id = :user AND id <> :id", params);
        }
        int updated = jdbc.update("""
                UPDATE addresses
                SET recipient = :recipient, phone_enc = :phone, zipcode = :zipcode, address1 = :address1,
                    address2 = :address2, is_default = (is_default OR :isDefault)
                WHERE id = :id AND user_id = :user
                """, params.addValue("isDefault", req.isDefault()));
        if (updated == 0) {
            throw new BusinessException(ErrorCode.ADDRESS_NOT_FOUND);
        }
    }

    /** 배송지 지우기. 지난 주문에는 주문 시점의 배송 정보가 따로 남아 있어 영향이 없다. */
    @Transactional
    public void deleteAddress(Long userId, Long addressId) {
        MapSqlParameterSource params = new MapSqlParameterSource("user", userId).addValue("id", addressId);
        if (jdbc.update("DELETE FROM addresses WHERE id = :id AND user_id = :user", params) == 0) {
            throw new BusinessException(ErrorCode.ADDRESS_NOT_FOUND);
        }
        // 기본 배송지를 지웠으면 가장 최근 것을 기본으로
        Long defaults = jdbc.queryForObject(
                "SELECT COUNT(*) FROM addresses WHERE user_id = :user AND is_default = TRUE", params, Long.class);
        if (defaults == null || defaults == 0) {
            jdbc.update("UPDATE addresses SET is_default = TRUE WHERE user_id = :user ORDER BY id DESC LIMIT 1",
                    params);
        }
    }

    private MapSqlParameterSource addressParams(Long userId, AddressRequest req) {
        return new MapSqlParameterSource("user", userId)
                .addValue("recipient", req.recipient().strip())
                .addValue("phone", phoneCipher.encrypt(req.phone()))
                .addValue("zipcode", req.zipcode())
                .addValue("address1", req.address1().strip())
                .addValue("address2", req.address2() == null ? "" : req.address2().strip());
    }

    /** description 은 상세에서만 싣는다 */
    private static RowMapper<Product> product(boolean withDescription) {
        return (rs, i) -> {
            int stock = rs.getInt("stock");
            return new Product(rs.getLong("id"), rs.getString("name"), rs.getInt("cid"), rs.getString("cname"),
                    ProductType.valueOf(rs.getString("type")), rs.getString("sname"), rs.getString("image_url"),
                    rs.getLong("price_points"), stock, stock <= 0 || "SOLD_OUT".equals(rs.getString("status")),
                    rs.getBoolean("wished"), withDescription ? rs.getString("description") : null);
        };
    }

    /** 검색어를 LIKE 패턴으로. %, _ 는 글자 그대로 찾도록 ! 로 이스케이프한다. 비어 있으면 null(조건 없음). */
    private static String likePattern(String keyword) {
        if (keyword == null || keyword.isBlank()) {
            return null;
        }
        return "%" + keyword.strip().replace("!", "!!").replace("%", "!%").replace("_", "!_") + "%";
    }
}
