package com.godlife.backend.shop;

import com.godlife.backend.shop.dto.ShopDtos.OrderLine;
import com.godlife.backend.shop.dto.ShopDtos.OrderRequest;
import com.godlife.backend.shop.dto.ShopDtos.ProductRequest;
import com.godlife.backend.shop.dto.ShopDtos.ProductStatus;
import com.godlife.backend.shop.dto.ShopDtos.ProductType;
import com.godlife.backend.shop.dto.ShopDtos.SponsorRequest;
import com.godlife.backend.user.User;
import com.godlife.backend.user.UserRepository;
import com.godlife.backend.wallet.PointSource;
import com.godlife.backend.wallet.PointTxType;
import com.godlife.backend.wallet.WalletService;
import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.test.context.ActiveProfiles;

import java.util.ArrayList;
import java.util.List;
import java.util.UUID;
import java.util.concurrent.Callable;
import java.util.concurrent.CountDownLatch;
import java.util.concurrent.ExecutorService;
import java.util.concurrent.Executors;
import java.util.concurrent.Future;

import static org.assertj.core.api.Assertions.assertThat;

/**
 * 동시에 주문이 몰려도 재고보다 많이 팔리거나 포인트가 두 번 빠지지 않는지 (프로젝트 규칙 4).
 * 스레드마다 트랜잭션이 따로 돌아야 해서 롤백하지 않고, 만든 데이터를 끝나고 직접 지운다.
 */
@SpringBootTest
@ActiveProfiles("test")
class ShopConcurrencyTest {

    private static final int THREADS = 8;

    @Autowired OrderService orderService;
    @Autowired ShopAdminService adminService;
    @Autowired WalletService walletService;
    @Autowired UserRepository userRepository;
    @Autowired JdbcTemplate jdbc;

    private final List<Long> userIds = new ArrayList<>();
    private final List<Long> productIds = new ArrayList<>();
    private Long sponsorId;

    @AfterEach
    void cleanUp() {
        userIds.forEach(id -> {
            jdbc.update("DELETE c FROM order_coupons c JOIN order_items i ON i.id = c.order_item_id "
                    + "JOIN orders o ON o.id = i.order_id WHERE o.user_id = ?", id);
            jdbc.update("DELETE i FROM order_items i JOIN orders o ON o.id = i.order_id WHERE o.user_id = ?", id);
            jdbc.update("DELETE FROM orders WHERE user_id = ?", id);
            jdbc.update("DELETE FROM addresses WHERE user_id = ?", id);
            jdbc.update("DELETE t FROM point_transactions t JOIN wallets w ON w.id = t.wallet_id WHERE w.user_id = ?", id);
            jdbc.update("DELETE FROM wallets WHERE user_id = ?", id);
            jdbc.update("DELETE FROM users WHERE id = ?", id);
        });
        productIds.forEach(id -> jdbc.update("DELETE FROM products WHERE id = ?", id));
        if (sponsorId != null) {
            jdbc.update("DELETE FROM sponsors WHERE id = ?", sponsorId);
        }
    }

    @Test
    @DisplayName("재고 3개인 상품을 8명이 동시에 사면 3명만 사고, 못 산 사람의 포인트는 빠지지 않는다")
    void stockNeverOversold() throws Exception {
        Long product = product(1_000, 3);
        List<Long> buyers = new ArrayList<>();
        List<Callable<Object>> tasks = new ArrayList<>();
        for (int i = 0; i < THREADS; i++) {
            Long buyer = newUser(5_000);
            buyers.add(buyer);
            tasks.add(() -> orderService.place(buyer, order(product, 1, "one")));
        }

        assertThat(runAll(tasks)).isEqualTo(3);
        assertThat(count("SELECT stock FROM products WHERE id = ?", product)).isZero();
        assertThat(count("SELECT sold_count FROM products WHERE id = ?", product)).isEqualTo(3);
        long spent = buyers.stream().mapToLong(b -> 5_000 - walletService.wallet(b).rewardBalance()).sum();
        assertThat(spent).isEqualTo(3_000);
    }

    @Test
    @DisplayName("같은 주문 요청이 동시에 8번 와도 한 번만 주문되고 포인트도 한 번만 빠진다")
    void sameRequestKeyOnce() throws Exception {
        Long product = product(1_000, 10);
        Long buyer = newUser(5_000);

        runAll(repeat(() -> orderService.place(buyer, order(product, 1, "double-click"))));

        assertThat(count("SELECT COUNT(*) FROM orders WHERE user_id = ?", buyer)).isEqualTo(1);
        assertThat(walletService.wallet(buyer).rewardBalance()).isEqualTo(4_000);
        assertThat(count("SELECT stock FROM products WHERE id = ?", product)).isEqualTo(9);
    }

    @Test
    @DisplayName("한 사람이 가진 포인트보다 많이 동시에 주문해도 잔액만큼만 주문된다")
    void neverSpendsMoreThanBalance() throws Exception {
        Long product = product(1_000, 20);
        Long buyer = newUser(3_000);
        List<Callable<Object>> tasks = new ArrayList<>();
        for (int i = 0; i < THREADS; i++) {
            String key = "k" + i;
            tasks.add(() -> orderService.place(buyer, order(product, 1, key)));
        }

        assertThat(runAll(tasks)).isEqualTo(3);
        assertThat(walletService.wallet(buyer).balance()).isZero();
        // 결제하지 못한 주문은 재고도 줄이지 않는다 (같은 트랜잭션에서 함께 취소된다)
        assertThat(count("SELECT stock FROM products WHERE id = ?", product)).isEqualTo(17);
    }

    @Test
    @DisplayName("같은 주문을 동시에 8번 취소해도 포인트와 재고는 한 번만 돌아온다")
    void cancelOnce() throws Exception {
        Long product = product(1_000, 5);
        Long buyer = newUser(5_000);
        Long address = address(buyer);
        Long order = orderService.place(buyer, new OrderRequest(List.of(new OrderLine(product, 2)), address, "c", false));

        int ok = runAll(repeat(() -> {
            orderService.cancel(buyer, order);
            return null;
        }));

        assertThat(ok).isEqualTo(1);
        assertThat(walletService.wallet(buyer).rewardBalance()).isEqualTo(5_000);
        assertThat(count("SELECT stock FROM products WHERE id = ?", product)).isEqualTo(5);
    }

    // ---------- helpers ----------

    /** 쿠폰 상품은 배송지 없이 살 수 있어서 동시 주문 테스트에 쓴다 */
    private static OrderRequest order(Long productId, int quantity, String key) {
        return new OrderRequest(List.of(new OrderLine(productId, quantity)), null, key, false);
    }

    private Long product(long price, int stock) {
        if (sponsorId == null) {
            sponsorId = adminService.addSponsor(new SponsorRequest("동시성 스폰서", "c@example.com", true));
        }
        Long id = adminService.addProduct(new ProductRequest(sponsorId, 1, ProductType.COUPON, "동시성 상품", "테스트",
                price, stock, ProductStatus.ON_SALE));
        productIds.add(id);
        return id;
    }

    /** cancelOnce 는 취소할 수 있는 주문(실물 · 준비 중)이 필요해서 상품을 실물로 바꾸고 배송지를 만든다 */
    private Long address(Long userId) {
        jdbc.update("UPDATE products SET type = 'PHYSICAL' WHERE id = ?", productIds.get(productIds.size() - 1));
        jdbc.update("INSERT INTO addresses (user_id, recipient, phone_enc, zipcode, address1) VALUES (?, '홍길동', 'x', '04524', '서울')",
                userId);
        return jdbc.queryForObject("SELECT MAX(id) FROM addresses WHERE user_id = ?", Long.class, userId);
    }

    private Long newUser(long reward) {
        String suffix = UUID.randomUUID().toString().replace("-", "").substring(0, 12);
        Long id = userRepository.saveAndFlush(User.createWithPassword(suffix + "@example.com", "unused", "k" + suffix,
                "k".repeat(52) + suffix)).getId();
        userIds.add(id);
        walletService.settle(id, PointTxType.REWARD, PointSource.REWARD, reward, null, null, "test:" + suffix);
        return id;
    }

    private List<Callable<Object>> repeat(Callable<Object> task) {
        List<Callable<Object>> tasks = new ArrayList<>();
        for (int i = 0; i < THREADS; i++) {
            tasks.add(task);
        }
        return tasks;
    }

    /** 모두 한꺼번에 출발시키고 성공한 수를 센다 */
    private int runAll(List<Callable<Object>> tasks) throws Exception {
        ExecutorService pool = Executors.newFixedThreadPool(tasks.size());
        CountDownLatch start = new CountDownLatch(1);
        try {
            List<Future<Object>> futures = new ArrayList<>();
            for (Callable<Object> task : tasks) {
                futures.add(pool.submit(() -> {
                    start.await();
                    return task.call();
                }));
            }
            start.countDown();
            int ok = 0;
            for (Future<Object> f : futures) {
                try {
                    f.get();
                    ok++;
                } catch (Exception ignored) {
                    // 재고 · 잔액 부족이나 중복으로 거절된 요청
                }
            }
            return ok;
        } finally {
            pool.shutdownNow();
        }
    }

    private long count(String sql, Object arg) {
        Long n = jdbc.queryForObject(sql, Long.class, arg);
        return n == null ? 0 : n;
    }
}
