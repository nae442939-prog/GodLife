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
import org.springframework.test.context.ActiveProfiles;
import org.springframework.test.web.servlet.MockMvc;
import org.springframework.test.web.servlet.ResultActions;
import org.springframework.transaction.annotation.Transactional;

import java.time.Clock;
import java.time.LocalDate;
import java.util.Locale;
import java.util.UUID;
import java.util.regex.Matcher;
import java.util.regex.Pattern;

import static org.assertj.core.api.Assertions.assertThat;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.post;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.jsonPath;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

/** 비공개 챌린지(초대 코드) 통합 테스트. godlife_test DB, 각 테스트는 롤백된다. */
@SpringBootTest
@AutoConfigureMockMvc
@ActiveProfiles("test")
@Transactional
class PrivateChallengeTest {

    private static final Pattern ID = Pattern.compile("\"id\":(\\d+)");
    private static final Pattern CODE = Pattern.compile("\"inviteCode\":\"([A-Z0-9]{8})\"");

    @Autowired MockMvc mvc;
    @Autowired UserRepository userRepository;
    @Autowired JwtProvider jwtProvider;
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
    @DisplayName("비공개 챌린지는 목록에 안 나오고, 모르는 사람이 id 로 보거나 참여하면 404")
    void hiddenFromListAndById() throws Exception {
        String tag = UUID.randomUUID().toString().substring(0, 8);
        String body = create(host, "PRIVATE", "비밀 " + tag).andExpect(status().isCreated())
                .andExpect(jsonPath("$.visibility").value("PRIVATE"))
                .andReturn().getResponse().getContentAsString();
        long id = find(ID, body);

        mvc.perform(get("/api/challenges").param("q", tag)).andExpect(jsonPath("$.totalElements").value(0));
        mvc.perform(get("/api/challenges/" + id)).andExpect(status().isNotFound());
        mvc.perform(get("/api/challenges/" + id).header("Authorization", "Bearer " + stranger))
                .andExpect(status().isNotFound());
        mvc.perform(post("/api/challenges/" + id + "/participants").header("Authorization", "Bearer " + stranger))
                .andExpect(status().isNotFound());
        // 개설자는 id 로 볼 수 있다
        mvc.perform(get("/api/challenges/" + id).header("Authorization", "Bearer " + host))
                .andExpect(status().isOk()).andExpect(jsonPath("$.host").value(true));
    }

    @Test
    @DisplayName("초대 코드로 미리보기 → 참여하면 id 로도 보이고, 참가자도 초대 코드를 받는다")
    void joinByInvite() throws Exception {
        String body = create(host, "PRIVATE", "친구들끼리").andReturn().getResponse().getContentAsString();
        long id = find(ID, body);
        String code = findCode(body);

        // 비로그인 미리보기: 코드가 곧 열쇠라 보이지만, 초대 코드 자체는 돌려주지 않는다
        mvc.perform(get("/api/challenges/invite/" + code)).andExpect(status().isOk())
                .andExpect(jsonPath("$.id").value(id))
                .andExpect(jsonPath("$.inviteCode").doesNotExist());
        // 소문자로 옮겨 적어도 같은 코드
        mvc.perform(get("/api/challenges/invite/" + code.toLowerCase(Locale.ROOT))).andExpect(status().isOk());

        joinByCode(friend, code).andExpect(status().isOk())
                .andExpect(jsonPath("$.joined").value(true))
                .andExpect(jsonPath("$.inviteCode").value(code));
        mvc.perform(get("/api/challenges/" + id).header("Authorization", "Bearer " + friend))
                .andExpect(status().isOk()).andExpect(jsonPath("$.inviteCode").value(code));

        joinByCode(friend, code).andExpect(status().isConflict()).andExpect(jsonPath("$.code").value("ALREADY_JOINED"));
        mvc.perform(get("/api/challenges/invite/NOPE2345")).andExpect(status().isNotFound())
                .andExpect(jsonPath("$.code").value("INVITE_NOT_FOUND"));
    }

    @Test
    @DisplayName("공개 챌린지도 초대 코드가 있다. 모르는 사람에게는 코드를 주지 않는다")
    void publicHasCode() throws Exception {
        String body = create(host, null, "공개").andExpect(jsonPath("$.visibility").value("PUBLIC"))
                .andReturn().getResponse().getContentAsString();
        long id = find(ID, body);
        String code = findCode(body);

        mvc.perform(get("/api/challenges/" + id).header("Authorization", "Bearer " + stranger))
                .andExpect(status().isOk()).andExpect(jsonPath("$.inviteCode").doesNotExist());
        joinByCode(stranger, code).andExpect(status().isOk());
    }

    @Test
    @DisplayName("초대 코드 재발급은 개설자만. 바꾸면 이전 링크는 404")
    void regenerate() throws Exception {
        String body = create(host, "PRIVATE", "재발급").andReturn().getResponse().getContentAsString();
        long id = find(ID, body);
        String oldCode = findCode(body);
        joinByCode(friend, oldCode).andExpect(status().isOk());

        mvc.perform(post("/api/challenges/" + id + "/invite-code").header("Authorization", "Bearer " + friend))
                .andExpect(status().isForbidden());
        mvc.perform(post("/api/challenges/" + id + "/invite-code").header("Authorization", "Bearer " + stranger))
                .andExpect(status().isNotFound());

        String newBody = mvc.perform(post("/api/challenges/" + id + "/invite-code")
                        .header("Authorization", "Bearer " + host))
                .andExpect(status().isOk()).andReturn().getResponse().getContentAsString();
        assertThat(findCode(newBody)).isNotEqualTo(oldCode);
        mvc.perform(get("/api/challenges/invite/" + oldCode)).andExpect(status().isNotFound());
    }

    // ---------- helpers ----------

    private User newUser() {
        String suffix = UUID.randomUUID().toString().replace("-", "").substring(0, 12);
        return userRepository.saveAndFlush(User.createWithPassword(suffix + "@example.com", "unused", "p" + suffix,
                "p".repeat(52) + suffix));
    }

    private String tokenOf(User user) {
        return jwtProvider.createAccessToken(user.getId(), "USER");
    }

    private ResultActions create(String token, String visibility, String title) throws Exception {
        String body = """
                {"categoryId":1,"title":"%s","description":"친구들과 함께","mode":"FREE",%s
                 "startDate":"%s","endDate":"%s","frequencyType":"DAILY","maxParticipants":10}
                """.formatted(title, visibility == null ? "" : "\"visibility\":\"" + visibility + "\",",
                today.plusDays(1), today.plusDays(7)).replace("\n", "");
        return mvc.perform(post("/api/challenges").header("Authorization", "Bearer " + token)
                .contentType(MediaType.APPLICATION_JSON).content(body));
    }

    private ResultActions joinByCode(String token, String code) throws Exception {
        return mvc.perform(post("/api/challenges/invite/" + code + "/participants")
                .header("Authorization", "Bearer " + token));
    }

    private static long find(Pattern pattern, String body) {
        Matcher m = pattern.matcher(body);
        assertThat(m.find()).isTrue();
        return Long.parseLong(m.group(1));
    }

    private static String findCode(String body) {
        Matcher m = CODE.matcher(body);
        assertThat(m.find()).isTrue();
        return m.group(1);
    }
}
