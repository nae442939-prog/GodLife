package com.godlife.backend.shop.dto;

import jakarta.validation.Valid;
import jakarta.validation.constraints.Email;
import jakarta.validation.constraints.Max;
import jakarta.validation.constraints.Min;
import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.NotEmpty;
import jakarta.validation.constraints.NotNull;
import jakarta.validation.constraints.Pattern;
import jakarta.validation.constraints.Size;

import java.time.LocalDateTime;
import java.util.List;

/** 포인트 상점 요청·응답 */
public final class ShopDtos {

    public static final int MAX_QUANTITY = 99;

    private ShopDtos() {
    }

    /** PHYSICAL = 배송받는 실물 / COUPON = 이용권 · 상품권 (배송 없이 쿠폰 번호 발급) */
    public enum ProductType {
        PHYSICAL, COUPON
    }

    public enum ProductStatus {
        ON_SALE, SOLD_OUT, HIDDEN
    }

    public enum OrderStatus {
        PREPARING, SHIPPING, DELIVERED, CANCELED
    }

    public record Category(Integer id, String name) {
    }

    /**
     * 상품 한 개 (목록 · 상세 공용).
     * @param imageUrl    상품 사진 주소. 비어 있으면 화면이 기본 그림을 보여 준다
     * @param soldOut     품절 (재고가 없거나 판매자가 품절로 표시)
     * @param wished      보는 사람이 찜했는지
     * @param description 상세에서만 채운다 (목록은 null)
     */
    public record Product(Long id, String name, Integer categoryId, String categoryName, ProductType type,
                          String sponsorName, String imageUrl, long pricePoints, int stock, boolean soldOut,
                          boolean wished, String description) {
    }

    /**
     * 장바구니 한 줄.
     * @param available 지금 이 수량으로 살 수 있는지 (판매 중이고 재고가 충분한지)
     */
    public record CartItem(Long productId, String name, String imageUrl, ProductType type, long pricePoints,
                           int quantity, int stock, boolean available) {
    }

    public record QuantityRequest(
            @Min(value = 1, message = "수량은 1개 이상이어야 해요.")
            @Max(value = MAX_QUANTITY, message = "한 번에 99개까지 담을 수 있어요.")
            int quantity) {
    }

    /** @param phone 010-1234-5678 모양 (본인에게만 보여 준다) */
    public record Address(Long id, String recipient, String phone, String zipcode, String address1, String address2,
                          boolean isDefault) {
    }

    public record AddressRequest(
            @NotBlank(message = "받는 사람을 입력해 주세요.")
            @Size(max = 50, message = "받는 사람은 50자까지 쓸 수 있어요.")
            String recipient,
            @NotBlank(message = "연락처를 입력해 주세요.")
            @Pattern(regexp = "^01[016789]-?\\d{3,4}-?\\d{4}$", message = "휴대폰 번호를 다시 확인해 주세요.")
            String phone,
            @NotBlank(message = "우편번호를 입력해 주세요.")
            @Pattern(regexp = "^\\d{5}$", message = "우편번호는 숫자 5자리예요.")
            String zipcode,
            @NotBlank(message = "주소를 입력해 주세요.")
            @Size(max = 200, message = "주소는 200자까지 쓸 수 있어요.")
            String address1,
            @Size(max = 200, message = "상세 주소는 200자까지 쓸 수 있어요.")
            String address2,
            boolean isDefault) {
    }

    public record OrderLine(
            @NotNull(message = "상품을 골라 주세요.")
            Long productId,
            @Min(value = 1, message = "수량은 1개 이상이어야 해요.")
            @Max(value = MAX_QUANTITY, message = "한 번에 99개까지 살 수 있어요.")
            int quantity) {
    }

    /**
     * 주문.
     * @param addressId  배송지 (실물 상품이 있으면 필수, 쿠폰만 사면 없어도 된다)
     * @param requestKey 화면이 결제 버튼을 누를 때마다 만드는 값. 같은 값으로 두 번 오면 한 번만 주문된다
     * @param fromCart   장바구니에서 온 주문이면 산 상품을 장바구니에서 뺀다
     */
    public record OrderRequest(
            @NotEmpty(message = "주문할 상품이 없어요.")
            @Size(max = 30, message = "한 번에 30가지 상품까지 주문할 수 있어요.")
            List<@Valid OrderLine> items,
            Long addressId,
            @NotBlank(message = "다시 시도해 주세요.")
            @Size(max = 50, message = "다시 시도해 주세요.")
            String requestKey,
            boolean fromCart) {
    }

    public record Created(Long id) {
    }

    /** 주문 목록 한 줄: 첫 상품 이름 + 그 밖의 상품 가짓수 */
    public record OrderSummary(Long id, OrderStatus status, long totalPoints, String firstItemName,
                               String firstImageUrl, ProductType firstType, int itemCount, LocalDateTime orderedAt) {
    }

    /** @param coupons 쿠폰 상품이면 발급된 쿠폰 번호들 (실물이면 빈 목록) */
    public record OrderItem(Long productId, String name, String imageUrl, ProductType type, int quantity,
                            long unitPoints, List<String> coupons) {
    }

    /** 주문 시점의 배송 정보 */
    public record Shipping(String recipient, String phone, String zipcode, String address1, String address2) {
    }

    /**
     * 주문 상세.
     * @param rewardPoints  보상 포인트로 낸 금액
     * @param chargedPoints 충전 포인트로 낸 금액
     * @param cancelable    지금 취소할 수 있는지 (준비 중이고 쿠폰이 발급되지 않은 주문)
     * @param shipping      배송 정보 (쿠폰만 산 주문은 null)
     */
    public record OrderDetail(Long id, OrderStatus status, long totalPoints, long rewardPoints, long chargedPoints,
                              String trackingNo, LocalDateTime orderedAt, LocalDateTime canceledAt,
                              boolean cancelable, Shipping shipping, List<OrderItem> items) {
    }

    // ---------- 관리자 ----------

    public record Sponsor(Long id, String name, String contactEmail, boolean active, int productCount) {
    }

    public record SponsorRequest(
            @NotBlank(message = "스폰서 이름을 입력해 주세요.")
            @Size(max = 100, message = "스폰서 이름은 100자까지 쓸 수 있어요.")
            String name,
            @NotBlank(message = "담당자 이메일을 입력해 주세요.")
            @Email(message = "이메일 주소를 다시 확인해 주세요.")
            @Size(max = 255, message = "이메일이 너무 길어요.")
            String contactEmail,
            boolean active) {
    }

    /** 관리자 화면의 상품 한 줄 (숨긴 상품 포함) */
    public record AdminProduct(Long id, String name, Long sponsorId, String sponsorName, Integer categoryId,
                               String categoryName, ProductType type, String description, String imageUrl,
                               long pricePoints, int stock, int soldCount, ProductStatus status) {
    }

    public record ProductRequest(
            @NotNull(message = "스폰서를 골라 주세요.")
            Long sponsorId,
            @NotNull(message = "카테고리를 골라 주세요.")
            Integer categoryId,
            @NotNull(message = "상품 종류를 골라 주세요.")
            ProductType type,
            @NotBlank(message = "상품 이름을 입력해 주세요.")
            @Size(max = 150, message = "상품 이름은 150자까지 쓸 수 있어요.")
            String name,
            @NotBlank(message = "상품 설명을 입력해 주세요.")
            @Size(max = 2000, message = "상품 설명은 2000자까지 쓸 수 있어요.")
            String description,
            @Min(value = 100, message = "가격은 100P 이상이어야 해요.")
            @Max(value = 10_000_000, message = "가격이 너무 커요.")
            long pricePoints,
            @Min(value = 0, message = "재고는 0 이상이어야 해요.")
            @Max(value = 1_000_000, message = "재고가 너무 커요.")
            int stock,
            @NotNull(message = "판매 상태를 골라 주세요.")
            ProductStatus status) {
    }

    /** 관리자 화면의 주문 한 줄 */
    public record AdminOrder(Long userId, String nickname, OrderDetail order) {
    }

    public record ShipRequest(
            @NotBlank(message = "운송장 번호를 입력해 주세요.")
            @Size(max = 50, message = "운송장 번호는 50자까지 쓸 수 있어요.")
            String trackingNo) {
    }
}
