package com.aicode.enterprise;

import org.springframework.boot.test.context.TestConfiguration;
import org.springframework.context.annotation.Bean;

import java.time.Clock;
import java.time.Instant;
import java.time.ZoneOffset;

/**
 * 测试用固定时钟。
 */
@TestConfiguration
public class TestClockConfig {

    public static final Instant NOW = Instant.parse("2026-09-04T00:00:00Z");

    @Bean
    Clock clock() {
        return Clock.fixed(NOW, ZoneOffset.UTC);
    }
}
