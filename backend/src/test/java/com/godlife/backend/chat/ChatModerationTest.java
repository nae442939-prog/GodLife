package com.godlife.backend.chat;

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
import org.springframework.test.web.servlet.ResultActions;
import org.springframework.transaction.annotation.Transactional;

import java.time.Clock;
import java.time.LocalDate;
import java.util.UUID;
import java.util.regex.Matcher;
import java.util.regex.Pattern;

import static org.assertj.core.api.Assertions.assertThat;
import static org.hamcrest.Matchers.hasItem;
import static org.hamcrest.Matchers.hasSize;
import static org.hamcrest.Matchers.not;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.delete;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.post;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.put;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.jsonPath;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

/** 오픈채팅 관리(공지·강퇴·차단·신고) 통합 테스트. 신고 알림 기준은 테스트 설정에서 3건. */
@SpringBootTest
@AutoConfigureMockMvc
@ActiveProfiles("test")
@Transactional
class ChatModerationTest {

    private static final Pattern ID = Pattern.compile("\"id\":(\\d+)");

    @Autowired MockMvc mvc;
    @Autowired UserRepository userRepository;
    @Autowired JwtProvider jwtProvider;
    @Autowired JdbcTemplate jdbc;
    @Autowired Clock clock;

    private User hostUser;
    private User troll;
    private String host;
    private String trollToken;
    private String a;
    private String b;
    private String c;
    private long challengeId;
    private String inviteCode;

    @BeforeEach
    void setUp() throws Exception {
        hostUser = newUser();
        troll = newUser();
        host = tokenOf(hostUser);
        trollToken = tokenOf(troll);
        a = tokenOf(newUser());
        b = tokenOf(newUser());
        c = tokenOf(newUser());
        LocalDate today = LocalDate.now(clock);
        String body = """
                {"categoryId":5,"title":"관리 테스트","description":"같이 해요","mode":"FREE",
                 "startDate":"%s","endDate":"%s","frequencyType":"DAILY","maxParticipants":10}
                """.formatted(today.plusDays(2), today.plusDays(9)).replace("\n", "");
        String created = mvc.perform(post("/api/challenges").header("Authorization", "Bearer " + host)
                        .contentType(MediaType.APPLICATION_JSON).content(body))
                .andExpect(status().isCreated()).andReturn().getResponse().getContentAsString();
        challengeId = idOf(created);
        Matcher m = Pattern.compile("\"inviteCode\":\"(\\w+)\"").matcher(created);
        assertThat(m.find()).isTrue();
        inviteCode = m.group(1);
        for (String token : new String[] {trollToken, a, b, c}) {
            mvc.perform(post("/api/challenges/" + challengeId + "/participants").header("Authorization", "Bearer " + token))
                    .andExpect(status().isOk());
        }
    }

    @Test
    @DisplayName("방장 공지: 올리면 상세에 보이고 채팅방에 안내가 남는다. 방장만, 빈 내용이면 내린다")
    void notice() throws Exception {
        putNotice(a, "제가 공지할게요").andExpect(status().isForbidden());
        putNotice(host, "매일 밤 10시까지 인증해 주세요!").andExpect(status().isOk())
                .andExpect(jsonPath("$.notice").value("매일 밤 10시까지 인증해 주세요!"))
                .andExpect(jsonPath("$.noticeUpdatedAt").exists());
        list(a).andExpect(jsonPath("$[0].type").value("SYSTEM"))
                .andExpect(jsonPath("$[0].content").value("📢 방장이 공지를 올렸어요: 매일 밤 10시까지 인증해 주세요!"));
        putNotice(host, "   ").andExpect(jsonPath("$.notice").doesNotExist());
    }

    @Test
    @DisplayName("강퇴: 메시지는 가려지고 안내가 남으며, 채팅·재참여(초대 링크 포함)가 막힌다")
    void kick() throws Exception {
        send(trollToken, "나쁜 말").andExpect(status().isCreated());

        mvc.perform(post(kickUrl(troll.getId())).header("Authorization", "Bearer " + a)).andExpect(status().isForbidden());
        mvc.perform(post(kickUrl(hostUser.getId())).header("Authorization", "Bearer " + host))
                .andExpect(status().isBadRequest());
        mvc.perform(post(kickUrl(troll.getId())).header("Authorization", "Bearer " + host)).andExpect(status().isOk())
                .andExpect(jsonPath("$.participants[*].userId").value(not(hasItem(troll.getId().intValue()))));

        list(a).andExpect(jsonPath("$", hasSize(2)))
                .andExpect(jsonPath("$[0].hidden").value(true))
                .andExpect(jsonPath("$[0].content").doesNotExist())
                .andExpect(jsonPath("$[1].type").value("SYSTEM"))
                .andExpect(jsonPath("$[1].content").value(troll.getNickname() + "님이 방장에 의해 내보내졌어요."));

        list(trollToken).andExpect(status().isNotFound());
        mvc.perform(post("/api/challenges/" + challengeId + "/participants").header("Authorization", "Bearer " + trollToken))
                .andExpect(status().isForbidden()).andExpect(jsonPath("$.code").value("KICKED_FROM_CHALLENGE"));
        mvc.perform(post("/api/challenges/invite/" + inviteCode + "/participants")
                        .header("Authorization", "Bearer " + trollToken))
                .andExpect(status().isForbidden());
    }

    @Test
    @DisplayName("차단: 차단한 사람 화면에서만 그 사람 메시지가 빠진다. 안내 메시지는 남고, 풀면 다시 보인다")
    void block() throws Exception {
        send(trollToken, "도배").andExpect(status().isCreated());
        putNotice(host, "공지");

        mvc.perform(put("/api/users/me/blocks/" + troll.getId()).header("Authorization", "Bearer " + a))
                .andExpect(status().isNoContent());
        mvc.perform(put("/api/users/me/blocks/" + hostUser.getId()).header("Authorization", "Bearer " + a))
                .andExpect(status().isNoContent());
        list(a).andExpect(jsonPath("$", hasSize(1))).andExpect(jsonPath("$[0].type").value("SYSTEM"));
        list(b).andExpect(jsonPath("$", hasSize(2)));

        mvc.perform(get("/api/users/me/blocks").header("Authorization", "Bearer " + a))
                .andExpect(jsonPath("$", hasSize(2)));
        mvc.perform(delete("/api/users/me/blocks/" + troll.getId()).header("Authorization", "Bearer " + a))
                .andExpect(status().isNoContent());
        list(a).andExpect(jsonPath("$", hasSize(2)));

        mvc.perform(put("/api/users/me/blocks/" + userIdOf(a)).header("Authorization", "Bearer " + a))
                .andExpect(status().isBadRequest());
    }

    @Test
    @DisplayName("신고: 한 메시지에 한 번, 내 것·안내는 불가. 기준(3건)에 닿으면 방장 알림, 넘기면 사라진다")
    void reportAndAlert() throws Exception {
        long bad = idOf(send(trollToken, "욕설").andReturn().getResponse().getContentAsString());
        long mine = idOf(send(a, "제 메시지").andReturn().getResponse().getContentAsString());

        report(a, bad, "ABUSE", "심한 욕을 했어요").andExpect(status().isCreated());
        report(a, bad, "SPAM", null).andExpect(status().isConflict());
        report(a, mine, "SPAM", null).andExpect(status().isBadRequest());
        report(a, bad, null, null).andExpect(status().isBadRequest());

        mvc.perform(get(alertsUrl()).header("Authorization", "Bearer " + host)).andExpect(jsonPath("$", hasSize(0)));
        report(b, bad, "ABUSE", null).andExpect(status().isCreated());
        report(c, bad, "SPAM", "광고 링크").andExpect(status().isCreated());

        mvc.perform(get(alertsUrl()).header("Authorization", "Bearer " + host)).andExpect(status().isOk())
                .andExpect(jsonPath("$", hasSize(1)))
                .andExpect(jsonPath("$[0].userId").value(troll.getId()))
                .andExpect(jsonPath("$[0].reportCount").value(3))
                .andExpect(jsonPath("$[0].reasons.ABUSE").value(2))
                .andExpect(jsonPath("$[0].reasons.SPAM").value(1))
                .andExpect(jsonPath("$[0].recentDetails", hasSize(2)));
        assertThat(jdbc.queryForObject("SELECT COUNT(*) FROM notifications WHERE user_id = ? AND type = 'REPORT_ALERT'",
                Integer.class, hostUser.getId())).isEqualTo(1);

        mvc.perform(get(alertsUrl()).header("Authorization", "Bearer " + a)).andExpect(status().isForbidden());
        mvc.perform(post(alertsUrl() + "/" + troll.getId() + "/dismiss").header("Authorization", "Bearer " + host))
                .andExpect(status().isNoContent());
        mvc.perform(get(alertsUrl()).header("Authorization", "Bearer " + host)).andExpect(jsonPath("$", hasSize(0)));
    }

    // ---------- helpers ----------

    private User newUser() {
        String suffix = UUID.randomUUID().toString().replace("-", "").substring(0, 12);
        return userRepository.saveAndFlush(User.createWithPassword(suffix + "@example.com", "unused", "k" + suffix,
                "k".repeat(52) + suffix));
    }

    private String tokenOf(User user) {
        return jwtProvider.createAccessToken(user.getId(), "USER");
    }

    private long userIdOf(String token) {
        return jwtProvider.parse(token).id();
    }

    private String kickUrl(long userId) {
        return "/api/challenges/" + challengeId + "/participants/" + userId + "/kick";
    }

    private String alertsUrl() {
        return "/api/challenges/" + challengeId + "/report-alerts";
    }

    private ResultActions putNotice(String token, String content) throws Exception {
        return mvc.perform(put("/api/challenges/" + challengeId + "/notice").header("Authorization", "Bearer " + token)
                .contentType(MediaType.APPLICATION_JSON).content("{\"content\":\"" + content + "\"}"));
    }

    private ResultActions send(String token, String content) throws Exception {
        return mvc.perform(post("/api/challenges/" + challengeId + "/messages").header("Authorization", "Bearer " + token)
                .contentType(MediaType.APPLICATION_JSON).content("{\"content\":\"" + content + "\"}"));
    }

    private ResultActions list(String token) throws Exception {
        return mvc.perform(get("/api/challenges/" + challengeId + "/messages").header("Authorization", "Bearer " + token));
    }

    private ResultActions report(String token, long messageId, String reason, String detail) throws Exception {
        String json = "{\"reason\":" + (reason == null ? "null" : "\"" + reason + "\"")
                + (detail == null ? "" : ",\"detail\":\"" + detail + "\"") + "}";
        return mvc.perform(post("/api/challenges/" + challengeId + "/messages/" + messageId + "/reports")
                .header("Authorization", "Bearer " + token).contentType(MediaType.APPLICATION_JSON).content(json));
    }

    private static long idOf(String body) {
        Matcher m = ID.matcher(body);
        assertThat(m.find()).isTrue();
        return Long.parseLong(m.group(1));
    }
}
