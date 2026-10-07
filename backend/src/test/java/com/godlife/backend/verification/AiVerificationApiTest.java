package com.godlife.backend.verification;

import com.godlife.backend.auth.JwtProvider;
import com.godlife.backend.user.User;
import com.godlife.backend.user.UserRepository;
import com.godlife.backend.verification.ai.AiClassifier;
import com.godlife.backend.verification.ai.AiClassifier.AiPrediction;
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
import org.springframework.test.context.bean.override.mockito.MockitoBean;
import org.springframework.test.web.servlet.MockMvc;
import org.springframework.test.web.servlet.ResultActions;
import org.springframework.transaction.annotation.Transactional;

import javax.imageio.ImageIO;
import java.awt.Color;
import java.awt.Graphics2D;
import java.awt.image.BufferedImage;
import java.io.ByteArrayOutputStream;
import java.io.IOException;
import java.nio.ByteBuffer;
import java.nio.ByteOrder;
import java.time.Clock;
import java.time.LocalDate;
import java.util.Optional;
import java.util.UUID;
import java.util.concurrent.ThreadLocalRandom;
import java.util.regex.Matcher;
import java.util.regex.Pattern;

import static org.assertj.core.api.Assertions.assertThat;
import static org.hamcrest.Matchers.containsString;
import static org.hamcrest.Matchers.hasItem;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.when;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.multipart;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.post;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.content;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.jsonPath;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

/**
 * AI 인증 판정 통합 테스트. ai-server 대신 가짜 분류기가 라벨 · 확신도 · 임베딩을 돌려준다.
 * (챌린지는 '운동' 카테고리 = 라벨 exercise)
 */
@SpringBootTest
@AutoConfigureMockMvc
@ActiveProfiles("test")
@Transactional
class AiVerificationApiTest {

    private static final Pattern ID = Pattern.compile("\"id\":(\\d+)");

    @Autowired MockMvc mvc;
    @Autowired UserRepository userRepository;
    @Autowired JwtProvider jwtProvider;
    @Autowired JdbcTemplate jdbc;
    @Autowired Clock clock;
    @Autowired EntityManager entityManager;
    @MockitoBean AiClassifier classifier;

    private String host;
    private String friend;
    private String friend2;
    private String admin;
    private LocalDate today;

    @BeforeEach
    void setUp() {
        host = tokenOf(newUser(), "USER");
        friend = tokenOf(newUser(), "USER");
        friend2 = tokenOf(newUser(), "USER");
        admin = tokenOf(newUser(), "ADMIN");
        today = LocalDate.now(clock);
    }

    @Test
    @DisplayName("카테고리와 같은 라벨로 확신하면 바로 인정되고, 판정 결과와 임베딩이 남는다")
    void autoPass() throws Exception {
        long id = challenge(1);
        join(id, friend);
        ai("exercise", 0.93, 1);

        long verificationId = idOf(submit(id, friend).andExpect(status().isCreated())
                .andExpect(jsonPath("$.status").value("APPROVED"))
                .andReturn().getResponse().getContentAsString());

        assertThat(jdbc.queryForObject("SELECT decision FROM ai_inference_results WHERE verification_id = ?",
                String.class, verificationId)).isEqualTo("AUTO_PASS");
        assertThat(jdbc.queryForObject("SELECT LENGTH(embedding) FROM image_embeddings WHERE verification_id = ?",
                Integer.class, verificationId)).isEqualTo(1280 * 4);
        reviews("OPEN").andExpect(jsonPath("$[?(@.verificationId == %d)]".formatted(verificationId)).isEmpty());
    }

    @Test
    @DisplayName("다른 카테고리라고 강하게 확신하면 저장하지 않고 돌려보내고, 바로 다시 찍어 올릴 수 있다")
    void autoRejectThenRetake() throws Exception {
        long id = challenge(1);
        join(id, friend);

        ai("cooking", 0.97, 1);
        submit(id, friend).andExpect(status().isBadRequest())
                .andExpect(jsonPath("$.code").value("VERIFICATION_REJECTED"));
        me(id, friend).andExpect(jsonPath("$.state").value("OPEN"))
                .andExpect(jsonPath("$.successDays").value(0));

        ai("exercise", 0.88, 2);
        submit(id, friend).andExpect(status().isCreated()).andExpect(jsonPath("$.status").value("APPROVED"));
        me(id, friend).andExpect(jsonPath("$.state").value("DONE_TODAY"))
                .andExpect(jsonPath("$.successDays").value(1));
    }

    @Test
    @DisplayName("기타 챌린지에 세부 종류를 고르면 그 라벨로 판정한다: 같은 라벨은 인정, 다른 라벨로 확신하면 돌려보낸다")
    void subTypeLabelDecides() throws Exception {
        long id = challenge(5, 2); // 산책 = walk
        join(id, friend);

        ai("wake_up", 0.95, 1);
        submit(id, friend).andExpect(status().isBadRequest())
                .andExpect(jsonPath("$.code").value("VERIFICATION_REJECTED"))
                .andExpect(jsonPath("$.message").value(containsString("산책")));

        ai("walk", 0.9, 2);
        long verificationId = idOf(submit(id, friend).andExpect(status().isCreated())
                .andExpect(jsonPath("$.status").value("APPROVED")).andReturn().getResponse().getContentAsString());
        assertThat(jdbc.queryForObject("SELECT category_match FROM ai_inference_results WHERE verification_id = ?",
                Boolean.class, verificationId)).isTrue();
    }

    @Test
    @DisplayName("세부 종류를 골랐는데 AI 가 애매하게 다른 라벨을 내면 인증은 받고 관리자 검토로 넘긴다 (검토 화면에는 세부 종류가 보인다)")
    void subTypeMismatchGoesToReview() throws Exception {
        long id = challenge(5, 1); // 일찍 일어나기 = wake_up
        join(id, friend);
        ai("other", 0.55, 1);

        long verificationId = idOf(submit(id, friend).andExpect(status().isCreated())
                .andExpect(jsonPath("$.status").value("IN_REVIEW")).andReturn().getResponse().getContentAsString());

        reviews("OPEN").andExpect(jsonPath("$[?(@.verificationId == %d)].expectedLabel".formatted(verificationId))
                        .value(hasItem("wake_up")))
                .andExpect(jsonPath("$[?(@.verificationId == %d)].categoryName".formatted(verificationId))
                        .value(hasItem("일찍 일어나기")));
    }

    @Test
    @DisplayName("세부 종류를 고르지 않은 기타 챌린지는 예전처럼 어떤 라벨이든 인정한다")
    void otherWithoutSubTypeAcceptsAnyLabel() throws Exception {
        long id = challenge(5);
        join(id, friend);
        ai("cooking", 0.97, 1);

        submit(id, friend).andExpect(status().isCreated()).andExpect(jsonPath("$.status").value("APPROVED"));
    }

    @Test
    @DisplayName("모델을 다시 학습하면 벡터가 달라지므로, 다른 모델 버전이 남긴 사진과는 재사용 비교를 하지 않는다")
    void duplicateCheckIgnoresOtherModelVersions() throws Exception {
        long id = challenge(1);
        join(id, friend);
        join(id, friend2);
        ai("exercise", 0.93, 1);
        long first = idOf(submit(id, friend).andExpect(status().isCreated())
                .andReturn().getResponse().getContentAsString());
        jdbc.update("UPDATE image_embeddings SET model_version = 'old-model' WHERE verification_id = ?", first);

        submit(id, friend2).andExpect(status().isCreated()).andExpect(jsonPath("$.status").value("APPROVED"));
    }

    @Test
    @DisplayName("AI 가 여러 번 거절하면 그 뒤에는 거절하지 않고 관리자 검토로 넘긴다")
    void tooManyRejectsGoToReview() throws Exception {
        long id = challenge(1);
        join(id, friend);
        ai("cooking", 0.97, 1);

        for (int i = 0; i < 5; i++) {
            submit(id, friend).andExpect(status().isBadRequest());
        }
        submit(id, friend).andExpect(status().isCreated()).andExpect(jsonPath("$.status").value("IN_REVIEW"));
    }

    @Test
    @DisplayName("애매하면 일단 인증으로 받고 검토로 넘긴다. 관리자가 거절하면 인증이 취소되고 알림이 간다")
    void lowConfidenceThenAdminRejects() throws Exception {
        long id = challenge(1);
        join(id, friend);
        join(id, friend2);
        ai("exercise", 0.41, 1);

        long verificationId = idOf(submit(id, friend).andExpect(status().isCreated())
                .andExpect(jsonPath("$.status").value("IN_REVIEW"))
                .andReturn().getResponse().getContentAsString());
        me(id, friend).andExpect(jsonPath("$.successDays").value(1));

        reviews("OPEN", friend).andExpect(status().isForbidden());
        long reviewId = reviewIdOf(verificationId);
        reviews("OPEN").andExpect(status().isOk())
                .andExpect(jsonPath("$[?(@.id == %d)].reason".formatted(reviewId)).value("LOW_CONFIDENCE"))
                .andExpect(jsonPath("$[?(@.id == %d)].predictedLabel".formatted(reviewId)).value("exercise"));
        mvc.perform(get("/api/admin/reviews/images/" + verificationId).header("Authorization", "Bearer " + admin))
                .andExpect(status().isOk()).andExpect(content().contentType(MediaType.IMAGE_JPEG));
        mvc.perform(get("/api/admin/reviews/images/" + verificationId).header("Authorization", "Bearer " + friend))
                .andExpect(status().isForbidden());

        decide(reviewId, "reject", "{\"memo\":\"운동 사진이 아니에요\"}").andExpect(status().isNoContent());
        decide(reviewId, "reject", "{}").andExpect(status().isConflict())
                .andExpect(jsonPath("$.code").value("REVIEW_CLOSED"));
        // 테스트는 한 트랜잭션이라, 검토가 SQL 로 바꾼 참가자 값을 다시 읽게 한다
        entityManager.clear();

        me(id, friend).andExpect(jsonPath("$.successDays").value(0))
                .andExpect(jsonPath("$.currentStreak").value(0));
        mvc.perform(get("/api/challenges/" + id + "/verifications").header("Authorization", "Bearer " + friend2))
                .andExpect(jsonPath("$.length()").value(0));
        assertThat(jdbc.queryForObject("""
                SELECT COUNT(*) FROM notifications n JOIN challenge_participants p ON p.user_id = n.user_id
                  JOIN verifications v ON v.participant_id = p.id
                WHERE v.id = ? AND n.type = 'VERIFY_REJECTED'
                """, Integer.class, verificationId)).isEqualTo(1);
    }

    @Test
    @DisplayName("예전 사진과 거의 같으면 재사용 의심으로 검토에 올리고, 관리자가 승인하면 인정된다")
    void duplicateSuspectThenAdminApproves() throws Exception {
        long id = challenge(1);
        join(id, friend);
        join(id, friend2);

        ai("exercise", 0.95, 7);
        long first = idOf(submit(id, friend).andExpect(jsonPath("$.status").value("APPROVED"))
                .andReturn().getResponse().getContentAsString());
        // 파일은 다르지만(해시가 다름) 모델이 본 모습은 같은 사진
        long second = idOf(submit(id, friend2).andExpect(status().isCreated())
                .andExpect(jsonPath("$.status").value("IN_REVIEW"))
                .andReturn().getResponse().getContentAsString());

        long reviewId = reviewIdOf(second);
        reviews("OPEN")
                .andExpect(jsonPath("$[?(@.id == %d)].reason".formatted(reviewId)).value("DUPLICATE_SUSPECT"))
                .andExpect(jsonPath("$[?(@.id == %d)].duplicateOfId".formatted(reviewId)).value((int) first));

        decide(reviewId, "approve", "{}").andExpect(status().isNoContent());
        mvc.perform(get("/api/challenges/" + id + "/verifications").header("Authorization", "Bearer " + friend))
                .andExpect(jsonPath("$.length()").value(2))
                .andExpect(jsonPath("$[1].status").value("APPROVED"));
        me(id, friend2).andExpect(jsonPath("$.successDays").value(1));
    }

    @Test
    @DisplayName("그날 결과가 이미 기록된 인증은 거절할 수 없다 (승인만)")
    void cannotRejectAfterDailyResult() throws Exception {
        long id = challenge(1);
        join(id, friend);
        ai("exercise", 0.3, 1);
        long verificationId = idOf(submit(id, friend).andReturn().getResponse().getContentAsString());
        jdbc.update("""
                INSERT INTO daily_settlements (challenge_id, period_start, period_end, success_count, fail_count,
                                               forfeited_pool, reward_share, distributed, settled_at)
                VALUES (?, ?, ?, 1, 0, 0, 0, 0, NOW())
                """, id, today, today);

        long reviewId = reviewIdOf(verificationId);
        decide(reviewId, "reject", "{}").andExpect(status().isConflict())
                .andExpect(jsonPath("$.code").value("REVIEW_TOO_LATE"));
        decide(reviewId, "approve", "{}").andExpect(status().isNoContent());
    }

    @Test
    @DisplayName("'기타' 카테고리는 라벨이 달라도 거절하지 않는다")
    void otherCategoryIsNotFiltered() throws Exception {
        long id = challenge(5);
        join(id, friend);
        ai("exercise", 0.99, 1);

        submit(id, friend).andExpect(status().isCreated()).andExpect(jsonPath("$.status").value("APPROVED"));
    }

    @Test
    @DisplayName("AI 서버가 꺼져 있으면 판정 없이 바로 인정된다")
    void aiUnavailable() throws Exception {
        long id = challenge(1);
        join(id, friend);
        when(classifier.classify(any())).thenReturn(Optional.empty());

        long verificationId = idOf(submit(id, friend).andExpect(status().isCreated())
                .andExpect(jsonPath("$.status").value("APPROVED"))
                .andReturn().getResponse().getContentAsString());
        assertThat(jdbc.queryForObject("SELECT COUNT(*) FROM ai_inference_results WHERE verification_id = ?",
                Integer.class, verificationId)).isZero();
    }

    // ---------- helpers ----------

    /** 가짜 분류 결과. 임베딩은 axis 번째만 1인 단위 벡터 (같은 axis = 같은 사진, 다른 axis = 전혀 다른 사진) */
    private void ai(String label, double confidence, int axis) {
        ByteBuffer buffer = ByteBuffer.allocate(1280 * Float.BYTES).order(ByteOrder.LITTLE_ENDIAN);
        buffer.putFloat(axis * Float.BYTES, 1f);
        when(classifier.classify(any()))
                .thenReturn(Optional.of(new AiPrediction(label, confidence, buffer.array(), "test-1")));
    }

    private long challenge(int categoryId) throws Exception {
        return challenge(categoryId, null);
    }

    /** subTypeId: '기타'(5)의 세부 종류 — 1 일찍 일어나기(wake_up) · 2 산책(walk). null 이면 고르지 않음 */
    private long challenge(int categoryId, Integer subTypeId) throws Exception {
        String body = """
                {"categoryId":%d,"subTypeId":%s,"title":"매일 챌린지","description":"하루 30분","mode":"FREE",
                 "startDate":"%s","endDate":"%s","frequencyType":"DAILY","maxParticipants":10}
                """.formatted(categoryId, subTypeId, today, today.plusDays(6)).replace("\n", "");
        return idOf(mvc.perform(post("/api/challenges").header("Authorization", "Bearer " + host)
                        .contentType(MediaType.APPLICATION_JSON).content(body))
                .andExpect(status().isCreated()).andReturn().getResponse().getContentAsString());
    }

    private void join(long id, String token) throws Exception {
        mvc.perform(post("/api/challenges/" + id + "/participants").header("Authorization", "Bearer " + token))
                .andExpect(status().isOk());
    }

    private ResultActions submit(long id, String token) throws Exception {
        return mvc.perform(multipart("/api/challenges/" + id + "/verifications")
                .file(new MockMultipartFile("file", "camera.jpg", "image/jpeg", photo()))
                .header("Authorization", "Bearer " + token));
    }

    private ResultActions me(long id, String token) throws Exception {
        return mvc.perform(get("/api/challenges/" + id + "/verifications/me").header("Authorization", "Bearer " + token));
    }

    private ResultActions reviews(String status) throws Exception {
        return reviews(status, admin);
    }

    private ResultActions reviews(String status, String token) throws Exception {
        return mvc.perform(get("/api/admin/reviews").param("status", status).header("Authorization", "Bearer " + token));
    }

    private ResultActions decide(long reviewId, String action, String body) throws Exception {
        return mvc.perform(post("/api/admin/reviews/" + reviewId + "/" + action)
                .header("Authorization", "Bearer " + admin).contentType(MediaType.APPLICATION_JSON).content(body));
    }

    private long reviewIdOf(long verificationId) {
        return jdbc.queryForObject("SELECT id FROM review_queue WHERE verification_id = ?", Long.class, verificationId);
    }

    /** 매번 다른 색이라 파일 내용(해시)이 겹치지 않는 사진 */
    private static byte[] photo() throws IOException {
        BufferedImage image = new BufferedImage(120, 90, BufferedImage.TYPE_INT_RGB);
        Graphics2D g = image.createGraphics();
        g.setColor(new Color(ThreadLocalRandom.current().nextInt(0xFFFFFF)));
        g.fillRect(0, 0, 120, 90);
        g.dispose();
        ByteArrayOutputStream out = new ByteArrayOutputStream();
        ImageIO.write(image, "png", out);
        return out.toByteArray();
    }

    private User newUser() {
        String suffix = UUID.randomUUID().toString().replace("-", "").substring(0, 12);
        return userRepository.saveAndFlush(User.createWithPassword(suffix + "@example.com", "unused", "a" + suffix,
                "a".repeat(52) + suffix));
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
