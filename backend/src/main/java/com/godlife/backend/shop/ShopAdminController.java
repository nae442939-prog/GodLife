package com.godlife.backend.shop;

import com.godlife.backend.shop.dto.ShopDtos.AdminOrder;
import com.godlife.backend.shop.dto.ShopDtos.AdminProduct;
import com.godlife.backend.shop.dto.ShopDtos.Created;
import com.godlife.backend.shop.dto.ShopDtos.OrderStatus;
import com.godlife.backend.shop.dto.ShopDtos.ProductRequest;
import com.godlife.backend.shop.dto.ShopDtos.ShipRequest;
import com.godlife.backend.shop.dto.ShopDtos.Sponsor;
import com.godlife.backend.shop.dto.ShopDtos.SponsorRequest;
import jakarta.validation.Valid;
import lombok.RequiredArgsConstructor;
import org.springframework.http.HttpStatus;
import org.springframework.http.MediaType;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.PutMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestParam;
import org.springframework.web.bind.annotation.RequestPart;
import org.springframework.web.bind.annotation.RestController;
import org.springframework.web.multipart.MultipartFile;

import java.util.List;
import java.util.Map;

/** 포인트 상점 관리 (/api/admin/** 는 관리자만 쓸 수 있다): 스폰서 · 상품 · 주문 */
@RestController
@RequiredArgsConstructor
public class ShopAdminController {

    private final ShopAdminService adminService;
    private final OrderService orderService;

    @GetMapping("/api/admin/shop/sponsors")
    public List<Sponsor> sponsors() {
        return adminService.sponsors();
    }

    @PostMapping("/api/admin/shop/sponsors")
    public ResponseEntity<Created> addSponsor(@Valid @RequestBody SponsorRequest request) {
        return ResponseEntity.status(HttpStatus.CREATED).body(new Created(adminService.addSponsor(request)));
    }

    @PutMapping("/api/admin/shop/sponsors/{id}")
    public ResponseEntity<Void> updateSponsor(@PathVariable Long id, @Valid @RequestBody SponsorRequest request) {
        adminService.updateSponsor(id, request);
        return ResponseEntity.noContent().build();
    }

    /** 모든 상품 (숨긴 것 포함) */
    @GetMapping("/api/admin/shop/products")
    public List<AdminProduct> products() {
        return adminService.products();
    }

    @PostMapping("/api/admin/shop/products")
    public ResponseEntity<Created> addProduct(@Valid @RequestBody ProductRequest request) {
        return ResponseEntity.status(HttpStatus.CREATED).body(new Created(adminService.addProduct(request)));
    }

    @PutMapping("/api/admin/shop/products/{id}")
    public ResponseEntity<Void> updateProduct(@PathVariable Long id, @Valid @RequestBody ProductRequest request) {
        adminService.updateProduct(id, request);
        return ResponseEntity.noContent().build();
    }

    /** 상품 사진 올리기 (multipart: file = JPG/PNG 5MB 이하) → { imageUrl } */
    @PostMapping(value = "/api/admin/shop/products/{id}/image", consumes = MediaType.MULTIPART_FORM_DATA_VALUE)
    public Map<String, String> changeImage(@PathVariable Long id, @RequestPart("file") MultipartFile file) {
        return Map.of("imageUrl", adminService.changeImage(id, file));
    }

    /** 주문 목록 (?status=PREPARING|SHIPPING|DELIVERED|CANCELED, 없으면 전체) */
    @GetMapping("/api/admin/shop/orders")
    public List<AdminOrder> orders(@RequestParam(required = false) OrderStatus status) {
        return orderService.all(status);
    }

    /** 발송 처리 (준비 중 → 배송 중) */
    @PostMapping("/api/admin/shop/orders/{id}/ship")
    public ResponseEntity<Void> ship(@PathVariable Long id, @Valid @RequestBody ShipRequest request) {
        orderService.ship(id, request.trackingNo());
        return ResponseEntity.noContent().build();
    }

    /** 배송 완료 처리 (배송 중 → 수령 완료) */
    @PostMapping("/api/admin/shop/orders/{id}/deliver")
    public ResponseEntity<Void> deliver(@PathVariable Long id) {
        orderService.deliver(id);
        return ResponseEntity.noContent().build();
    }

    /** 준비 중인 주문 취소 (포인트 · 재고를 되돌리고 회원에게 알린다) */
    @PostMapping("/api/admin/shop/orders/{id}/cancel")
    public ResponseEntity<Void> cancel(@PathVariable Long id) {
        orderService.cancelByAdmin(id);
        return ResponseEntity.noContent().build();
    }
}
