package com.aicode.framework.platform.infrastructure.security;

import com.aicode.framework.platform.infrastructure.config.PlatformRateLimitProperties;
import org.junit.jupiter.api.Test;

import java.time.Clock;
import java.time.Duration;
import java.time.Instant;
import java.time.ZoneOffset;

import static org.assertj.core.api.Assertions.assertThat;

/**
 * 登录失败计数（内存滑动窗口）：阈值、封禁到期、窗口过期、成功清零、key 隔离。
 */
class InMemoryLoginAttemptAdapterTest {

    private static final Instant START = Instant.parse("2026-10-24T12:00:00Z");
    private static final PlatformRateLimitProperties PROPERTIES =
            new PlatformRateLimitProperties(true, 5, 300, 300, false);

    @Test
    void notBlockedBeforeThreshold() {
        MutableClock clock = new MutableClock(START);
        InMemoryLoginAttemptAdapter adapter = adapter(clock);

        for (int i = 0; i < 4; i++) {
            adapter.recordFailure("1.1.1.1|alice");
        }

        assertThat(adapter.isBlocked("1.1.1.1|alice")).isFalse();
    }

    @Test
    void blocksAtThreshold() {
        MutableClock clock = new MutableClock(START);
        InMemoryLoginAttemptAdapter adapter = adapter(clock);

        for (int i = 0; i < 5; i++) {
            adapter.recordFailure("1.1.1.1|alice");
        }

        assertThat(adapter.isBlocked("1.1.1.1|alice")).isTrue();
    }

    @Test
    void unblocksAfterBlockWindow() {
        MutableClock clock = new MutableClock(START);
        InMemoryLoginAttemptAdapter adapter = adapter(clock);
        for (int i = 0; i < 5; i++) {
            adapter.recordFailure("1.1.1.1|alice");
        }

        clock.advance(Duration.ofSeconds(301));

        assertThat(adapter.isBlocked("1.1.1.1|alice")).isFalse();
    }

    @Test
    void failuresOlderThanWindowDoNotCount() {
        MutableClock clock = new MutableClock(START);
        InMemoryLoginAttemptAdapter adapter = adapter(clock);
        for (int i = 0; i < 4; i++) {
            adapter.recordFailure("1.1.1.1|alice");
        }

        clock.advance(Duration.ofSeconds(301));
        for (int i = 0; i < 4; i++) {
            adapter.recordFailure("1.1.1.1|alice");
        }

        assertThat(adapter.isBlocked("1.1.1.1|alice")).isFalse();
    }

    @Test
    void clearResetsCounter() {
        MutableClock clock = new MutableClock(START);
        InMemoryLoginAttemptAdapter adapter = adapter(clock);
        for (int i = 0; i < 5; i++) {
            adapter.recordFailure("1.1.1.1|alice");
        }

        adapter.clear("1.1.1.1|alice");

        assertThat(adapter.isBlocked("1.1.1.1|alice")).isFalse();
    }

    @Test
    void keysAreIndependent() {
        MutableClock clock = new MutableClock(START);
        InMemoryLoginAttemptAdapter adapter = adapter(clock);
        for (int i = 0; i < 5; i++) {
            adapter.recordFailure("1.1.1.1|alice");
        }

        assertThat(adapter.isBlocked("1.1.1.1|alice")).isTrue();
        assertThat(adapter.isBlocked("1.1.1.1|bob")).isFalse();
        assertThat(adapter.isBlocked("2.2.2.2|alice")).isFalse();
    }

    @Test
    void blankKeyNeverBlocks() {
        MutableClock clock = new MutableClock(START);
        InMemoryLoginAttemptAdapter adapter = adapter(clock);

        for (int i = 0; i < 10; i++) {
            adapter.recordFailure(" ");
        }

        assertThat(adapter.isBlocked(" ")).isFalse();
    }

    private InMemoryLoginAttemptAdapter adapter(Clock clock) {
        return new InMemoryLoginAttemptAdapter(PROPERTIES, clock);
    }

    /** 可推进的固定时钟。 */
    private static final class MutableClock extends Clock {

        private Instant now;

        private MutableClock(Instant start) {
            this.now = start;
        }

        private void advance(Duration duration) {
            now = now.plus(duration);
        }

        @Override
        public ZoneOffset getZone() {
            return ZoneOffset.UTC;
        }

        @Override
        public Clock withZone(java.time.ZoneId zone) {
            return this;
        }

        @Override
        public Instant instant() {
            return now;
        }
    }
}
