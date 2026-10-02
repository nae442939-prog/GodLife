package com.godlife.backend.shop;

import com.godlife.backend.payment.TestCharger;
import com.godlife.backend.auth.JwtProvider;
import com.godlife.backend.user.User;
import com.godlife.backend.user.UserRepository;
import com.godlife.backend.wallet.PointSource;
import com.godlife.backend.wallet.PointTxType;
import com.godlife.backend.wallet.WalletService;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.boot.webmvc.test.autoconfigure.AutoConfigureMockMvc;
import org.springframework.http.MediaType;
import org.springframework.test.context.ActiveProfiles;
import org.springframework.test.web.servlet.MockMvc;
import org.springframework.test.web.servlet.ResultActions;
import org.springframework.test.web.servlet.request.MockHttpServletRequestBuilder;
import org.springframework.transaction.annotation.Transactional;

import java.util.UUID;
import java.util.regex.Matcher;
import java.util.regex.Pattern;

import static org.assertj.core.api.Assertions.assertThat;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.delete;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.post;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.put;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.jsonPath;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

/** 포인트 상점: 상품 · 찜 · 장바구니 · 배송지 · 주문(보상 포인트 먼저) · 취소(원래 출처로) · 쿠폰 · 관리자 */
@SpringBootTest
@AutoConfigureMockMvc
@ActiveProfiles("test")
@Transactional
class ShopApiTest {

    private static final Pattern ID = Pattern.compile("\"id\":(\\d+)");

    @Autowired MockMvc mvc;
    @Autowired UserRepository userRepository;
    @Autowired JwtProvider jwtProvider;
    @Autowired WalletService walletService;
    @Autowired TestCharger testCharger;

    private User user;
    private String me;
    private String admin;
    private long sponsor;
    /** 다른 상품과 섞이지 않게 테스트마다 다른 낱말을 상품 이름에 넣어 검색한다 */
    private String word;

    @BeforeEach
    void setUp() throws Exception {
        user = newUser();
        me = tokenOf(user, "USER");
        admin = tokenOf(newUser(), "ADMIN");
        word = "w" + UUID.randomUUID().toString().replace("-", "").substring(0, 10);
        sponsor = idOf(send(post("/api/admin/shop/sponsors"), admin,
                "{\"name\":\"테스트 스폰서\",\"contactEmail\":\"sponsor@example.com\",\"active\":true}")
                .andExpect(status().isCreated()));
    }

    @Test
    @DisplayName("관리자가 등록한 상품을 비로그인도 보고, 숨긴 상품과 정지된 스폰서의 상품은 보이지 않는다")
    void catalog() throws Exception {
        long mat = product("요가 매트", "PHYSICAL", 3000, 5, "ON_SALE");
        long hidden = product("숨긴 상품", "PHYSICAL", 1000, 5, "HIDDEN");
        product("품절 상품", "PHYSICAL", 500, 0, "ON_SALE");

        mvc.perform(get("/api/shop/products").param("q", word).param("sort", "price_desc"))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.totalElements").value(2))
                .andExpect(jsonPath("$.items[0].id").value(mat))
                .andExpect(jsonPath("$.items[0].sponsorName").value("테스트 스폰서"))
                .andExpect(jsonPath("$.items[1].soldOut").value(true));
        mvc.perform(get("/api/shop/products/" + mat)).andExpect(jsonPath("$.description").value("설명"));
        mvc.perform(get("/api/shop/products/" + hidden)).andExpect(status().isNotFound());
        mvc.perform(get("/api/shop/categories")).andExpect(jsonPath("$.length()").value(5));

        // 관리자가 아니면 등록할 수 없다
        send(post("/api/admin/shop/products"), me, productBody("남의 상품", "PHYSICAL", 1000, 1, "ON_SALE"))
                .andExpect(status().isForbidden());

        send(put("/api/admin/shop/sponsors/" + sponsor), admin,
                "{\"name\":\"테스트 스폰서\",\"contactEmail\":\"sponsor@example.com\",\"active\":false}")
                .andExpect(status().isNoContent());
        mvc.perform(get("/api/shop/products").param("q", word)).andExpect(jsonPath("$.totalElements").value(0));
    }

    @Test
    @DisplayName("주문하면 보상 포인트를 먼저 쓰고 모자란 만큼 충전 포인트를 쓴다. 같은 요청은 한 번만 주문된다")
    void orderUsesRewardFirst() throws Exception {
        fund(3_000, 10_000);
        long mat = product("요가 매트", "PHYSICAL", 2000, 5, "ON_SALE");
        long address = address("홍길동", true);
        String body = orderBody(mat, 2, address, "key-1", false);

        // 실물 상품은 배송지가 있어야 한다
        send(post("/api/shop/orders"), me, orderBody(mat, 2, null, "key-0", false))
                .andExpect(status().isBadRequest()).andExpect(jsonPath("$.code").value("ADDRESS_REQUIRED"));

        long order = idOf(send(post("/api/shop/orders"), me, body).andExpect(status().isCreated()));
        assertThat(idOf(send(post("/api/shop/orders"), me, body).andExpect(status().isCreated()))).isEqualTo(order);

        fetch("/api/shop/orders/" + order, me)
                .andExpect(jsonPath("$.status").value("PREPARING"))
                .andExpect(jsonPath("$.totalPoints").value(4000))
                .andExpect(jsonPath("$.rewardPoints").value(3000))
                .andExpect(jsonPath("$.chargedPoints").value(1000))
                .andExpect(jsonPath("$.cancelable").value(true))
                .andExpect(jsonPath("$.shipping.recipient").value("홍길동"))
                .andExpect(jsonPath("$.shipping.phone").value("010-1234-5678"))
                .andExpect(jsonPath("$.items[0].quantity").value(2));
        // 지갑 거래 내역에 주문한 상품이 붙는다 (보상 · 충전 두 줄)
        fetch("/api/wallet", me).andExpect(jsonPath("$.rewardBalance").value(0))
                .andExpect(jsonPath("$.chargedBalance").value(9000))
                .andExpect(jsonPath("$.transactions[0].type").value("PURCHASE"))
                .andExpect(jsonPath("$.transactions[0].orderId").value(order))
                .andExpect(jsonPath("$.transactions[0].orderTitle").value(word + " 요가 매트"))
                .andExpect(jsonPath("$.transactions[1].orderId").value(order));
        mvc.perform(get("/api/shop/products/" + mat)).andExpect(jsonPath("$.stock").value(3));
        fetch("/api/shop/orders", me).andExpect(jsonPath("$[0].id").value(order))
                .andExpect(jsonPath("$[0].firstItemName").value(word + " 요가 매트"));

        // 남의 주문은 볼 수 없다
        fetch("/api/shop/orders/" + order, tokenOf(newUser(), "USER")).andExpect(status().isNotFound());
    }

    @Test
    @DisplayName("주문을 취소하면 재고가 돌아오고 포인트는 원래 출처 그대로 돌아온다")
    void cancelRefundsToSameSource() throws Exception {
        fund(3_000, 10_000);
        long mat = product("요가 매트", "PHYSICAL", 2000, 5, "ON_SALE");
        long order = idOf(send(post("/api/shop/orders"), me, orderBody(mat, 2, address("홍길동", true), "key-1", false)));

        mvc.perform(post("/api/shop/orders/" + order + "/cancel").header("Authorization", "Bearer " + me))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.status").value("CANCELED"))
                .andExpect(jsonPath("$.cancelable").value(false));
        // 보상으로 낸 3,000P 는 보상으로, 충전으로 낸 1,000P 는 충전으로
        fetch("/api/wallet", me).andExpect(jsonPath("$.rewardBalance").value(3000))
                .andExpect(jsonPath("$.chargedBalance").value(10000));
        mvc.perform(get("/api/shop/products/" + mat)).andExpect(jsonPath("$.stock").value(5));

        mvc.perform(post("/api/shop/orders/" + order + "/cancel").header("Authorization", "Bearer " + me))
                .andExpect(status().isConflict()).andExpect(jsonPath("$.code").value("ORDER_CANNOT_CANCEL"));
    }

    @Test
    @DisplayName("쿠폰 상품은 배송지 없이 사고 쿠폰 번호가 수량만큼 바로 발급된다. 발급된 주문은 취소할 수 없다")
    void couponOrder() throws Exception {
        fund(0, 10_000);
        long coupon = product("헬스장 1일 이용권", "COUPON", 3000, 10, "ON_SALE");

        long order = idOf(send(post("/api/shop/orders"), me, orderBody(coupon, 2, null, "key-1", false))
                .andExpect(status().isCreated()));
        fetch("/api/shop/orders/" + order, me)
                .andExpect(jsonPath("$.status").value("DELIVERED"))
                .andExpect(jsonPath("$.shipping").doesNotExist())
                .andExpect(jsonPath("$.cancelable").value(false))
                .andExpect(jsonPath("$.items[0].coupons.length()").value(2))
                .andExpect(jsonPath("$.chargedPoints").value(6000));
        mvc.perform(post("/api/shop/orders/" + order + "/cancel").header("Authorization", "Bearer " + me))
                .andExpect(status().isConflict());
    }

    @Test
    @DisplayName("관리자가 발송하면 배송 중이 되고 회원에게 알린다. 회원이 수령 확인을 하면 수령 완료가 된다")
    void shipAndReceive() throws Exception {
        fund(5_000, 0);
        long mat = product("요가 매트", "PHYSICAL", 2000, 5, "ON_SALE");
        long order = idOf(send(post("/api/shop/orders"), me, orderBody(mat, 1, address("홍길동", true), "key-1", false)));

        fetch("/api/admin/shop/orders?status=PREPARING", admin)
                .andExpect(jsonPath("$[?(@.order.id == " + order + ")].nickname").value(user.getNickname()));
        // 발송 전에는 수령 확인을 할 수 없다
        mvc.perform(post("/api/shop/orders/" + order + "/receive").header("Authorization", "Bearer " + me))
                .andExpect(status().isConflict());

        send(post("/api/admin/shop/orders/" + order + "/ship"), admin, "{\"trackingNo\":\"1234-5678\"}")
                .andExpect(status().isNoContent());
        send(post("/api/admin/shop/orders/" + order + "/ship"), admin, "{\"trackingNo\":\"1234-5678\"}")
                .andExpect(status().isConflict());
        fetch("/api/shop/orders/" + order, me).andExpect(jsonPath("$.status").value("SHIPPING"))
                .andExpect(jsonPath("$.trackingNo").value("1234-5678"))
                .andExpect(jsonPath("$.cancelable").value(false));
        fetch("/api/notifications", me).andExpect(jsonPath("$[0].type").value("ORDER"));

        mvc.perform(post("/api/shop/orders/" + order + "/receive").header("Authorization", "Bearer " + me))
                .andExpect(status().isOk()).andExpect(jsonPath("$.status").value("DELIVERED"));
    }

    @Test
    @DisplayName("관리자가 준비 중인 주문을 취소하면 포인트를 돌려주고 회원에게 알린다")
    void adminCancel() throws Exception {
        fund(5_000, 0);
        long mat = product("요가 매트", "PHYSICAL", 2000, 5, "ON_SALE");
        long order = idOf(send(post("/api/shop/orders"), me, orderBody(mat, 1, address("홍길동", true), "key-1", false)));

        mvc.perform(post("/api/admin/shop/orders/" + order + "/cancel").header("Authorization", "Bearer " + admin))
                .andExpect(status().isNoContent());
        fetch("/api/wallet", me).andExpect(jsonPath("$.rewardBalance").value(5000));
        fetch("/api/notifications", me).andExpect(jsonPath("$[0].title").value("주문이 취소됐어요"));
    }

    @Test
    @DisplayName("장바구니에 담고 수량을 바꾸고, 장바구니에서 주문하면 산 상품이 빠진다. 찜은 여러 번 눌러도 한 번이다")
    void cartAndWishlist() throws Exception {
        fund(20_000, 0);
        long mat = product("요가 매트", "PHYSICAL", 2000, 5, "ON_SALE");
        long pen = product("볼펜 세트", "PHYSICAL", 1000, 5, "ON_SALE");

        send(put("/api/shop/cart/" + mat), me, "{\"quantity\":1}").andExpect(status().isOk());
        send(put("/api/shop/cart/" + mat), me, "{\"quantity\":3}")
                .andExpect(jsonPath("$.length()").value(1)).andExpect(jsonPath("$[0].quantity").value(3));
        send(put("/api/shop/cart/" + pen), me, "{\"quantity\":1}").andExpect(jsonPath("$.length()").value(2));
        // 재고보다 많이 담을 수 없다
        send(put("/api/shop/cart/" + pen), me, "{\"quantity\":6}").andExpect(status().isConflict());

        send(put("/api/shop/wishlist/" + mat), me, null).andExpect(status().isNoContent());
        send(put("/api/shop/wishlist/" + mat), me, null).andExpect(status().isNoContent());
        fetch("/api/shop/wishlist", me).andExpect(jsonPath("$.length()").value(1))
                .andExpect(jsonPath("$[0].wished").value(true));
        fetch("/api/shop/products/" + pen, me).andExpect(jsonPath("$.wished").value(false));

        send(post("/api/shop/orders"), me, orderBody(mat, 3, address("홍길동", true), "key-1", true))
                .andExpect(status().isCreated());
        fetch("/api/shop/cart", me).andExpect(jsonPath("$.length()").value(1))
                .andExpect(jsonPath("$[0].productId").value(pen));

        mvc.perform(delete("/api/shop/wishlist/" + mat).header("Authorization", "Bearer " + me))
                .andExpect(status().isNoContent());
        fetch("/api/shop/wishlist", me).andExpect(jsonPath("$.length()").value(0));
    }

    @Test
    @DisplayName("배송지: 첫 배송지가 기본이 되고, 기본을 바꾸거나 지울 수 있다. 남의 배송지로는 주문할 수 없다")
    void addresses() throws Exception {
        long first = address("홍길동", false);
        long second = address("김철수", true);
        fetch("/api/shop/addresses", me).andExpect(jsonPath("$.length()").value(2))
                .andExpect(jsonPath("$[0].id").value(second))
                .andExpect(jsonPath("$[0].isDefault").value(true))
                .andExpect(jsonPath("$[1].isDefault").value(false));

        mvc.perform(delete("/api/shop/addresses/" + second).header("Authorization", "Bearer " + me))
                .andExpect(status().isNoContent());
        fetch("/api/shop/addresses", me).andExpect(jsonPath("$.length()").value(1))
                .andExpect(jsonPath("$[0].id").value(first))
                .andExpect(jsonPath("$[0].isDefault").value(true));

        send(post("/api/shop/addresses"), me, addressBody("이상한", "0212345678", false)).andExpect(status().isBadRequest());

        // 남의 배송지로 주문
        fund(5_000, 0);
        long mat = product("요가 매트", "PHYSICAL", 2000, 5, "ON_SALE");
        String other = tokenOf(newUser(), "USER");
        mvc.perform(delete("/api/shop/addresses/" + first).header("Authorization", "Bearer " + other))
                .andExpect(status().isNotFound());
        long theirs = idOf(send(post("/api/shop/addresses"), other, addressBody("남", "01099998888", true)));
        send(post("/api/shop/orders"), me, orderBody(mat, 1, theirs, "key-1", false)).andExpect(status().isNotFound());
    }

    @Test
    @DisplayName("포인트나 재고가 모자라면 주문할 수 없다")
    void notEnough() throws Exception {
        fund(1_000, 0);
        long mat = product("요가 매트", "PHYSICAL", 2000, 1, "ON_SALE");
        long address = address("홍길동", true);

        send(post("/api/shop/orders"), me, orderBody(mat, 2, address, "key-1", false))
                .andExpect(status().isConflict()).andExpect(jsonPath("$.code").value("OUT_OF_STOCK"));
        send(post("/api/shop/orders"), me, orderBody(mat, 1, address, "key-2", false))
                .andExpect(status().isConflict()).andExpect(jsonPath("$.code").value("INSUFFICIENT_POINTS"));
    }

    // ---------- helpers ----------

    /** 보상 포인트와 충전 포인트를 넣어 준다 */
    private void fund(long reward, long charged) {
        String key = UUID.randomUUID().toString();
        walletService.settle(user.getId(), PointTxType.REWARD, PointSource.REWARD, reward, null, null, "test:" + key);
        if (charged > 0) {
            testCharger.charge(user.getId(), charged);
        }
    }

    private long product(String name, String type, long price, int stock, String status) throws Exception {
        return idOf(send(post("/api/admin/shop/products"), admin, productBody(name, type, price, stock, status))
                .andExpect(status().isCreated()));
    }

    private String productBody(String name, String type, long price, int stock, String status) {
        return """
                {"sponsorId":%d,"categoryId":1,"type":"%s","name":"%s %s","description":"설명","pricePoints":%d,
                 "stock":%d,"status":"%s"}
                """.formatted(sponsor, type, word, name, price, stock, status);
    }

    private long address(String recipient, boolean isDefault) throws Exception {
        return idOf(send(post("/api/shop/addresses"), me, addressBody(recipient, "010-1234-5678", isDefault))
                .andExpect(status().isCreated()));
    }

    private static String addressBody(String recipient, String phone, boolean isDefault) {
        return """
                {"recipient":"%s","phone":"%s","zipcode":"04524","address1":"서울시 중구 세종대로 110",
                 "address2":"1층","isDefault":%s}
                """.formatted(recipient, phone, isDefault);
    }

    private static String orderBody(long productId, int quantity, Long addressId, String key, boolean fromCart) {
        return "{\"items\":[{\"productId\":%d,\"quantity\":%d}],\"addressId\":%s,\"requestKey\":\"%s\",\"fromCart\":%s}"
                .formatted(productId, quantity, addressId, key, fromCart);
    }

    private ResultActions fetch(String url, String token) throws Exception {
        return mvc.perform(get(url)
                .header("Authorization", "Bearer " + token));
    }

    private ResultActions send(MockHttpServletRequestBuilder request, String token, String body) throws Exception {
        request.header("Authorization", "Bearer " + token);
        if (body != null) {
            request.contentType(MediaType.APPLICATION_JSON).content(body);
        }
        return mvc.perform(request);
    }

    private User newUser() {
        String suffix = UUID.randomUUID().toString().replace("-", "").substring(0, 12);
        return userRepository.saveAndFlush(User.createWithPassword(suffix + "@example.com", "unused", "s" + suffix,
                "s".repeat(52) + suffix));
    }

    private String tokenOf(User target, String role) {
        return jwtProvider.createAccessToken(target.getId(), role);
    }

    private static long idOf(ResultActions result) throws Exception {
        Matcher m = ID.matcher(result.andReturn().getResponse().getContentAsString());
        assertThat(m.find()).isTrue();
        return Long.parseLong(m.group(1));
    }
}
