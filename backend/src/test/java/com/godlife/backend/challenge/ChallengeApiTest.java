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
import java.util.UUID;
import java.util.regex.Matcher;
import java.util.regex.Pattern;

import static org.assertj.core.api.Assertions.assertThat;
import static org.hamcrest.Matchers.hasItem;
import static org.hamcrest.Matchers.not;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.delete;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.post;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.jsonPath;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

/** 챌린지 개설/탐색/상세/참여/취소 통합 테스트. godlife_test DB, 각 테스트는 롤백된다. */
@SpringBootTest
@AutoConfigureMockMvc
@ActiveProfiles("test")
@Transactional
class ChallengeApiTest {

    private static final Pattern ID = Pattern.compile("^\\{\"id\":(\\d+)");

    @Autowired MockMvc mvc;
    @Autowired UserRepository userRepository;
    @Autowired JwtProvider jwtProvider;
    @Autowired Clock clock;

    private String host;
    private String other;
    private LocalDate today;

    @BeforeEach
    void setUp() {
        host = tokenOf(newUser(true));
        other = tokenOf(newUser(true));
        today = LocalDate.now(clock);
    }

    // ---------- 개설 ----------

    @Test
    @DisplayName("무료 챌린지를 만들면 201 과 상세를 돌려준다. 참가 포인트는 0 으로 저장된다")
    void createFree() throws Exception {
        create(host, freeBody("아침 러닝", today.plusDays(1), today.plusDays(14)).replace("\"entryFee\":null", "\"entryFee\":5000"))
                .andExpect(status().isCreated())
                .andExpect(jsonPath("$.mode").value("FREE"))
                .andExpect(jsonPath("$.entryFee").value(0))
                .andExpect(jsonPath("$.totalDays").value(14))
                .andExpect(jsonPath("$.category.name").value("운동"))
                .andExpect(jsonPath("$.host").value(true))
                .andExpect(jsonPath("$.joined").value(false))
                .andExpect(jsonPath("$.participantCount").value(0));
    }

    @Test
    @DisplayName("포인트 챌린지는 참가 포인트가 100P 단위, 100 ~ 100,000P 여야 한다")
    void createBetEntryFee() throws Exception {
        create(host, betBody("독서 챌린지", 150)).andExpect(status().isBadRequest())
                .andExpect(jsonPath("$.fieldErrors.entryFeeValid").exists());
        create(host, betBody("독서 챌린지", 0)).andExpect(status().isBadRequest());
        create(host, betBody("독서 챌린지", 100_100)).andExpect(status().isBadRequest());
        create(host, betBody("독서 챌린지", 3000)).andExpect(status().isCreated())
                .andExpect(jsonPath("$.mode").value("BET"))
                .andExpect(jsonPath("$.entryFee").value(3000));
    }

    @Test
    @DisplayName("기간 90일 초과, 지난 시작일, 주 N회 누락, 인증 시간대 한쪽만 입력은 거절한다")
    void createValidation() throws Exception {
        create(host, freeBody("긴 챌린지", today, today.plusDays(90))).andExpect(status().isBadRequest())
                .andExpect(jsonPath("$.fieldErrors.endDateValid").exists());
        create(host, freeBody("지난 챌린지", today.minusDays(1), today.plusDays(5))).andExpect(status().isBadRequest());
        create(host, freeBody("주간", today, today.plusDays(6)).replace("\"DAILY\"", "\"WEEKLY_N\""))
                .andExpect(status().isBadRequest())
                .andExpect(jsonPath("$.fieldErrors.weeklyCountValid").exists());
        create(host, freeBody("새벽", today, today.plusDays(6)).replace("\"verifyFrom\":null", "\"verifyFrom\":\"05:00\""))
                .andExpect(status().isBadRequest())
                .andExpect(jsonPath("$.fieldErrors.verifyWindowValid").exists());
        create(host, freeBody("", today, today.plusDays(6))).andExpect(status().isBadRequest())
                .andExpect(jsonPath("$.fieldErrors.title").exists());
    }

    @Test
    @DisplayName("비로그인은 만들 수 없고(401), 휴대폰 인증 안 한 회원도 만들 수 없다(403)")
    void createRequiresPhone() throws Exception {
        String body = freeBody("테스트", today, today.plusDays(3));
        mvc.perform(post("/api/challenges").contentType(MediaType.APPLICATION_JSON).content(body))
                .andExpect(status().isUnauthorized());
        create(tokenOf(newUser(false)), body).andExpect(status().isForbidden())
                .andExpect(jsonPath("$.code").value("PHONE_NOT_REGISTERED"));
    }

    // ---------- 탐색 / 상세 ----------

    @Test
    @DisplayName("목록과 상세, 카테고리는 비로그인도 본다. 모드·검색어로 거르고, 검색어의 % 는 글자 그대로 찾는다")
    void searchAndDetailArePublic() throws Exception {
        String tag = UUID.randomUUID().toString().substring(0, 8);
        long freeId = idOf(create(host, freeBody("무료 " + tag, today.plusDays(1), today.plusDays(7))));
        idOf(create(host, betBody("포인트 " + tag, 1000)));

        mvc.perform(get("/api/categories")).andExpect(status().isOk())
                .andExpect(jsonPath("$[*].name").value(hasItem("공부")));

        mvc.perform(get("/api/challenges").param("q", tag)).andExpect(status().isOk())
                .andExpect(jsonPath("$.totalElements").value(2));
        mvc.perform(get("/api/challenges").param("q", tag).param("mode", "FREE"))
                .andExpect(jsonPath("$.totalElements").value(1))
                .andExpect(jsonPath("$.items[0].id").value(freeId));
        mvc.perform(get("/api/challenges").param("q", "%")).andExpect(status().isOk())
                .andExpect(jsonPath("$.items[*].id").value(not(hasItem((int) freeId))));
        mvc.perform(get("/api/challenges").param("mode", "NOPE")).andExpect(status().isBadRequest());

        mvc.perform(get("/api/challenges/" + freeId)).andExpect(status().isOk())
                .andExpect(jsonPath("$.joined").value(false))
                .andExpect(jsonPath("$.host").value(false))
                .andExpect(jsonPath("$.recruiting").value(true));
        mvc.perform(get("/api/challenges/999999999")).andExpect(status().isNotFound());
    }

    // ---------- 참여 / 취소 ----------

    @Test
    @DisplayName("무료 챌린지 참여 → 중복 참여 거절 → 취소 → 다시 참여")
    void joinLeaveRejoin() throws Exception {
        long id = idOf(create(host, freeBody("독서", today.plusDays(2), today.plusDays(10))));

        join(other, id).andExpect(status().isOk())
                .andExpect(jsonPath("$.joined").value(true))
                .andExpect(jsonPath("$.participantCount").value(1))
                .andExpect(jsonPath("$.participants[0].nickname").exists());
        join(other, id).andExpect(status().isConflict()).andExpect(jsonPath("$.code").value("ALREADY_JOINED"));

        leave(other, id).andExpect(status().isOk())
                .andExpect(jsonPath("$.joined").value(false))
                .andExpect(jsonPath("$.participantCount").value(0));
        leave(other, id).andExpect(status().isConflict()).andExpect(jsonPath("$.code").value("NOT_JOINED"));

        join(other, id).andExpect(status().isOk()).andExpect(jsonPath("$.participantCount").value(1));
    }

    @Test
    @DisplayName("정원이 차면 더 참여할 수 없다")
    void full() throws Exception {
        long id = idOf(create(host, freeBody("2인", today.plusDays(1), today.plusDays(3))
                .replace("\"maxParticipants\":20", "\"maxParticipants\":2")));
        join(host, id).andExpect(status().isOk());
        join(other, id).andExpect(status().isOk());
        join(tokenOf(newUser(true)), id).andExpect(status().isConflict())
                .andExpect(jsonPath("$.code").value("CHALLENGE_FULL"));
    }

    @Test
    @DisplayName("시작일 당일에는 참여할 수 있지만 취소는 할 수 없다")
    void startDay() throws Exception {
        long id = idOf(create(host, freeBody("오늘 시작", today, today.plusDays(3))));
        join(other, id).andExpect(status().isOk()).andExpect(jsonPath("$.canLeave").value(false));
        leave(other, id).andExpect(status().isConflict())
                .andExpect(jsonPath("$.code").value("CHALLENGE_ALREADY_STARTED"));
    }

    @Test
    @DisplayName("포인트 챌린지는 충전 포인트가 모자라면 참여할 수 없다 (자세한 건 WalletApiTest)")
    void betJoinNeedsPoints() throws Exception {
        long id = idOf(create(host, betBody("포인트", 1000)));
        join(other, id).andExpect(status().isConflict())
                .andExpect(jsonPath("$.code").value("INSUFFICIENT_POINTS"));
    }

    // ---------- helpers ----------

    private User newUser(boolean withPhone) {
        String suffix = UUID.randomUUID().toString().replace("-", "").substring(0, 12);
        User user = withPhone
                ? User.createWithPassword(suffix + "@example.com", "unused", "u" + suffix, "h".repeat(52) + suffix)
                : User.createSocial(suffix + "@example.com", "s" + suffix);
        return userRepository.saveAndFlush(user);
    }

    private String tokenOf(User user) {
        return jwtProvider.createAccessToken(user.getId(), "USER");
    }

    private String freeBody(String title, LocalDate start, LocalDate end) {
        return """
                {"categoryId":1,"title":"%s","description":"매일 인증해요","mode":"FREE",
                 "startDate":"%s","endDate":"%s","frequencyType":"DAILY","weeklyCount":null,
                 "entryFee":null,"maxParticipants":20,"verifyFrom":null,"verifyUntil":null,"partialRefund":false}
                """.formatted(title, start, end).replace("\n", "");
    }

    private String betBody(String title, long entryFee) {
        return freeBody(title, today.plusDays(1), today.plusDays(7))
                .replace("\"FREE\"", "\"BET\"")
                .replace("\"entryFee\":null", "\"entryFee\":" + entryFee);
    }

    private ResultActions create(String token, String body) throws Exception {
        return mvc.perform(post("/api/challenges").header("Authorization", "Bearer " + token)
                .contentType(MediaType.APPLICATION_JSON).content(body));
    }

    private ResultActions join(String token, long id) throws Exception {
        return mvc.perform(post("/api/challenges/" + id + "/participants").header("Authorization", "Bearer " + token));
    }

    private ResultActions leave(String token, long id) throws Exception {
        return mvc.perform(delete("/api/challenges/" + id + "/participants/me")
                .header("Authorization", "Bearer " + token));
    }

    private static long idOf(ResultActions result) throws Exception {
        String body = result.andExpect(status().isCreated()).andReturn().getResponse().getContentAsString();
        Matcher m = ID.matcher(body);
        assertThat(m.find()).isTrue();
        return Long.parseLong(m.group(1));
    }
}
