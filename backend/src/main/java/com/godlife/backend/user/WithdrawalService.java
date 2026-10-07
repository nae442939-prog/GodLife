package com.godlife.backend.user;

import com.godlife.backend.auth.RefreshTokenRepository;
import com.godlife.backend.common.error.BusinessException;
import com.godlife.backend.common.error.ErrorCode;
import com.godlife.backend.common.ratelimit.RequestThrottle;
import com.godlife.backend.common.upload.ImageStore;
import lombok.RequiredArgsConstructor;
import org.springframework.jdbc.core.namedparam.MapSqlParameterSource;
import org.springframework.jdbc.core.namedparam.NamedParameterJdbcTemplate;
import org.springframework.security.crypto.password.PasswordEncoder;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.time.Clock;
import java.time.Duration;
import java.time.LocalDateTime;
import java.util.ArrayList;
import java.util.List;

/**
 * 회원 탈퇴. 포인트가 얽힌 것이 남아 있으면 탈퇴를 막는다 (탈퇴하면서 포인트를 옮기거나 없애지 않는다):
 * - 아직 정산이 끝나지 않은 챌린지에 참여 중이면 불가 (포기했더라도 정산이 끝나야 한다)
 * - 내가 연 챌린지가 아직 끝나지 않았으면 불가
 * - 쓰지 않은 충전 포인트가 있으면 불가 (지갑에서 환불받은 뒤 탈퇴)
 * - 포인트 상점에서 아직 받지 못한 주문(준비 중 · 배송 중)이 있으면 불가
 * 보상 포인트는 원래 현금으로 바꿀 수 없는 포인트라 탈퇴하면 그대로 사라진다 (탈퇴 전에 안내한다).
 * 탈퇴하면 이메일 · 닉네임 · 휴대폰 · 사진 · 소개 · 일기 · 팔로우 · 소셜 연결을 지우고 계정을 WITHDRAWN 으로 바꾼다.
 * 챌린지 참여 · 인증 · 채팅 기록은 다른 참가자의 기록과 얽혀 있어 '탈퇴한회원' 이름으로 남는다.
 */
@Service
@RequiredArgsConstructor
public class WithdrawalService {

    /** 탈퇴 확인 문구 (비밀번호가 없는 소셜 계정은 이 문구를 입력해 확인한다) */
    public static final String CONFIRM_TEXT = "탈퇴";
    private static final int VERIFY_TRIES = 10;
    private static final Duration VERIFY_WINDOW = Duration.ofMinutes(10);

    /**
     * 탈퇴 전 확인.
     * @param blockers      탈퇴를 막는 이유들 (비어 있으면 탈퇴할 수 있다)
     * @param rewardBalance 탈퇴하면 사라지는 보상 포인트
     * @param hasPassword   비밀번호로 확인하는 계정인지 (아니면 확인 문구)
     */
    public record WithdrawalCheck(boolean canWithdraw, List<String> blockers, long rewardBalance,
                                  boolean hasPassword) {
    }

    private final UserService userService;
    private final UserRepository userRepository;
    private final RefreshTokenRepository refreshTokenRepository;
    private final PasswordEncoder passwordEncoder;
    private final RequestThrottle throttle;
    private final ImageStore imageStore;
    private final NamedParameterJdbcTemplate jdbc;
    private final Clock clock;

    @Transactional(readOnly = true)
    public WithdrawalCheck check(Long userId) {
        User user = userService.getActive(userId);
        MapSqlParameterSource me = new MapSqlParameterSource("me", userId);
        List<String> blockers = new ArrayList<>();

        long joined = count("""
                SELECT COUNT(*) FROM challenge_participants p JOIN challenges c ON c.id = p.challenge_id
                WHERE p.user_id = :me AND p.status IN ('ACTIVE', 'COMPLETED', 'FAILED', 'GAVE_UP')
                  AND c.status <> 'SETTLED'
                """, me);
        if (joined > 0) {
            blockers.add("아직 정산이 끝나지 않은 챌린지가 " + joined + "개 있어요. 챌린지가 끝나고 정산된 뒤에 탈퇴할 수 있어요.");
        }
        long hosting = count("SELECT COUNT(*) FROM challenges WHERE host_id = :me AND status IN ('RECRUITING', 'ONGOING')", me);
        if (hosting > 0) {
            blockers.add("내가 연 챌린지가 " + hosting + "개 진행 중이에요. 시작 전이면 삭제하고, 시작했으면 끝난 뒤에 탈퇴할 수 있어요.");
        }
        long charged = count("SELECT COALESCE(SUM(charged_balance), 0) FROM wallets WHERE user_id = :me", me);
        if (charged > 0) {
            blockers.add("쓰지 않은 충전 포인트 " + String.format("%,d", charged) + "P가 남아 있어요. 포인트 지갑에서 환불받은 뒤에 탈퇴할 수 있어요.");
        }
        long shipping = count("SELECT COUNT(*) FROM orders WHERE user_id = :me AND status IN ('PREPARING', 'SHIPPING')",
                me);
        if (shipping > 0) {
            blockers.add("포인트 상점에서 아직 받지 못한 주문이 " + shipping + "개 있어요. 받은 뒤(또는 취소한 뒤)에 탈퇴할 수 있어요.");
        }
        long reward = count("SELECT COALESCE(SUM(reward_balance), 0) FROM wallets WHERE user_id = :me", me);
        return new WithdrawalCheck(blockers.isEmpty(), blockers, reward, user.getPasswordHash() != null);
    }

    /**
     * 탈퇴할 수 있는지와 본인인지 확인한다 (탈퇴는 하지 않는다).
     * 비밀번호가 있는 계정은 비밀번호, 소셜 계정은 확인 문구('탈퇴')로 확인한다. 비밀번호 대입을 막으려 10분에 10번까지.
     */
    @Transactional(readOnly = true)
    public User verify(Long userId, String password, String confirm) {
        User user = userService.getActive(userId);
        WithdrawalCheck check = check(userId);
        if (!check.canWithdraw()) {
            throw new BusinessException(ErrorCode.CANNOT_WITHDRAW, check.blockers().get(0));
        }
        if (user.getPasswordHash() != null) {
            throttle.check("withdraw:" + userId, VERIFY_TRIES, VERIFY_WINDOW);
            if (password == null || !passwordEncoder.matches(password, user.getPasswordHash())) {
                throw new BusinessException(ErrorCode.VALIDATION_ERROR, "비밀번호가 올바르지 않습니다.");
            }
        } else if (!CONFIRM_TEXT.equals(confirm == null ? null : confirm.strip())) {
            throw new BusinessException(ErrorCode.VALIDATION_ERROR, "확인 문구 '" + CONFIRM_TEXT + "'를 입력해 주세요.");
        }
        return user;
    }

    /** 탈퇴. 본인 확인을 한 번 더 거친 뒤 진행한다 (화면의 마지막 경고에서 [탈퇴하기]를 눌렀을 때). */
    @Transactional
    public void withdraw(Long userId, String password, String confirm) {
        User user = verify(userId, password, confirm);

        MapSqlParameterSource me = new MapSqlParameterSource("me", userId);
        // 지울 사진 파일: 일기 사진, 올린 프로필 사진
        List<String> files = new ArrayList<>(jdbc.queryForList(
                "SELECT photo_key FROM diary_entries WHERE user_id = :me AND photo_key IS NOT NULL", me, String.class));
        String image = user.getProfileImageUrl();
        if (image != null && image.startsWith(ProfileEditService.IMAGE_URL_PREFIX)) {
            files.add("profile/" + image.substring(ProfileEditService.IMAGE_URL_PREFIX.length()));
        }

        jdbc.update("DELETE FROM diary_entries WHERE user_id = :me", me);
        jdbc.update("DELETE FROM follows WHERE follower_id = :me OR following_id = :me", me);
        jdbc.update("DELETE FROM social_accounts WHERE user_id = :me", me);
        // 포인트 상점: 배송지 · 장바구니 · 찜을 지우고, 지난 주문에 남은 배송 정보(이름 · 연락처 · 주소)도 지운다
        jdbc.update("""
                UPDATE orders SET ship_recipient = NULL, ship_phone_enc = NULL, ship_zipcode = NULL,
                                  ship_address1 = NULL, ship_address2 = NULL
                WHERE user_id = :me
                """, me);
        jdbc.update("DELETE FROM addresses WHERE user_id = :me", me);
        jdbc.update("DELETE FROM cart_items WHERE user_id = :me", me);
        jdbc.update("DELETE FROM wishlists WHERE user_id = :me", me);
        // 개인정보를 지우고, 같은 이메일 · 닉네임 · 휴대폰으로 다시 가입할 수 있게 비워 둔다
        user.withdraw("탈퇴한회원" + userId);
        userRepository.flush();
        jdbc.update("UPDATE users SET email = :email WHERE id = :me",
                me.addValue("email", "withdrawn-" + userId + "@withdrawn.invalid"));
        // 토큰 폐기는 맨 나중에: 이 호출이 영속성 컨텍스트를 비워서, 먼저 부르면 위의 회원 변경이 저장되지 않는다
        refreshTokenRepository.revokeAllByUserId(userId, LocalDateTime.now(clock));
        files.forEach(imageStore::delete);
    }

    private long count(String sql, MapSqlParameterSource params) {
        Long n = jdbc.queryForObject(sql, params, Long.class);
        return n == null ? 0 : n;
    }
}
