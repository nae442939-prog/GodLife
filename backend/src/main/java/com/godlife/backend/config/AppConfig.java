package com.godlife.backend.config;

import org.springframework.boot.context.properties.EnableConfigurationProperties;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;

import java.time.Clock;
import java.time.ZoneId;

@Configuration
@EnableConfigurationProperties(JwtProperties.class)
public class AppConfig {

    /** 모든 시각 계산의 기준. 서버 시각 + Asia/Seoul 로 고정한다. (테스트에서는 교체 가능) */
    @Bean
    public Clock clock() {
        return Clock.system(ZoneId.of("Asia/Seoul"));
    }
}
