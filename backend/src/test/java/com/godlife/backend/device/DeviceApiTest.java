package com.godlife.backend.device;

import com.godlife.backend.auth.JwtProvider;
import com.godlife.backend.common.crypto.Hashing;
import com.godlife.backend.user.User;
import com.godlife.backend.user.UserRepository;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.boot.webmvc.test.autoconfigure.AutoConfigureMockMvc;
import org.springframework.http.MediaType;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.security.crypto.password.PasswordEncoder;
import org.springframework.test.context.ActiveProfiles;
import org.springframework.test.web.servlet.MockMvc;
import org.springframework.transaction.annotation.Transactional;

import java.util.UUID;

import static org.assertj.core.api.Assertions.assertThat;
import static org.hamcrest.Matchers.hasItem;
import static org.hamcrest.Matchers.not;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.post;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.jsonPath;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

/** 기기 핑거프린팅: 로그인 때 지문 해시 기록, 같은 기기 · IP 를 여러 계정이 쓰면 관리자 화면에 표시 */
@SpringBootTest
@AutoConfigureMockMvc
@ActiveProfiles("test")
@Transactional
class DeviceApiTest {

    private static final String PASSWORD = "Passw0rd!";

    @Autowired MockMvc mvc;
    @Autowired UserRepository userRepository;
    @Autowired JwtProvider jwtProvider;
    @Autowired PasswordEncoder passwordEncoder;
    @Autowired DeviceService deviceService;
    @Autowired JdbcTemplate jdbc;

    @Test
    @DisplayName("로그인할 때 보낸 기기 지문은 해시로만 남고, 같은 기기로 다시 들어오면 한 줄만 고친다. 지문이 없거나 이상해도 로그인은 된다")
    void recordsOnLogin() throws Exception {
        User user = newUser();
        String fingerprint = fingerprint();

        login(user, fingerprint).andExpect(status().isOk());
        login(user, fingerprint).andExpect(status().isOk());

        assertThat(jdbc.queryForList("SELECT fingerprint_hash FROM devices WHERE user_id = ?", String.class,
                user.getId())).containsExactly(Hashing.sha256Hex(fingerprint));

        login(user, null).andExpect(status().isOk());
        login(user, "<script>alert(1)</script>").andExpect(status().isOk());
        assertThat(devices(user)).isEqualTo(1);
    }

    @Test
    @DisplayName("같은 기기를 쓴 계정이 3개 이상이면 관리자 화면에 묶여 나오고(이메일은 가려서), 2개까지는 나오지 않는다")
    void flagsSharedDevice() throws Exception {
        String shared = fingerprint();
        User a = newUser();
        User b = newUser();
        User c = newUser();
        for (User u : new User[] {a, b, c}) {
            deviceService.record(u.getId(), shared, "203.0.113.7", "test-agent");
        }
        String pair = fingerprint();
        deviceService.record(newUser().getId(), pair, "203.0.113.8", "test-agent");
        deviceService.record(newUser().getId(), pair, "203.0.113.9", "test-agent");

        String key = Hashing.sha256Hex(shared).substring(0, 12);
        String group = "$[?(@.key == '" + key + "')]";
        mvc.perform(get("/api/admin/devices").header("Authorization", "Bearer " + adminToken()))
                .andExpect(status().isOk())
                .andExpect(jsonPath(group + ".kind").value(hasItem("DEVICE")))
                .andExpect(jsonPath(group + ".accounts").value(hasItem(3)))
                .andExpect(jsonPath(group + ".members[*].userId").value(hasItem(a.getId().intValue())))
                .andExpect(jsonPath(group + ".members[*].email").value(not(hasItem(a.getEmail()))))
                .andExpect(jsonPath("$[*].key").value(not(hasItem(Hashing.sha256Hex(pair).substring(0, 12)))));
    }

    @Test
    @DisplayName("같은 IP 를 쓴 계정이 5개 이상이면 IP 묶음으로 나온다")
    void flagsSharedIp() throws Exception {
        for (int i = 0; i < 5; i++) {
            deviceService.record(newUser().getId(), fingerprint(), "198.51.100.23", "test-agent");
        }
        mvc.perform(get("/api/admin/devices").header("Authorization", "Bearer " + adminToken()))
                .andExpect(jsonPath("$[?(@.key == '198.51.100.23')].kind").value(hasItem("IP")))
                .andExpect(jsonPath("$[?(@.key == '198.51.100.23')].accounts").value(hasItem(5)));
    }

    @Test
    @DisplayName("기기 목록은 관리자만 본다")
    void adminOnly() throws Exception {
        String userToken = jwtProvider.createAccessToken(newUser().getId(), "USER");
        mvc.perform(get("/api/admin/devices").header("Authorization", "Bearer " + userToken))
                .andExpect(status().isForbidden());
        mvc.perform(get("/api/admin/devices")).andExpect(status().isUnauthorized());
    }

    // ---------- helpers ----------

    private org.springframework.test.web.servlet.ResultActions login(User user, String fingerprint) throws Exception {
        var request = post("/api/auth/login").contentType(MediaType.APPLICATION_JSON)
                .content("{\"email\":\"%s\",\"password\":\"%s\"}".formatted(user.getEmail(), PASSWORD));
        if (fingerprint != null) {
            request = request.header(DeviceService.HEADER, fingerprint);
        }
        return mvc.perform(request);
    }

    private int devices(User user) {
        Integer n = jdbc.queryForObject("SELECT COUNT(*) FROM devices WHERE user_id = ?", Integer.class,
                user.getId());
        return n == null ? 0 : n;
    }

    private static String fingerprint() {
        return UUID.randomUUID().toString().replace("-", "") + UUID.randomUUID().toString().replace("-", "");
    }

    private String adminToken() {
        return jwtProvider.createAccessToken(newUser().getId(), "ADMIN");
    }

    private User newUser() {
        String suffix = UUID.randomUUID().toString().replace("-", "").substring(0, 12);
        return userRepository.saveAndFlush(User.createWithPassword(suffix + "@example.com",
                passwordEncoder.encode(PASSWORD), "d" + suffix, "d".repeat(52) + suffix));
    }
}
