package com.godlife.backend.community;

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
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.delete;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.multipart;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.post;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.put;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.jsonPath;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

/** 커뮤니티: 글(말머리 · 검색 · 인증 결과 첨부 · 사진) · 좋아요 · 댓글 · 신고 · 차단, 인증 사진 응원 댓글 */
@SpringBootTest
@AutoConfigureMockMvc
@ActiveProfiles("test")
@Transactional
class CommunityApiTest {

    private static final Pattern ID = Pattern.compile("\"id\":(\\d+)");

    @Autowired MockMvc mvc;
    @Autowired UserRepository userRepository;
    @Autowired JwtProvider jwtProvider;
    @Autowired Clock clock;

    private User aUser;
    private User bUser;
    private String a;
    private String b;
    /** 다른 글과 섞이지 않게 테스트마다 다른 낱말을 제목에 넣어 검색한다 */
    private String word;

    @BeforeEach
    void setUp() {
        aUser = newUser();
        bUser = newUser();
        a = tokenOf(aUser, "USER");
        b = tokenOf(bUser, "USER");
        word = "w" + UUID.randomUUID().toString().replace("-", "").substring(0, 10);
    }

    @Test
    @DisplayName("글을 쓰면 비로그인도 목록 · 상세에서 볼 수 있고, 말머리와 검색으로 거른다")
    void writeAndRead() throws Exception {
        long tip = write(a, "TIP", word + " 인증샷 팁", "밝은 곳에서 찍으세요", null);
        write(a, "FREE", word + " 잡담", "오늘도 화이팅", null);

        mvc.perform(get("/api/posts").param("q", word)).andExpect(status().isOk())
                .andExpect(jsonPath("$.totalElements").value(2))
                .andExpect(jsonPath("$.items[0].title").value(word + " 잡담"))
                .andExpect(jsonPath("$.items[0].author.nickname").value(aUser.getNickname()));
        mvc.perform(get("/api/posts").param("q", word).param("topic", "TIP"))
                .andExpect(jsonPath("$.totalElements").value(1))
                .andExpect(jsonPath("$.items[0].id").value(tip));

        mvc.perform(get("/api/posts/" + tip)).andExpect(status().isOk())
                .andExpect(jsonPath("$.content").value("밝은 곳에서 찍으세요"))
                .andExpect(jsonPath("$.mine").value(false))
                .andExpect(jsonPath("$.verify").doesNotExist());
        detail(a, tip).andExpect(jsonPath("$.mine").value(true));

        // 비로그인은 쓸 수 없다
        mvc.perform(post("/api/posts").contentType(MediaType.APPLICATION_JSON).content(body("FREE", "제목", "내용", null)))
                .andExpect(status().isUnauthorized());
    }

    @Test
    @DisplayName("글쓴이만 고치고 지울 수 있고, 지운 글은 없는 글이 된다")
    void editAndDelete() throws Exception {
        long id = write(a, "FREE", word, "처음 내용", null);

        mvc.perform(put("/api/posts/" + id).header("Authorization", "Bearer " + b)
                        .contentType(MediaType.APPLICATION_JSON).content(body("FREE", word, "남이 고침", null)))
                .andExpect(status().isForbidden());
        mvc.perform(put("/api/posts/" + id).header("Authorization", "Bearer " + a)
                        .contentType(MediaType.APPLICATION_JSON).content(body("QUESTION", word, "고친 내용", null)))
                .andExpect(status().isNoContent());
        detail(b, id).andExpect(jsonPath("$.topic").value("QUESTION"))
                .andExpect(jsonPath("$.content").value("고친 내용"))
                .andExpect(jsonPath("$.updatedAt").isNotEmpty());

        mvc.perform(delete("/api/posts/" + id).header("Authorization", "Bearer " + b)).andExpect(status().isForbidden());
        mvc.perform(delete("/api/posts/" + id).header("Authorization", "Bearer " + a)).andExpect(status().isNoContent());
        detail(a, id).andExpect(status().isNotFound());
        mvc.perform(get("/api/posts").param("q", word)).andExpect(jsonPath("$.totalElements").value(0));
    }

    @Test
    @DisplayName("인증 결과는 서버가 인증 기록으로 붙인다: 오늘 인증 전이면 실패, 참여하지 않은 챌린지는 붙일 수 없다")
    void attachVerifyResult() throws Exception {
        long challenge = challenge(a, true);

        mvc.perform(get("/api/community/attachable").header("Authorization", "Bearer " + a))
                .andExpect(jsonPath("$[0].challengeId").value(challenge))
                .andExpect(jsonPath("$[0].verifiedToday").value(false));
        long before = write(a, "REVIEW", word + " 인증 전", "아직 못 했어요", challenge);
        detail(b, before).andExpect(jsonPath("$.verify.challengeTitle").value("커뮤니티 테스트"))
                .andExpect(jsonPath("$.verify.success").value(false));

        verify(a, challenge);
        long after = write(a, "REVIEW", word + " 인증 후", "오늘도 했어요", challenge);
        detail(b, after).andExpect(jsonPath("$.verify.success").value(true))
                .andExpect(jsonPath("$.verify.date").value(LocalDate.now(clock).toString()));
        // 먼저 쓴 글의 결과는 쓴 시점 값 그대로다
        detail(b, before).andExpect(jsonPath("$.verify.success").value(false));

        // b 는 이 챌린지에 참여하지 않았다
        mvc.perform(post("/api/posts").header("Authorization", "Bearer " + b)
                        .contentType(MediaType.APPLICATION_JSON).content(body("REVIEW", word, "남의 챌린지", challenge)))
                .andExpect(status().isBadRequest());
    }

    @Test
    @DisplayName("좋아요는 여러 번 눌러도 한 번이고, 인기순은 좋아요 많은 글이 먼저다")
    void likes() throws Exception {
        long first = write(a, "FREE", word + " 하나", "내용", null);
        long second = write(a, "FREE", word + " 둘", "내용", null);

        like(b, first, true).andExpect(jsonPath("$.liked").value(true)).andExpect(jsonPath("$.likeCount").value(1));
        like(b, first, true).andExpect(jsonPath("$.likeCount").value(1));
        like(a, first, true).andExpect(jsonPath("$.likeCount").value(2));
        detail(b, first).andExpect(jsonPath("$.liked").value(true)).andExpect(jsonPath("$.likeCount").value(2));

        mvc.perform(get("/api/posts").param("q", word).param("sort", "popular"))
                .andExpect(jsonPath("$.items[0].id").value(first))
                .andExpect(jsonPath("$.items[1].id").value(second));

        like(b, first, false).andExpect(jsonPath("$.liked").value(false)).andExpect(jsonPath("$.likeCount").value(1));
        like(b, first, false).andExpect(jsonPath("$.likeCount").value(1));
        mvc.perform(put("/api/posts/" + first + "/like")).andExpect(status().isUnauthorized());
    }

    @Test
    @DisplayName("댓글을 달면 글쓴이에게 알림이 가고, 내 댓글만 지울 수 있다")
    void comments() throws Exception {
        long id = write(a, "QUESTION", word, "어떻게 찍어요?", null);

        long comment = idOf(mvc.perform(post("/api/posts/" + id + "/comments").header("Authorization", "Bearer " + b)
                        .contentType(MediaType.APPLICATION_JSON).content("{\"content\":\"가까이서 찍으세요\"}"))
                .andExpect(status().isCreated()).andReturn().getResponse().getContentAsString());
        detail(a, id).andExpect(jsonPath("$.commentCount").value(1))
                .andExpect(jsonPath("$.comments[0].content").value("가까이서 찍으세요"))
                .andExpect(jsonPath("$.comments[0].mine").value(false));
        mvc.perform(get("/api/notifications").header("Authorization", "Bearer " + a))
                .andExpect(jsonPath("$[0].type").value("COMMENT"))
                .andExpect(jsonPath("$[0].link").value("/community/" + id));

        // 댓글 좋아요: 여러 번 눌러도 한 번
        String likeUrl = "/api/post-comments/" + comment + "/like";
        mvc.perform(put(likeUrl).header("Authorization", "Bearer " + a)).andExpect(jsonPath("$.likeCount").value(1));
        mvc.perform(put(likeUrl).header("Authorization", "Bearer " + a)).andExpect(jsonPath("$.likeCount").value(1));
        detail(a, id).andExpect(jsonPath("$.comments[0].likeCount").value(1))
                .andExpect(jsonPath("$.comments[0].liked").value(true));
        detail(b, id).andExpect(jsonPath("$.comments[0].liked").value(false));
        mvc.perform(delete(likeUrl).header("Authorization", "Bearer " + a))
                .andExpect(jsonPath("$.liked").value(false)).andExpect(jsonPath("$.likeCount").value(0));

        mvc.perform(delete("/api/post-comments/" + comment).header("Authorization", "Bearer " + a))
                .andExpect(status().isNotFound());
        mvc.perform(delete("/api/post-comments/" + comment).header("Authorization", "Bearer " + b))
                .andExpect(status().isNoContent());
        detail(a, id).andExpect(jsonPath("$.commentCount").value(0))
                .andExpect(jsonPath("$.comments.length()").value(0));
    }

    @Test
    @DisplayName("답글(대댓글)은 원 댓글 아래 한 단계로 달리고, 원 댓글을 지워도 답글이 있으면 자리가 남는다")
    void replies() throws Exception {
        long id = write(a, "FREE", word, "내용", null);
        long parent = comment(b, id, "첫 댓글", null);
        long reply = comment(a, id, "답글이에요", parent);
        // 답글에 답글을 달아도 같은 원 댓글 아래로 간다
        comment(b, id, "답글의 답글", reply);

        detail(a, id).andExpect(jsonPath("$.commentCount").value(3))
                .andExpect(jsonPath("$.comments[0].parentId").doesNotExist())
                .andExpect(jsonPath("$.comments[1].parentId").value(parent))
                .andExpect(jsonPath("$.comments[2].parentId").value(parent));
        // 원 댓글을 쓴 b 에게 답글 알림이 간다
        mvc.perform(get("/api/notifications").header("Authorization", "Bearer " + b))
                .andExpect(jsonPath("$[0].title").value("답글이 달렸어요"));

        // 다른 글의 댓글에는 답글을 달 수 없다
        long other = write(a, "FREE", word + " 다른 글", "내용", null);
        mvc.perform(post("/api/posts/" + other + "/comments").header("Authorization", "Bearer " + a)
                        .contentType(MediaType.APPLICATION_JSON)
                        .content("{\"content\":\"엉뚱한 답글\",\"parentId\":" + parent + "}"))
                .andExpect(status().isNotFound());

        mvc.perform(delete("/api/post-comments/" + parent).header("Authorization", "Bearer " + b))
                .andExpect(status().isNoContent());
        detail(a, id).andExpect(jsonPath("$.commentCount").value(2))
                .andExpect(jsonPath("$.comments.length()").value(3))
                .andExpect(jsonPath("$.comments[0].removed").value(true))
                .andExpect(jsonPath("$.comments[0].content").value(""))
                .andExpect(jsonPath("$.comments[0].author").doesNotExist())
                .andExpect(jsonPath("$.comments[1].content").value("답글이에요"));
    }

    @Test
    @DisplayName("신고: 내 글은 안 되고 한 번만. 관리자가 가리면 글이 사라지고 신고한 사람 · 글쓴이에게 알린다")
    void reportAndHide() throws Exception {
        long id = write(a, "FREE", word, "광고 글", null);
        String admin = tokenOf(newUser(), "ADMIN");

        report(a, "posts", id).andExpect(status().isBadRequest());
        report(b, "posts", id).andExpect(status().isNoContent());
        report(b, "posts", id).andExpect(status().isConflict());
        detail(b, id).andExpect(jsonPath("$.reported").value(true));

        mvc.perform(get("/api/admin/community-reports").header("Authorization", "Bearer " + b))
                .andExpect(status().isForbidden());
        mvc.perform(get("/api/admin/community-reports").header("Authorization", "Bearer " + admin))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$[?(@.targetId == " + id + " && @.targetType == 'POST')].reportCount").value(1));

        handle(admin, "POST", id, "hide").andExpect(status().isNoContent());
        handle(admin, "POST", id, "hide").andExpect(status().isConflict());
        detail(b, id).andExpect(status().isNotFound());
        for (String token : new String[] {a, b}) {
            mvc.perform(get("/api/notifications").header("Authorization", "Bearer " + token))
                    .andExpect(jsonPath("$[0].type").value("REPORT_RESULT"));
        }
    }

    @Test
    @DisplayName("댓글 신고를 문제없음으로 닫으면 댓글은 그대로 남는다")
    void reportCommentDismissed() throws Exception {
        long id = write(a, "FREE", word, "내용", null);
        long comment = idOf(mvc.perform(post("/api/posts/" + id + "/comments").header("Authorization", "Bearer " + a)
                        .contentType(MediaType.APPLICATION_JSON).content("{\"content\":\"내 글의 댓글\"}"))
                .andReturn().getResponse().getContentAsString());
        String admin = tokenOf(newUser(), "ADMIN");

        report(b, "post-comments", comment).andExpect(status().isNoContent());
        detail(b, id).andExpect(jsonPath("$.comments[0].reported").value(true));
        handle(admin, "COMMENT", comment, "dismiss").andExpect(status().isNoContent());
        detail(b, id).andExpect(jsonPath("$.commentCount").value(1));
    }

    @Test
    @DisplayName("사진은 글쓴이만 4장까지 붙이고, 누구나 볼 수 있다. 글을 지우면 사진도 사라진다")
    void images() throws Exception {
        long id = write(a, "FREE", word, "사진 글", null);

        upload(b, id).andExpect(status().isForbidden());
        long image = 0;
        for (int i = 0; i < CommunityService.MAX_IMAGES; i++) {
            image = idOf(upload(a, id).andExpect(status().isCreated()).andReturn().getResponse().getContentAsString());
        }
        upload(a, id).andExpect(status().isConflict());

        mvc.perform(get("/api/post-images/" + image)).andExpect(status().isOk());
        mvc.perform(get("/api/posts").param("q", word)).andExpect(jsonPath("$.items[0].imageCount").value(4))
                .andExpect(jsonPath("$.items[0].thumbnailId").isNumber());

        mvc.perform(delete("/api/posts/" + id + "/images/" + image).header("Authorization", "Bearer " + a))
                .andExpect(status().isNoContent());
        detail(b, id).andExpect(jsonPath("$.imageIds.length()").value(3));

        // 지우면 사진 파일도 지운다 (테스트가 올린 파일을 남기지 않는다)
        mvc.perform(delete("/api/posts/" + id).header("Authorization", "Bearer " + a)).andExpect(status().isNoContent());
        mvc.perform(get("/api/post-images/" + image)).andExpect(status().isNotFound());
    }

    @Test
    @DisplayName("내가 차단한 사람의 글과 댓글은 보이지 않는다")
    void blocked() throws Exception {
        long mine = write(a, "FREE", word + " 내 글", "내용", null);
        long theirs = write(b, "FREE", word + " 남의 글", "내용", null);
        mvc.perform(post("/api/posts/" + mine + "/comments").header("Authorization", "Bearer " + b)
                .contentType(MediaType.APPLICATION_JSON).content("{\"content\":\"댓글\"}"));

        mvc.perform(put("/api/users/me/blocks/" + bUser.getId()).header("Authorization", "Bearer " + a))
                .andExpect(status().is2xxSuccessful());

        mvc.perform(get("/api/posts").param("q", word).header("Authorization", "Bearer " + a))
                .andExpect(jsonPath("$.totalElements").value(1))
                .andExpect(jsonPath("$.items[0].id").value(mine));
        detail(a, theirs).andExpect(status().isNotFound());
        detail(a, mine).andExpect(jsonPath("$.comments.length()").value(0));
    }

    @Test
    @DisplayName("인증 사진 응원 댓글은 같은 챌린지 사람끼리만 보고 쓰며, 인증한 사람에게 알림이 간다")
    void cheerComments() throws Exception {
        long challenge = challenge(a, true);
        mvc.perform(post("/api/challenges/" + challenge + "/participants").header("Authorization", "Bearer " + b))
                .andExpect(status().isOk());
        long verification = verify(a, challenge);
        String base = "/api/challenges/" + challenge + "/verifications/" + verification + "/comments";

        long comment = idOf(mvc.perform(post(base).header("Authorization", "Bearer " + b)
                        .contentType(MediaType.APPLICATION_JSON).content("{\"content\":\"멋져요!\"}"))
                .andExpect(status().isCreated()).andReturn().getResponse().getContentAsString());
        mvc.perform(get(base).header("Authorization", "Bearer " + a)).andExpect(status().isOk())
                .andExpect(jsonPath("$[0].content").value("멋져요!"))
                .andExpect(jsonPath("$[0].mine").value(false));
        mvc.perform(get("/api/notifications").header("Authorization", "Bearer " + a))
                .andExpect(jsonPath("$[?(@.type == 'COMMENT')].title").value("응원 댓글이 달렸어요"));

        // 챌린지 밖의 사람은 볼 수도 쓸 수도 없다
        String outsider = tokenOf(newUser(), "USER");
        mvc.perform(get(base).header("Authorization", "Bearer " + outsider)).andExpect(status().isNotFound());

        String remove = "/api/challenges/" + challenge + "/verification-comments/" + comment;
        mvc.perform(delete(remove).header("Authorization", "Bearer " + a)).andExpect(status().isNotFound());
        mvc.perform(delete(remove).header("Authorization", "Bearer " + b)).andExpect(status().isNoContent());
        mvc.perform(get(base).header("Authorization", "Bearer " + a)).andExpect(jsonPath("$.length()").value(0));
    }

    // ---------- helpers ----------

    private long write(String token, String topic, String title, String content, Long challengeId) throws Exception {
        return idOf(mvc.perform(post("/api/posts").header("Authorization", "Bearer " + token)
                        .contentType(MediaType.APPLICATION_JSON).content(body(topic, title, content, challengeId)))
                .andExpect(status().isCreated()).andReturn().getResponse().getContentAsString());
    }

    private long comment(String token, long postId, String content, Long parentId) throws Exception {
        return idOf(mvc.perform(post("/api/posts/" + postId + "/comments").header("Authorization", "Bearer " + token)
                        .contentType(MediaType.APPLICATION_JSON)
                        .content("{\"content\":\"%s\",\"parentId\":%s}".formatted(content, parentId)))
                .andExpect(status().isCreated()).andReturn().getResponse().getContentAsString());
    }

    private static String body(String topic, String title, String content, Long challengeId) {
        return "{\"topic\":\"%s\",\"title\":\"%s\",\"content\":\"%s\",\"challengeId\":%s}"
                .formatted(topic, title, content, challengeId);
    }

    private ResultActions detail(String token, long id) throws Exception {
        return mvc.perform(get("/api/posts/" + id).header("Authorization", "Bearer " + token));
    }

    private ResultActions like(String token, long id, boolean on) throws Exception {
        return mvc.perform((on ? put("/api/posts/" + id + "/like") : delete("/api/posts/" + id + "/like"))
                .header("Authorization", "Bearer " + token)).andExpect(status().isOk());
    }

    private ResultActions report(String token, String path, long id) throws Exception {
        return mvc.perform(post("/api/" + path + "/" + id + "/reports").header("Authorization", "Bearer " + token)
                .contentType(MediaType.APPLICATION_JSON).content("{\"reason\":\"광고예요\"}"));
    }

    private ResultActions handle(String admin, String type, long id, String action) throws Exception {
        return mvc.perform(post("/api/admin/community-reports/" + type + "/" + id + "/" + action)
                .header("Authorization", "Bearer " + admin));
    }

    private ResultActions upload(String token, long postId) throws Exception {
        return mvc.perform(multipart("/api/posts/" + postId + "/images")
                .file(new MockMultipartFile("file", "photo.png", "image/png", photo()))
                .header("Authorization", "Bearer " + token));
    }

    /** 오늘 시작하는 무료 챌린지. join 이면 만든 사람도 바로 참여한다 */
    private long challenge(String hostToken, boolean join) throws Exception {
        LocalDate today = LocalDate.now(clock);
        String body = """
                {"categoryId":1,"title":"커뮤니티 테스트","description":"매일 인증","mode":"FREE","visibility":"PUBLIC",
                 "startDate":"%s","endDate":"%s","frequencyType":"DAILY","maxParticipants":10}
                """.formatted(today, today.plusDays(6)).replace("\n", "");
        return idOf(mvc.perform(post("/api/challenges").param("join", String.valueOf(join))
                        .header("Authorization", "Bearer " + hostToken)
                        .contentType(MediaType.APPLICATION_JSON).content(body))
                .andExpect(status().isCreated()).andReturn().getResponse().getContentAsString());
    }

    /** 오늘 인증을 올리고 그 인증의 id 를 돌려준다 */
    private long verify(String token, long challengeId) throws Exception {
        return idOf(mvc.perform(multipart("/api/challenges/" + challengeId + "/verifications")
                        .file(new MockMultipartFile("file", "camera.png", "image/png", photo()))
                        .header("Authorization", "Bearer " + token))
                .andExpect(status().isCreated()).andReturn().getResponse().getContentAsString());
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
        return userRepository.saveAndFlush(User.createWithPassword(suffix + "@example.com", "unused", "c" + suffix,
                "c".repeat(52) + suffix));
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
