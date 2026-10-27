package com.godlife.backend.demo;

import com.godlife.backend.device.DeviceService.Group;
import com.godlife.backend.device.DeviceService.Member;
import com.godlife.backend.shop.dto.ShopDtos.AdminOrder;
import com.godlife.backend.shop.dto.ShopDtos.OrderDetail;
import com.godlife.backend.shop.dto.ShopDtos.OrderItem;
import com.godlife.backend.shop.dto.ShopDtos.Shipping;
import com.godlife.backend.shop.dto.ShopDtos.Sponsor;

/**
 * 테스트용 관리자 화면에서 실제 회원의 개인정보를 가린다 (화면 모양은 그대로 보이게 자리만 남긴다).
 * 김갓생 → 김**, 010-1234-5678 → 010-****-****, 서울 강남구 테헤란로 1 → 서울 강남구 ***, 1.2.3.4 → 1.2.*.*
 */
public final class DemoMask {

    private DemoMask() {
    }

    public static AdminOrder order(AdminOrder row) {
        OrderDetail o = row.order();
        Shipping s = o.shipping();
        Shipping masked = s == null ? null
                : new Shipping(name(s.recipient()), s.phone() == null ? null : "010-****-****", "*****",
                        address(s.address1()), null);
        return new AdminOrder(row.userId(), row.nickname(), new OrderDetail(o.id(), o.status(), o.totalPoints(),
                o.rewardPoints(), o.chargedPoints(), o.trackingNo(), o.orderedAt(), o.canceledAt(), o.cancelable(),
                masked, o.items().stream()
                        .map(i -> new OrderItem(i.productId(), i.name(), i.imageUrl(), i.type(), i.quantity(),
                                i.unitPoints(), i.coupons().stream().map(c -> "****-****").toList()))
                        .toList()));
    }

    public static Sponsor sponsor(Sponsor s) {
        return new Sponsor(s.id(), s.name(), email(s.contactEmail()), s.active(), s.productCount());
    }

    public static Group device(Group g) {
        String key = "IP".equals(g.kind()) ? ip(g.key()) : g.key();
        return new Group(g.kind(), key, g.accounts(), g.lastSeenAt(), g.members().stream()
                .map(m -> new Member(m.userId(), m.nickname(), email(m.email()), m.status(), m.joinedAt(),
                        m.lastSeenAt()))
                .toList());
    }

    static String name(String name) {
        if (name == null || name.isEmpty()) {
            return name;
        }
        return name.charAt(0) + "*".repeat(Math.max(2, name.length() - 1));
    }

    /** 앞의 두 단어(시 · 구)만 남긴다 */
    static String address(String address) {
        if (address == null) {
            return null;
        }
        String[] words = address.trim().split("\\s+");
        return words.length <= 2 ? "***" : words[0] + " " + words[1] + " ***";
    }

    static String email(String email) {
        if (email == null) {
            return null;
        }
        int at = email.indexOf('@');
        return at <= 0 ? "***" : email.charAt(0) + "***" + email.substring(at);
    }

    static String ip(String ip) {
        if (ip == null) {
            return null;
        }
        String[] parts = ip.split("\\.");
        if (parts.length == 4) {
            return parts[0] + "." + parts[1] + ".*.*";
        }
        // IPv6 는 앞 두 묶음만
        String[] groups = ip.split(":");
        return groups.length > 2 ? groups[0] + ":" + groups[1] + ":****" : "***";
    }
}
