package com.godlife.backend.verification;

import com.godlife.backend.payment.TestCharger;
import com.godlife.backend.auth.JwtProvider;
import com.godlife.backend.challenge.ChallengeLifecycleService;
import com.godlife.backend.user.User;
import com.godlife.backend.user.UserRepository;
import jakarta.persistence.EntityManager;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.boot.webmvc.test.autoconfigure.AutoConfigureMockMvc;
import org.springframework.http.MediaType;
import org.springframework.jdbc.core.JdbcTemplate;
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

/** 인증 신고 → 관리자 검토, 그리고 담합 의심 표시 통합 테스트 */
@SpringBootTest
@AutoConfigureMockMvc
@ActiveProfiles("test")
@Transactional
class VerificationReportApiTest {

    private static final Pattern ID = Pattern.compile("\"id\":(\\d+)");

    @Autowired MockMvc mvc;
    @Autowired UserRepository userRepository;
    @Autowired JwtProvider jwtProvider;
    @Autowired JdbcTemplate jdbc;
    @Autowired ChallengeLifecycleService lifecycle;
    @Autowired EntityManager entityManager;
    @Autowired TestCharger testCharger;
    @Autowired Clock clock;

    private String host;
    private String friend;
    private String friend2;
    private String stranger;
    private String admin;
    private LocalDate today;

    @BeforeEach
    void setUp() {
        host = tokenOf(newUser(), "USER");
        friend = tokenOf(newUser(), "USER");
        friend2 = tokenOf(newUser(), "USER");
        stranger = tokenOf(newUser(), "USER");
        admin = tokenOf(newUser(), "ADMIN");
        today = LocalDate.now(clock);
    }

    @Test
    @DisplayName("참가자가 남의 인증을 신고하면 관리자 검토에 올라가고, 거절되면 인증이 취소되고 신고한 사람에게 결과가 간다")
    void reportThenAdminRejects() throws Exception {
        long id = challenge("FREE", "");
        join(id, friend);
        join(id, friend2);
        long verificationId = verify(id, friend);

        report(id, verificationId, friend, "내 사진").andExpect(status().isBadRequest());
        report(id, verificationId, stranger, "남의 챌린지").andExpect(status().isNotFound());
        report(id, verificationId, friend2, " ").andExpect(status().isBadRequest());
        report(id, verificationId, friend2, "어제 올린 사진이랑 똑같아요").andExpect(status().isNoContent());
        report(id, verificationId, friend2, "한 번 더").andExpect(status().isConflict())
                .andExpect(jsonPath("$.code").value("ALREADY_REPORTED"));
        // 개설자도 신고할 수 있고, 검토 큐에는 한 줄만 올라간다
        report(id, verificationId, host, "운동 사진이 아니에요").andExpect(status().isNoContent());
        assertThat(jdbc.queryForObject("SELECT COUNT(*) FROM review_queue WHERE verification_id = ?", Integer.class,
                verificationId)).isEqualTo(1);

        list(id, friend2).andExpect(jsonPath("$[0].reported").value(true))
                .andExpect(jsonPath("$[0].status").value("APPROVED"));
        list(id, friend).andExpect(jsonPath("$[0].reported").value(false));

        long reviewId = jdbc.queryForObject("SELECT id FROM review_queue WHERE verification_id = ?", Long.class,
                verificationId);
        mvc.perform(get("/api/admin/reviews").param("status", "OPEN").header("Authorization", "Bearer " + admin))
                .andExpect(jsonPath("$[?(@.id == %d)].reason".formatted(reviewId)).value("REPORTED"))
                .andExpect(jsonPath("$[?(@.id == %d)].reports.length()".formatted(reviewId)).value(2))
                .andExpect(jsonPath("$[?(@.id == %d)].reports[0].reason".formatted(reviewId))
                        .value("어제 올린 사진이랑 똑같아요"));

        mvc.perform(post("/api/admin/reviews/" + reviewId + "/reject").header("Authorization", "Bearer " + admin)
                        .contentType(MediaType.APPLICATION_JSON).content("{}"))
                .andExpect(status().isNoContent());
        entityManager.clear();

        list(id, friend2).andExpect(jsonPath("$.length()").value(0));
        assertThat(jdbc.queryForObject(
                "SELECT COUNT(*) FROM reports WHERE verification_id = ? AND status = 'ACCEPTED'", Integer.class,
                verificationId)).isEqualTo(2);
        assertThat(jdbc.queryForObject("""
                SELECT COUNT(*) FROM notifications n JOIN reports r ON r.reporter_id = n.user_id
                WHERE r.verification_id = ? AND n.type = 'REPORT_RESULT'
                """, Integer.class, verificationId)).isEqualTo(2);
        // 이미 처리된 인증은 다시 신고할 수 없다 (거절된 인증은 목록에서도 사라진다)
        report(id, verificationId, friend2, "또").andExpect(status().isNotFound());
    }

    @Test
    @DisplayName("관리자가 승인하면 인증은 그대로이고 신고는 기각된다. 확인이 끝난 인증은 다시 신고할 수 없다")
    void reportDismissed() throws Exception {
        long id = challenge("FREE", "");
        join(id, friend);
        join(id, friend2);
        long verificationId = verify(id, friend);
        report(id, verificationId, friend2, "의심돼요").andExpect(status().isNoContent());

        long reviewId = jdbc.queryForObject("SELECT id FROM review_queue WHERE verification_id = ?", Long.class,
                verificationId);
        mvc.perform(post("/api/admin/reviews/" + reviewId + "/approve").header("Authorization", "Bearer " + admin)
                        .contentType(MediaType.APPLICATION_JSON).content("{}"))
                .andExpect(status().isNoContent());

        list(id, friend2).andExpect(jsonPath("$.length()").value(1));
        assertThat(jdbc.queryForObject("SELECT status FROM reports WHERE verification_id = ?", String.class,
                verificationId)).isEqualTo("DISMISSED");
        report(id, verificationId, host, "저도 의심돼요").andExpect(status().isConflict())
                .andExpect(jsonPath("$.code").value("REVIEW_CLOSED"));
    }

    @Test
    @DisplayName("그날 결과가 이미 기록된 인증은 신고할 수 없다")
    void cannotReportAfterDailyResult() throws Exception {
        long id = challenge("FREE", "");
        join(id, friend);
        join(id, friend2);
        long verificationId = verify(id, friend);
        jdbc.update("""
                INSERT INTO daily_settlements (challenge_id, period_start, period_end, success_count, fail_count,
                                               forfeited_pool, reward_share, distributed, settled_at)
                VALUES (?, ?, ?, 1, 1, 0, 0, 0, NOW())
                """, id, today, today);

        report(id, verificationId, friend2, "의심돼요").andExpect(status().isConflict())
                .andExpect(jsonPath("$.code").value("REVIEW_TOO_LATE"));
    }

    @Test
    @DisplayName("같은 두 사람이 작은 포인트 챌린지에서 세 번 함께하고 늘 한쪽만 실패하면 담합 의심으로 표시된다")
    void collusionFlag() throws Exception {
        charge(friend);
        charge(friend2);
        for (int i = 0; i < 3; i++) {
            long id = challenge("BET", ",\"entryFee\":3000");
            join(id, friend);
            join(id, friend2);
            verify(id, friend2); // friend 는 늘 인증하지 않아 실패한다
        }
        mvc.perform(get("/api/admin/collusion").header("Authorization", "Bearer " + friend))
                .andExpect(status().isForbidden());

        lifecycle.run(today.plusDays(1));

        String body = mvc.perform(get("/api/admin/collusion").param("status", "OPEN")
                        .header("Authorization", "Bearer " + admin))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.length()").value(1))
                .andExpect(jsonPath("$[0].coMatchCount").value(3))
                .andExpect(jsonPath("$[0].score").value(100.0))
                .andReturn().getResponse().getContentAsString();
        // 다시 돌아도 줄이 늘지 않는다
        lifecycle.run(today.plusDays(1));
        mvc.perform(get("/api/admin/collusion").header("Authorization", "Bearer " + admin))
                .andExpect(jsonPath("$.length()").value(1));

        mvc.perform(post("/api/admin/collusion/" + idOf(body) + "/dismiss").header("Authorization", "Bearer " + admin))
                .andExpect(status().isNoContent());
        mvc.perform(get("/api/admin/collusion").param("status", "OPEN").header("Authorization", "Bearer " + admin))
                .andExpect(jsonPath("$.length()").value(0));
    }

    // ---------- helpers ----------

    /** 오늘 하루짜리 챌린지 (extra: BET 이면 참가비) */
    private long challenge(String mode, String extra) throws Exception {
        String body = """
                {"categoryId":1,"title":"신고 테스트","description":"하루 인증","mode":"%s",
                 "startDate":"%s","endDate":"%s","frequencyType":"DAILY","maxParticipants":10%s}
                """.formatted(mode, today, "BET".equals(mode) ? today : today.plusDays(6), extra).replace("\n", "");
        return idOf(mvc.perform(post("/api/challenges").header("Authorization", "Bearer " + host)
                        .contentType(MediaType.APPLICATION_JSON).content(body))
                .andExpect(status().isCreated()).andReturn().getResponse().getContentAsString());
    }

    private void join(long id, String token) throws Exception {
        mvc.perform(post("/api/challenges/" + id + "/participants").header("Authorization", "Bearer " + token))
                .andExpect(status().isOk());
    }

    private long verify(long id, String token) throws Exception {
        return idOf(mvc.perform(multipart("/api/challenges/" + id + "/verifications")
                        .file(new MockMultipartFile("file", "camera.jpg", "image/jpeg", photo()))
                        .header("Authorization", "Bearer " + token))
                .andExpect(status().isCreated()).andReturn().getResponse().getContentAsString());
    }

    private ResultActions report(long id, long verificationId, String token, String reason) throws Exception {
        return mvc.perform(post("/api/challenges/" + id + "/verifications/" + verificationId + "/reports")
                .header("Authorization", "Bearer " + token).contentType(MediaType.APPLICATION_JSON)
                .content("{\"reason\":\"" + reason + "\"}"));
    }

    private ResultActions list(long id, String token) throws Exception {
        return mvc.perform(get("/api/challenges/" + id + "/verifications").header("Authorization", "Bearer " + token));
    }

    private void charge(String token) {
        testCharger.charge(token, 10_000);
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
        return userRepository.saveAndFlush(User.createWithPassword(suffix + "@example.com", "unused", "r" + suffix,
                "r".repeat(52) + suffix));
    }

    private String tokenOf(User user, String role) {
        return jwtProvider.createAccessToken(user.getId(), role);
    }

    private static long idOf(String body) {
        Matcher m = ID.matcher(body);
        assertThat(m.find()).isTrue();
        return Long.parseLong(m.group(1));
    }
}
