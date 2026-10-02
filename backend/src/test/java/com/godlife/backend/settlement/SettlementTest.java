package com.godlife.backend.settlement;

import com.godlife.backend.payment.TestCharger;
import com.godlife.backend.auth.JwtProvider;
import com.godlife.backend.challenge.ChallengeLifecycleService;
import com.godlife.backend.user.User;
import com.godlife.backend.user.UserRepository;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.boot.webmvc.test.autoconfigure.AutoConfigureMockMvc;
import org.springframework.http.MediaType;
import org.springframework.mock.web.MockMultipartFile;
import org.springframework.test.context.ActiveProfiles;
import org.springframework.test.web.servlet.MockMvc;
import org.springframework.test.web.servlet.ResultActions;
import org.springframework.transaction.annotation.Transactional;

import javax.imageio.ImageIO;
import java.awt.Color;
import java.awt.Graphics2D;
import java.awt.image.BufferedImage;
import java.io.ByteArrayOutputStream;
import java.io.IOException;
import java.time.Clock;
import java.time.LocalDate;
import java.util.UUID;
import java.util.concurrent.ThreadLocalRandom;
import java.util.regex.Matcher;
import java.util.regex.Pattern;

import static org.assertj.core.api.Assertions.assertThat;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.multipart;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.post;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.jsonPath;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

/**
 * 정산: 매일 결과를 기록하고 챌린지가 끝나면 한 번에 지급. 3일짜리 3,000P 챌린지면 하루 몫은 1,000P.
 * 참가자는 각자 10,000P 충전 → 참여하면 충전 포인트 7,000P.
 */
@SpringBootTest
@AutoConfigureMockMvc
@ActiveProfiles("test")
@Transactional
class SettlementTest {

    private static final Pattern ID = Pattern.compile("\"id\":(\\d+)");

    @Autowired MockMvc mvc;
    @Autowired UserRepository userRepository;
    @Autowired JwtProvider jwtProvider;
    @Autowired SettlementService settlementService;
    @Autowired ChallengeLifecycleService lifecycle;
    @Autowired DailySettlementRepository settlementRepository;
    @Autowired SettlementRepository finalRepository;
    @Autowired TestCharger testCharger;
    @Autowired Clock clock;

    private String host;
    private String a;
    private String b;
    private String c;
    private LocalDate today;

    @BeforeEach
    void setUp() throws Exception {
        host = tokenOf(newUser());
        a = tokenOf(newUser());
        b = tokenOf(newUser());
        c = tokenOf(newUser());
        today = LocalDate.now(clock);
        for (String t : new String[]{a, b, c}) {
            charge(t);
        }
    }

    @Test
    @DisplayName("매일은 결과만 기록하고 지갑은 그대로, 챌린지가 끝나면 모두 더해 한 번에 지급한다")
    void resultsDailyPayOnceAtEnd() throws Exception {
        long id = betChallenge(3000, 3, "DAILY", null);
        joinAll(id);
        verify(id, a);
        verify(id, b);

        settlementService.settleDue(today.plusDays(1));

        // 첫날 결과: A·B 성공, C 실패 → C 몫 1,000P 를 A·B 가 500P 씩 (예정)
        DailySettlement s = settlementRepository.findByChallengeIdAndPeriodStart(id, today).orElseThrow();
        assertThat(s.getSuccessCount()).isEqualTo(2);
        assertThat(s.getFailCount()).isEqualTo(1);
        assertThat(s.getForfeitedPool()).isEqualTo(1000);
        assertThat(s.getRewardShare()).isEqualTo(500);
        // 아직 지급 전
        wallet(a).andExpect(jsonPath("$.chargedBalance").value(7000))
                .andExpect(jsonPath("$.rewardBalance").value(0));

        // 끝난 다음 날: 나머지 이틀 결과(아무도 인증 못 함) 기록 + 종료 + 한 번에 지급
        lifecycle.run(today.plusDays(3));

        // A: 환급 1,000 (첫날 몫) + 보상 500 → 거래는 환급 1건 + 보상 1건
        wallet(a).andExpect(jsonPath("$.chargedBalance").value(8000))
                .andExpect(jsonPath("$.rewardBalance").value(500))
                .andExpect(jsonPath("$.transactions[0].type").value("REWARD"))
                .andExpect(jsonPath("$.transactions[0].settlement").value(true))
                .andExpect(jsonPath("$.transactions[0].challengeTitle").value("정산 테스트"))
                .andExpect(jsonPath("$.transactions[1].type").value("REFUND"))
                .andExpect(jsonPath("$.transactions[1].amount").value(1000))
                .andExpect(jsonPath("$.transactions.length()").value(4)); // 충전·참가비·환급·보상
        wallet(c).andExpect(jsonPath("$.chargedBalance").value(7000))
                .andExpect(jsonPath("$.rewardBalance").value(0));
        assertThat(settlementRepository.findByChallengeId(id)).hasSize(3);
        Settlement settlement = finalRepository.findByChallengeId(id).orElseThrow();
        assertThat(settlement.getStatus()).isEqualTo(Settlement.Status.DONE);
        assertThat(settlement.getForfeitedPool()).isEqualTo(1000 + 3000 + 3000);
        mvc.perform(get("/api/challenges/" + id).header("Authorization", "Bearer " + a))
                .andExpect(jsonPath("$.status").value("SETTLED"));
    }

    @Test
    @DisplayName("정산이 두 번 돌아도 한 번만 지급된다")
    void idempotent() throws Exception {
        long id = betChallenge(3000, 1, "DAILY", null); // 하루짜리 → 몫 3,000P
        joinAll(id);
        verify(id, a);

        lifecycle.run(today.plusDays(1));
        lifecycle.run(today.plusDays(1));

        // A: 3,000 환급 + 보상 (B·C 몫 6,000 을 혼자 → 상한 3,000)
        wallet(a).andExpect(jsonPath("$.chargedBalance").value(10000))
                .andExpect(jsonPath("$.rewardBalance").value(3000));
        assertThat(settlementRepository.findByChallengeId(id)).hasSize(1);
        assertThat(finalRepository.findByChallengeId(id)).isPresent();
    }

    @Test
    @DisplayName("보상은 하루 몫까지만 (상한): 혼자 성공해도 2,000P 가 아니라 1,000P")
    void rewardCap() throws Exception {
        long id = betChallenge(3000, 3, "DAILY", null);
        joinAll(id);
        verify(id, a);

        settlementService.settleDue(today.plusDays(1));

        DailySettlement s = settlementRepository.findByChallengeIdAndPeriodStart(id, today).orElseThrow();
        assertThat(s.getForfeitedPool()).isEqualTo(2000);
        assertThat(s.getRewardShare()).isEqualTo(1000);
        assertThat(s.getDistributed()).isEqualTo(1000);
    }

    @Test
    @DisplayName("아무도 실패하지 않은 날은 나눌 포인트가 없고, 모두 하루 몫만 돌려받는다")
    void nobodyFailed() throws Exception {
        long id = betChallenge(3000, 1, "DAILY", null);
        joinAll(id);
        verify(id, a);
        verify(id, b);
        verify(id, c);

        lifecycle.run(today.plusDays(1));

        wallet(c).andExpect(jsonPath("$.chargedBalance").value(10000))
                .andExpect(jsonPath("$.rewardBalance").value(0));
        DailySettlement s = settlementRepository.findByChallengeIdAndPeriodStart(id, today).orElseThrow();
        assertThat(s.getFailCount()).isZero();
        assertThat(s.getForfeitedPool()).isZero();
    }

    @Test
    @DisplayName("포기한 사람은 남은 날 모두 실패로 치고, 한 날만큼은 끝날 때 돌려받는다")
    void gaveUp() throws Exception {
        long id = betChallenge(3000, 3, "DAILY", null);
        joinAll(id);
        verify(id, a);
        verify(id, c); // C 는 첫날 인증하고
        mvc.perform(post("/api/challenges/" + id + "/participants/me/give-up").header("Authorization", "Bearer " + c))
                .andExpect(status().isNoContent()); // 포기

        lifecycle.run(today.plusDays(3));

        // C: 첫날 몫 1,000 환급 + 첫날 B 몫 나눔 500 (A·C 성공)
        wallet(c).andExpect(jsonPath("$.chargedBalance").value(8000))
                .andExpect(jsonPath("$.rewardBalance").value(500));
        wallet(b).andExpect(jsonPath("$.chargedBalance").value(7000));
    }

    @Test
    @DisplayName("주 N회는 한 주 단위: 목표(주 2회)를 못 채우면 한 만큼만 돌려받는다")
    void weekly() throws Exception {
        long id = betChallenge(2000, 7, "WEEKLY_N", 2); // 필요 2회 → 1회 몫 1,000P
        joinAll(id);
        verify(id, a); // 1회만

        lifecycle.run(today.plusDays(7));

        wallet(a).andExpect(jsonPath("$.chargedBalance").value(9000))  // 8,000 + 1,000
                .andExpect(jsonPath("$.rewardBalance").value(0));      // 목표를 채운 사람이 없음
        DailySettlement s = settlementRepository.findByChallengeIdAndPeriodStart(id, today).orElseThrow();
        assertThat(s.getPeriodEnd()).isEqualTo(today.plusDays(6));
        assertThat(s.getSuccessCount()).isZero();
        assertThat(s.getForfeitedPool()).isEqualTo(5000); // A 1,000 + B 2,000 + C 2,000
    }

    @Test
    @DisplayName("챌린지 랭킹: 인증 횟수 순, 같은 기록은 같은 순위, 내 줄 표시. 끝난 기간이 없으면 결과는 204")
    void rankingAndNoLatestYet() throws Exception {
        long id = betChallenge(3000, 3, "DAILY", null);
        joinAll(id);
        verify(id, b);

        mvc.perform(get("/api/challenges/" + id + "/ranking").header("Authorization", "Bearer " + a))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$[0].rank").value(1))
                .andExpect(jsonPath("$[0].successDays").value(1))
                .andExpect(jsonPath("$[1].rank").value(2))
                .andExpect(jsonPath("$[2].rank").value(2))
                .andExpect(jsonPath("$[?(@.mine == true)].rank").value(2));
        mvc.perform(get("/api/challenges/" + id + "/settlements/latest").header("Authorization", "Bearer " + a))
                .andExpect(status().isNoContent());
    }

    // ---------- helpers ----------

    private long betChallenge(long fee, int days, String frequency, Integer weekly) throws Exception {
        String body = """
                {"categoryId":1,"title":"정산 테스트","description":"매일 인증","mode":"BET",
                 "startDate":"%s","endDate":"%s","frequencyType":"%s","weeklyCount":%s,
                 "entryFee":%d,"maxParticipants":10}
                """.formatted(today, today.plusDays(days - 1), frequency, weekly, fee).replace("\n", "");
        String res = mvc.perform(post("/api/challenges").header("Authorization", "Bearer " + host)
                        .contentType(MediaType.APPLICATION_JSON).content(body))
                .andExpect(status().isCreated()).andReturn().getResponse().getContentAsString();
        Matcher m = ID.matcher(res);
        assertThat(m.find()).isTrue();
        return Long.parseLong(m.group(1));
    }

    private void joinAll(long id) throws Exception {
        for (String t : new String[]{a, b, c}) {
            mvc.perform(post("/api/challenges/" + id + "/participants").header("Authorization", "Bearer " + t))
                    .andExpect(status().isOk());
        }
    }

    private void verify(long id, String token) throws Exception {
        mvc.perform(multipart("/api/challenges/" + id + "/verifications")
                        .file(new MockMultipartFile("file", "camera.jpg", "image/jpeg", photo()))
                        .header("Authorization", "Bearer " + token))
                .andExpect(status().isCreated());
    }

    private void charge(String token) {
        testCharger.charge(token, 10_000);
    }

    private ResultActions wallet(String token) throws Exception {
        return mvc.perform(get("/api/wallet").header("Authorization", "Bearer " + token)).andExpect(status().isOk());
    }

    private static byte[] photo() throws IOException {
        BufferedImage image = new BufferedImage(80, 60, BufferedImage.TYPE_INT_RGB);
        Graphics2D g = image.createGraphics();
        g.setColor(new Color(ThreadLocalRandom.current().nextInt(0xFFFFFF)));
        g.fillRect(0, 0, 80, 60);
        g.dispose();
        ByteArrayOutputStream out = new ByteArrayOutputStream();
        ImageIO.write(image, "png", out);
        return out.toByteArray();
    }

    private User newUser() {
        String suffix = UUID.randomUUID().toString().replace("-", "").substring(0, 12);
        return userRepository.saveAndFlush(User.createWithPassword(suffix + "@example.com", "unused", "s" + suffix,
                "s".repeat(52) + suffix));
    }

    private String tokenOf(User user) {
        return jwtProvider.createAccessToken(user.getId(), "USER");
    }
}
