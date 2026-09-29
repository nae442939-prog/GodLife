package com.godlife.backend.common.notify;

import com.godlife.backend.common.error.BusinessException;
import com.godlife.backend.common.ratelimit.RequestThrottle;
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

import java.time.Duration;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatCode;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.post;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.jsonPath;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

/**
 * 데모 모드(기본값): 실제로 보내지 않고 인증번호를 응답으로 돌려준다. IP당 제한도 여기서 확인한다.
 * TestSenders 를 import 하지 않으므로 실제 설정대로 데모 발송기가 쓰인다.
 */
@SpringBootTest
@AutoConfigureMockMvc
@ActiveProfiles("test")
@Transactional
class DemoModeTest {

    @Autowired MockMvc mvc;
    @Autowired SmsSender smsSender;
    @Autowired EmailSender emailSender;
    @Autowired RequestThrottle throttle;

    @Test
    @DisplayName("기본 설정은 문자/메일 모두 데모 발송기다")
    void defaultsToDemo() {
        assertThat(smsSender.isDemo()).isTrue();
        assertThat(emailSender.isDemo()).isTrue();
    }

    @Test
    @DisplayName("데모 모드: 응답의 demoCode 로 인증을 끝낼 수 있다")
    void demoCodeWorks() throws Exception {
        String body = send("01020000001").andExpect(status().isOk())
                .andExpect(jsonPath("$.demoCode").isString())
                .andReturn().getResponse().getContentAsString();
        String code = body.replaceAll(".*\"demoCode\":\"(\\d{6})\".*", "$1");

        postJson("/api/phone-verifications/confirm", "{\"phone\":\"01020000001\",\"code\":\"%s\"}".formatted(code))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.phoneProof").isNotEmpty());
    }


    @Test
    @DisplayName("같은 키(IP)는 시간 창 안에서 정한 횟수까지만")
    void throttlePerKey() {
        String key = "test:" + System.nanoTime();
        throttle.check(key, 2, Duration.ofHours(1));
        throttle.check(key, 2, Duration.ofHours(1));
        assertThatThrownBy(() -> throttle.check(key, 2, Duration.ofHours(1)))
                .isInstanceOf(BusinessException.class);
        assertThatCode(() -> throttle.check(key + ":other", 2, Duration.ofHours(1))).doesNotThrowAnyException();
    }

    private ResultActions send(String phone) throws Exception {
        return postJson("/api/phone-verifications", "{\"phone\":\"%s\"}".formatted(phone));
    }

    private ResultActions postJson(String url, String json) throws Exception {
        return mvc.perform(post(url)
                .contentType(MediaType.APPLICATION_JSON).content(json));
    }
}
