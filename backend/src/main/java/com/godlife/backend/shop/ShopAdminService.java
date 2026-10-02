package com.godlife.backend.shop;

import com.godlife.backend.common.error.BusinessException;
import com.godlife.backend.common.error.ErrorCode;
import com.godlife.backend.common.upload.ImageStore;
import com.godlife.backend.shop.dto.ShopDtos.AdminProduct;
import com.godlife.backend.shop.dto.ShopDtos.ProductRequest;
import com.godlife.backend.shop.dto.ShopDtos.ProductStatus;
import com.godlife.backend.shop.dto.ShopDtos.ProductType;
import com.godlife.backend.shop.dto.ShopDtos.Sponsor;
import com.godlife.backend.shop.dto.ShopDtos.SponsorRequest;
import lombok.RequiredArgsConstructor;
import org.springframework.jdbc.core.namedparam.MapSqlParameterSource;
import org.springframework.jdbc.core.namedparam.NamedParameterJdbcTemplate;
import org.springframework.jdbc.support.GeneratedKeyHolder;
import org.springframework.jdbc.support.KeyHolder;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;
import org.springframework.web.multipart.MultipartFile;

import java.util.List;

/**
 * 포인트 상점 관리 (관리자): 스폰서와 상품을 등록 · 수정한다.
 * 상품은 지우지 않고 숨긴다(HIDDEN) — 지난 주문이 상품을 가리키고 있어서다.
 * 스폰서를 정지하면 그 스폰서의 상품이 상점에서 보이지 않는다.
 */
@Service
@RequiredArgsConstructor
public class ShopAdminService {

    private final NamedParameterJdbcTemplate jdbc;
    private final ImageStore imageStore;

    // ---------- 스폰서 ----------

    @Transactional(readOnly = true)
    public List<Sponsor> sponsors() {
        return jdbc.query("""
                SELECT s.id, s.name, s.contact_email, s.status,
                       (SELECT COUNT(*) FROM products p WHERE p.sponsor_id = s.id) AS products
                FROM sponsors s ORDER BY s.id
                """, (rs, i) -> new Sponsor(rs.getLong("id"), rs.getString("name"), rs.getString("contact_email"),
                "ACTIVE".equals(rs.getString("status")), rs.getInt("products")));
    }

    @Transactional
    public Long addSponsor(SponsorRequest req) {
        KeyHolder key = new GeneratedKeyHolder();
        jdbc.update("INSERT INTO sponsors (name, contact_email, status) VALUES (:name, :email, :status)",
                sponsorParams(req), key);
        return key.getKey().longValue();
    }

    @Transactional
    public void updateSponsor(Long sponsorId, SponsorRequest req) {
        int updated = jdbc.update("""
                UPDATE sponsors SET name = :name, contact_email = :email, status = :status WHERE id = :id
                """, sponsorParams(req).addValue("id", sponsorId));
        if (updated == 0) {
            throw new BusinessException(ErrorCode.VALIDATION_ERROR, "없는 스폰서예요.");
        }
    }

    // ---------- 상품 ----------

    /** 모든 상품 (숨긴 것 포함, 최근 등록한 것부터) */
    @Transactional(readOnly = true)
    public List<AdminProduct> products() {
        return jdbc.query("""
                SELECT p.id, p.name, p.type, p.description, p.image_url, p.price_points, p.stock, p.sold_count,
                       p.status, s.id AS sid, s.name AS sname, c.id AS cid, c.name AS cname
                FROM products p
                  JOIN sponsors s ON s.id = p.sponsor_id
                  JOIN product_categories c ON c.id = p.category_id
                ORDER BY p.id DESC
                """, (rs, i) -> new AdminProduct(rs.getLong("id"), rs.getString("name"), rs.getLong("sid"),
                rs.getString("sname"), rs.getInt("cid"), rs.getString("cname"),
                ProductType.valueOf(rs.getString("type")), rs.getString("description"), rs.getString("image_url"),
                rs.getLong("price_points"), rs.getInt("stock"), rs.getInt("sold_count"),
                ProductStatus.valueOf(rs.getString("status"))));
    }

    @Transactional
    public Long addProduct(ProductRequest req) {
        MapSqlParameterSource params = productParams(req);
        KeyHolder key = new GeneratedKeyHolder();
        jdbc.update("""
                INSERT INTO products (sponsor_id, category_id, type, name, description, price_points, stock, status)
                VALUES (:sponsor, :category, :type, :name, :description, :price, :stock, :status)
                """, params, key);
        return key.getKey().longValue();
    }

    /** 상품 수정. 재고를 바꾸는 동안 주문과 엇갈리지 않게 상품 행을 잠근다. */
    @Transactional
    public void updateProduct(Long productId, ProductRequest req) {
        MapSqlParameterSource params = productParams(req).addValue("id", productId);
        lock(productId);
        jdbc.update("""
                UPDATE products
                SET sponsor_id = :sponsor, category_id = :category, type = :type, name = :name,
                    description = :description, price_points = :price, stock = :stock, status = :status
                WHERE id = :id
                """, params);
    }

    /** 상품 사진 바꾸기 (JPG·PNG). 전에 올린 사진 파일은 지운다. 새 사진 주소를 돌려준다. */
    @Transactional
    public String changeImage(Long productId, MultipartFile file) {
        String old = lock(productId);
        String key = imageStore.storeProductImage(productId, file);
        String url = ShopService.IMAGE_URL_PREFIX + key.substring(ShopService.IMAGE_KEY_PREFIX.length());
        jdbc.update("UPDATE products SET image_url = :url WHERE id = :id",
                new MapSqlParameterSource("id", productId).addValue("url", url));
        if (old != null && old.startsWith(ShopService.IMAGE_URL_PREFIX)) {
            imageStore.delete(ShopService.IMAGE_KEY_PREFIX + old.substring(ShopService.IMAGE_URL_PREFIX.length()));
        }
        return url;
    }

    /** 상품 행을 잠그고 지금 사진 주소를 돌려준다 */
    private String lock(Long productId) {
        return jdbc.queryForList("SELECT image_url FROM products WHERE id = :id FOR UPDATE",
                        new MapSqlParameterSource("id", productId), String.class).stream().findFirst()
                .orElseThrow(() -> new BusinessException(ErrorCode.PRODUCT_NOT_FOUND));
    }

    private MapSqlParameterSource productParams(ProductRequest req) {
        MapSqlParameterSource params = new MapSqlParameterSource("sponsor", req.sponsorId())
                .addValue("category", req.categoryId());
        Long known = jdbc.queryForObject("""
                SELECT (SELECT COUNT(*) FROM sponsors WHERE id = :sponsor)
                     + (SELECT COUNT(*) FROM product_categories WHERE id = :category)
                """, params, Long.class);
        if (known == null || known < 2) {
            throw new BusinessException(ErrorCode.VALIDATION_ERROR, "스폰서나 카테고리를 다시 골라 주세요.");
        }
        return params.addValue("type", req.type().name()).addValue("name", req.name().strip())
                .addValue("description", req.description().strip()).addValue("price", req.pricePoints())
                .addValue("stock", req.stock()).addValue("status", req.status().name());
    }

    private static MapSqlParameterSource sponsorParams(SponsorRequest req) {
        return new MapSqlParameterSource("name", req.name().strip()).addValue("email", req.contactEmail().strip())
                .addValue("status", req.active() ? "ACTIVE" : "SUSPENDED");
    }
}
