package com.godlife.backend.demo;

import com.godlife.backend.challenge.ChallengeService;
import com.godlife.backend.common.crypto.Hashing;
import com.godlife.backend.community.CommunityService;
import com.godlife.backend.demo.DemoAccounts.Account;
import com.godlife.backend.user.Role;
import com.godlife.backend.user.User;
import com.godlife.backend.user.UserRepository;
import com.godlife.backend.user.WithdrawalService;
import com.godlife.backend.wallet.PointSource;
import com.godlife.backend.wallet.PointTxType;
import com.godlife.backend.wallet.WalletService;
import lombok.extern.slf4j.Slf4j;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.boot.context.event.ApplicationReadyEvent;
import org.springframework.context.event.EventListener;
import org.springframework.jdbc.core.namedparam.MapSqlParameterSource;
import org.springframework.jdbc.core.namedparam.NamedParameterJdbcTemplate;
import org.springframework.scheduling.annotation.Scheduled;
import org.springframework.security.crypto.password.PasswordEncoder;
import org.springframework.stereotype.Service;
import org.springframework.transaction.PlatformTransactionManager;
import org.springframework.transaction.support.TransactionTemplate;

import java.time.Clock;
import java.time.LocalDate;
import java.time.LocalDateTime;
import java.time.LocalTime;
import java.util.List;
import java.util.Optional;

/**
 * 테스트 계정을 매일 새벽 4시에 처음 상태로 되돌린다 (DemoAccounts 에 계정이 설정돼 있을 때만).
 * 여러 회사가 같은 계정을 쓰므로, 전날 누가 무엇을 했든 다음 날은 같은 화면에서 시작하게 한다.
 * 테이블을 하나하나 되돌리지 않고, 예전 계정은 탈퇴와 똑같이 내리고(이메일 · 닉네임이 바뀐다) 같은 아이디로 새로 만든다.
 * - 내리기 전에: 쓴 글 · 댓글 · 응원 댓글을 지우고, 다른 참가자가 없는 시작 전 챌린지를 지운다 (다른 회사 화면에 남지 않게)
 * - 새 계정: 보상 포인트 50,000P (원장에 ADJUST / REWARD 한 줄, 멱등 키는 계정마다 하나). 충전 포인트는 넣지 않는다.
 *   일반 계정에는 체험 데이터(DemoStarter)도 넣는다.
 * 서버를 시연할 때만 켜서 새벽 4시에 꺼져 있을 수 있으므로, 켜질 때도 마지막 초기화 시각 전에 만든 계정이면 다시 만든다.
 */
@Slf4j
@Service
public class DemoAccountReset {

    static final long STARTING_REWARD = 50_000;
    static final LocalTime RESET_AT = LocalTime.of(4, 0);

    private final DemoAccounts demoAccounts;
    private final UserRepository userRepository;
    private final PasswordEncoder passwordEncoder;
    private final WithdrawalService withdrawalService;
    private final CommunityService communityService;
    private final ChallengeService challengeService;
    private final WalletService walletService;
    private final DemoStarter demoStarter;
    private final NamedParameterJdbcTemplate jdbc;
    private final TransactionTemplate tx;
    private final Clock clock;
    private final boolean resetOnStartup;

    public DemoAccountReset(DemoAccounts demoAccounts, UserRepository userRepository, PasswordEncoder passwordEncoder,
                            WithdrawalService withdrawalService, CommunityService communityService,
                            ChallengeService challengeService, WalletService walletService, DemoStarter demoStarter,
                            NamedParameterJdbcTemplate jdbc, PlatformTransactionManager transactionManager, Clock clock,
                            @Value("${app.demo.reset-on-startup:true}") boolean resetOnStartup) {
        this.demoAccounts = demoAccounts;
        this.userRepository = userRepository;
        this.passwordEncoder = passwordEncoder;
        this.withdrawalService = withdrawalService;
        this.communityService = communityService;
        this.challengeService = challengeService;
        this.walletService = walletService;
        this.demoStarter = demoStarter;
        this.jdbc = jdbc;
        this.tx = new TransactionTemplate(transactionManager);
        this.clock = clock;
        this.resetOnStartup = resetOnStartup;
    }

    @EventListener(ApplicationReadyEvent.class)
    public void onStartup() {
        if (resetOnStartup) {
            resetStale();
        }
    }

    @Scheduled(cron = "0 0 4 * * *", zone = "Asia/Seoul")
    public void daily() {
        resetStale();
    }

    /** 마지막 초기화 시각(새벽 4시) 전에 만든 테스트 계정(또는 아직 없는 계정)을 새로 만든다. 새로 만든 계정 수 */
    public int resetStale() {
        LocalDateTime boundary = lastResetTime(LocalDateTime.now(clock));
        int reset = 0;
        for (Account account : demoAccounts.accounts()) {
            try {
                // 계정마다 한 트랜잭션: 내리기와 새로 만들기가 함께 되거나 함께 안 된다
                if (Boolean.TRUE.equals(tx.execute(status -> resetIfStale(account, boundary)))) {
                    reset++;
                }
            } catch (RuntimeException e) {
                log.warn("테스트 계정을 초기화하지 못했습니다 ({}): {}", account.email(), e.getMessage());
            }
        }
        if (reset > 0) {
            log.info("테스트 계정을 초기화했습니다: {}개", reset);
        }
        return reset;
    }

    static LocalDateTime lastResetTime(LocalDateTime now) {
        LocalDateTime today = now.toLocalDate().atTime(RESET_AT);
        return now.isBefore(today) ? today.minusDays(1) : today;
    }

    private boolean resetIfStale(Account account, LocalDateTime boundary) {
        Optional<User> current = userRepository.findByEmail(account.email());
        if (current.isPresent() && !current.get().getCreatedAt().isBefore(boundary)) {
            return false;
        }
        current.ifPresent(this::retire);
        create(account);
        return true;
    }

    /** 예전 테스트 계정 내리기: 남이 볼 수 있는 흔적을 지운 뒤 탈퇴 처리 */
    private void retire(User user) {
        Long id = user.getId();
        MapSqlParameterSource me = new MapSqlParameterSource("me", id).addValue("today", LocalDate.now(clock));
        for (Long postId : ids("SELECT id FROM posts WHERE user_id = :me AND status = 'VISIBLE'", me)) {
            communityService.delete(id, postId);
        }
        for (Long commentId : ids("SELECT id FROM post_comments WHERE user_id = :me AND status = 'VISIBLE'", me)) {
            communityService.deleteComment(id, commentId);
        }
        jdbc.update("UPDATE comments SET is_deleted = TRUE WHERE user_id = :me", me);
        // 시작 전이고 다른 참가자가 없는 챌린지만 (다른 사람이 참여한 챌린지는 그 사람 것이기도 하다)
        for (Long challengeId : ids("""
                SELECT id FROM challenges
                WHERE host_id = :me AND status = 'RECRUITING' AND start_date > :today AND participant_count <= 1
                """, me)) {
            challengeService.delete(challengeId, id);
        }
        withdrawalService.forceWithdraw(id);
    }

    private void create(Account account) {
        User user = userRepository.saveAndFlush(User.createDemo(account.email(),
                passwordEncoder.encode(account.password()), account.nickname(),
                Hashing.sha256Hex("godlife-demo-account:" + account.email()), account.role()));
        walletService.settle(user.getId(), PointTxType.ADJUST, PointSource.REWARD, STARTING_REWARD, null, null,
                "demo-grant-" + user.getId());
        if (account.role() == Role.USER) {
            demoStarter.seed(user.getId());
        }
    }

    private List<Long> ids(String sql, MapSqlParameterSource params) {
        return jdbc.queryForList(sql, params, Long.class);
    }
}
