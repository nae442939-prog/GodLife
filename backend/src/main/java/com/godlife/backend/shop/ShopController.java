package com.godlife.backend.shop;

import com.godlife.backend.auth.AuthUser;
import com.godlife.backend.challenge.dto.PageResponse;
import com.godlife.backend.shop.ShopService.SortOption;
import com.godlife.backend.shop.dto.ShopDtos.Address;
import com.godlife.backend.shop.dto.ShopDtos.AddressRequest;
import com.godlife.backend.shop.dto.ShopDtos.CartItem;
import com.godlife.backend.shop.dto.ShopDtos.Category;
import com.godlife.backend.shop.dto.ShopDtos.Created;
import com.godlife.backend.shop.dto.ShopDtos.OrderDetail;
import com.godlife.backend.shop.dto.ShopDtos.OrderRequest;
import com.godlife.backend.shop.dto.ShopDtos.OrderSummary;
import com.godlife.backend.shop.dto.ShopDtos.Product;
import com.godlife.backend.shop.dto.ShopDtos.QuantityRequest;
import jakarta.validation.Valid;
import lombok.RequiredArgsConstructor;
import org.springframework.core.io.FileSystemResource;
import org.springframework.core.io.Resource;
import org.springframework.http.CacheControl;
import org.springframework.http.HttpStatus;
import org.springframework.http.MediaType;
import org.springframework.http.ResponseEntity;
import org.springframework.security.core.annotation.AuthenticationPrincipal;
import org.springframework.web.bind.annotation.DeleteMapping;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.PutMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestParam;
import org.springframework.web.bind.annotation.RestController;

import java.time.Duration;
import java.util.List;
import java.util.Locale;

/** 포인트 상점. 상품 보기는 비로그인도 가능하고, 찜 · 장바구니 · 배송지 · 주문은 로그인 회원만. */
@RestController
@RequiredArgsConstructor
public class ShopController {

    private final ShopService shopService;
    private final OrderService orderService;

    // ---------- 상품 (누구나) ----------

    @GetMapping("/api/shop/categories")
    public List<Category> categories() {
        return shopService.categories();
    }

    /** 상품 목록 (?categoryId=, ?q= 검색, ?sort=popular|latest|price_asc|price_desc, ?page=) */
    @GetMapping("/api/shop/products")
    public PageResponse<Product> products(@RequestParam(required = false) Integer categoryId,
                                          @RequestParam(required = false) String q,
                                          @RequestParam(defaultValue = "popular") String sort,
                                          @RequestParam(defaultValue = "0") int page,
                                          @AuthenticationPrincipal AuthUser authUser) {
        return shopService.products(idOf(authUser), categoryId, q, parseSort(sort), page);
    }

    @GetMapping("/api/shop/products/{id}")
    public Product product(@PathVariable Long id, @AuthenticationPrincipal AuthUser authUser) {
        return shopService.product(idOf(authUser), id);
    }

    /** 상품 사진. 사진을 바꾸면 주소도 바뀌므로 오래 캐시해도 된다. */
    @GetMapping("/api/shop/images/{productId}/{name}")
    public ResponseEntity<Resource> image(@PathVariable Long productId, @PathVariable String name) {
        return ResponseEntity.ok()
                .contentType(MediaType.IMAGE_JPEG)
                .cacheControl(CacheControl.maxAge(Duration.ofDays(30)).cachePublic())
                .body(new FileSystemResource(shopService.imageFile(productId, name)));
    }

    // ---------- 찜 ----------

    @GetMapping("/api/shop/wishlist")
    public List<Product> wishlist(@AuthenticationPrincipal AuthUser authUser) {
        return shopService.wishlist(authUser.id());
    }

    @PutMapping("/api/shop/wishlist/{productId}")
    public ResponseEntity<Void> wish(@PathVariable Long productId, @AuthenticationPrincipal AuthUser authUser) {
        shopService.wish(authUser.id(), productId, true);
        return ResponseEntity.noContent().build();
    }

    @DeleteMapping("/api/shop/wishlist/{productId}")
    public ResponseEntity<Void> unwish(@PathVariable Long productId, @AuthenticationPrincipal AuthUser authUser) {
        shopService.wish(authUser.id(), productId, false);
        return ResponseEntity.noContent().build();
    }

    // ---------- 장바구니 ----------

    @GetMapping("/api/shop/cart")
    public List<CartItem> cart(@AuthenticationPrincipal AuthUser authUser) {
        return shopService.cart(authUser.id());
    }

    /** 담기 / 수량 바꾸기 (그 상품의 수량을 보낸 값으로 맞춘다) */
    @PutMapping("/api/shop/cart/{productId}")
    public List<CartItem> putCart(@PathVariable Long productId, @AuthenticationPrincipal AuthUser authUser,
                                  @Valid @RequestBody QuantityRequest request) {
        shopService.putCart(authUser.id(), productId, request.quantity());
        return shopService.cart(authUser.id());
    }

    @DeleteMapping("/api/shop/cart/{productId}")
    public List<CartItem> removeCart(@PathVariable Long productId, @AuthenticationPrincipal AuthUser authUser) {
        shopService.removeCart(authUser.id(), productId);
        return shopService.cart(authUser.id());
    }

    // ---------- 배송지 ----------

    @GetMapping("/api/shop/addresses")
    public List<Address> addresses(@AuthenticationPrincipal AuthUser authUser) {
        return shopService.addresses(authUser.id());
    }

    @PostMapping("/api/shop/addresses")
    public ResponseEntity<Created> addAddress(@AuthenticationPrincipal AuthUser authUser,
                                              @Valid @RequestBody AddressRequest request) {
        return ResponseEntity.status(HttpStatus.CREATED)
                .body(new Created(shopService.addAddress(authUser.id(), request)));
    }

    @PutMapping("/api/shop/addresses/{id}")
    public ResponseEntity<Void> updateAddress(@PathVariable Long id, @AuthenticationPrincipal AuthUser authUser,
                                              @Valid @RequestBody AddressRequest request) {
        shopService.updateAddress(authUser.id(), id, request);
        return ResponseEntity.noContent().build();
    }

    @DeleteMapping("/api/shop/addresses/{id}")
    public ResponseEntity<Void> deleteAddress(@PathVariable Long id, @AuthenticationPrincipal AuthUser authUser) {
        shopService.deleteAddress(authUser.id(), id);
        return ResponseEntity.noContent().build();
    }

    // ---------- 주문 ----------

    /** 주문하고 포인트로 결제한다. 같은 requestKey 로 다시 보내면 먼저 만든 주문을 돌려준다. */
    @PostMapping("/api/shop/orders")
    public ResponseEntity<Created> order(@AuthenticationPrincipal AuthUser authUser,
                                         @Valid @RequestBody OrderRequest request) {
        return ResponseEntity.status(HttpStatus.CREATED)
                .body(new Created(orderService.place(authUser.id(), request)));
    }

    @GetMapping("/api/shop/orders")
    public List<OrderSummary> orders(@AuthenticationPrincipal AuthUser authUser,
                                     @RequestParam(defaultValue = "0") int page) {
        return orderService.orders(authUser.id(), page);
    }

    @GetMapping("/api/shop/orders/{id}")
    public OrderDetail orderDetail(@PathVariable Long id, @AuthenticationPrincipal AuthUser authUser) {
        return orderService.order(authUser.id(), id);
    }

    /** 주문 취소 ('준비 중'이고 쿠폰이 발급되지 않은 주문만). 포인트는 원래 출처 그대로 돌아온다. */
    @PostMapping("/api/shop/orders/{id}/cancel")
    public OrderDetail cancel(@PathVariable Long id, @AuthenticationPrincipal AuthUser authUser) {
        orderService.cancel(authUser.id(), id);
        return orderService.order(authUser.id(), id);
    }

    /** 수령 확인 (배송 중 → 수령 완료) */
    @PostMapping("/api/shop/orders/{id}/receive")
    public OrderDetail receive(@PathVariable Long id, @AuthenticationPrincipal AuthUser authUser) {
        orderService.receive(authUser.id(), id);
        return orderService.order(authUser.id(), id);
    }

    private static Long idOf(AuthUser authUser) {
        return authUser == null ? null : authUser.id();
    }

    private static SortOption parseSort(String sort) {
        try {
            return SortOption.valueOf(sort.toUpperCase(Locale.ROOT));
        } catch (IllegalArgumentException e) {
            return SortOption.POPULAR;
        }
    }
}
