package com.godlife.backend.challenge;

import com.godlife.backend.auth.JwtProvider;
import com.godlife.backend.user.User;
import com.godlife.backend.user.UserRepository;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.boot.webmvc.test.autoconfigure.AutoConfigureMockMvc;
import org.springframework.http.MediaType;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.test.context.ActiveProfiles;
import org.springframework.test.web.servlet.MockMvc;
import org.springframework.transaction.annotation.Transactional;

import java.time.Clock;
import java.time.LocalDate;
import java.util.UUID;
import java.util.regex.Matcher;
import java.util.regex.Pattern;

import static org.assertj.core.api.Assertions.assertThat;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.delete;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.post;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.jsonPath;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

/** 챌린지 삭제 통합 테스트. godlife_test DB, 각 테스트는 롤백된다. */
@SpringBootTest
@AutoConfigureMockMvc
@ActiveProfiles("test")
@Transactional
class ChallengeDeleteTest {

    private static final Pattern ID = Pattern.compile("\"id\":(\\d+)");

    @Autowired MockMvc mvc;
    @Autowired UserRepository userRepository;
    @Autowired JwtProvider jwtProvider;
    @Autowired JdbcTemplate jdbc;
    @Autowired Clock clock;

    private String host;
    private String friend;
    private String stranger;
    private LocalDate today;

    @BeforeEach
    void setUp() {
        host = tokenOf(newUser());
        friend = tokenOf(newUser());
        stranger = tokenOf(newUser());
        today = LocalDate.now(clock);
    }

    @Test
    @DisplayName("개설자는 시작 전에 삭제할 수 있고, 참가 기록과 채팅도 함께 지워진다")
    void hostDeletesBeforeStart() throws Exception {
        long id = create(today.plusDays(3), "PUBLIC");
        mvc.perform(post("/api/challenges/" + id + "/participants").header("Authorization", "Bearer " + friend))
                .andExpect(status().isOk());
        mvc.perform(post("/api/challenges/" + id + "/messages").header("Authorization", "Bearer " + friend)
                .contentType(MediaType.APPLICATION_JSON).content("{\"content\":\"안녕\"}")).andExpect(status().isCreated());

        mvc.perform(delete("/api/challenges/" + id).header("Authorization", "Bearer " + host))
                .andExpect(status().isNoContent());

        mvc.perform(get("/api/challenges/" + id)).andExpect(status().isNotFound());
        assertThat(count("challenge_participants", id)).isZero();
        assertThat(count("chat_messages", id)).isZero();
    }

    @Test
    @DisplayName("개설자가 아니면 403, 비공개 챌린지를 모르는 사람이면 404")
    void onlyHost() throws Exception {
        long publicId = create(today.plusDays(3), "PUBLIC");
        mvc.perform(post("/api/challenges/" + publicId + "/participants").header("Authorization", "Bearer " + friend))
                .andExpect(status().isOk());
        mvc.perform(delete("/api/challenges/" + publicId).header("Authorization", "Bearer " + friend))
                .andExpect(status().isForbidden());
        mvc.perform(delete("/api/challenges/" + publicId).header("Authorization", "Bearer " + stranger))
                .andExpect(status().isForbidden());

        long privateId = create(today.plusDays(3), "PRIVATE");
        mvc.perform(delete("/api/challenges/" + privateId).header("Authorization", "Bearer " + stranger))
                .andExpect(status().isNotFound());
        mvc.perform(delete("/api/challenges/" + privateId)).andExpect(status().isUnauthorized());
    }

    @Test
    @DisplayName("시작일 당일부터는 삭제할 수 없다")
    void notAfterStart() throws Exception {
        long id = create(today, "PUBLIC");
        mvc.perform(delete("/api/challenges/" + id).header("Authorization", "Bearer " + host))
                .andExpect(status().isConflict())
                .andExpect(jsonPath("$.code").value("CHALLENGE_CANNOT_DELETE"));
    }

    // ---------- helpers ----------

    private long create(LocalDate start, String visibility) throws Exception {
        String body = """
                {"categoryId":2,"title":"삭제 테스트","description":"곧 지울 챌린지","mode":"FREE","visibility":"%s",
                 "startDate":"%s","endDate":"%s","frequencyType":"DAILY","maxParticipants":10}
                """.formatted(visibility, start, start.plusDays(6)).replace("\n", "");
        String created = mvc.perform(post("/api/challenges").header("Authorization", "Bearer " + host)
                        .contentType(MediaType.APPLICATION_JSON).content(body))
                .andExpect(status().isCreated()).andReturn().getResponse().getContentAsString();
        Matcher m = ID.matcher(created);
        assertThat(m.find()).isTrue();
        return Long.parseLong(m.group(1));
    }

    private int count(String table, long challengeId) {
        return jdbc.queryForObject("SELECT COUNT(*) FROM " + table + " WHERE challenge_id = ?", Integer.class,
                challengeId);
    }

    private User newUser() {
        String suffix = UUID.randomUUID().toString().replace("-", "").substring(0, 12);
        return userRepository.saveAndFlush(User.createWithPassword(suffix + "@example.com", "unused", "d" + suffix,
                "d".repeat(52) + suffix));
    }

    private String tokenOf(User user) {
        return jwtProvider.createAccessToken(user.getId(), "USER");
    }
}
