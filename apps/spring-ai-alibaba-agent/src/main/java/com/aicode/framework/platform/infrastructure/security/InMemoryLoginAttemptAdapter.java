package com.aicode.framework.platform.infrastructure.security;

import com.aicode.framework.platform.domain.port.LoginAttemptPort;
import com.aicode.framework.platform.infrastructure.config.PlatformRateLimitProperties;
import org.springframework.stereotype.Component;

import java.time.Clock;
import java.time.Duration;
import java.time.Instant;
import java.util.ArrayDeque;
import java.util.Deque;
import java.util.Map;
import java.util.concurrent.ConcurrentHashMap;

/**
 * 内存版登录失败计数（Week 22）：滑动窗口 + 达到阈值后的封禁窗口。
 *
 * <p>为什么先做内存版：平台当前单实例；用 Redis 会引入新的运行时依赖与序列化问题，
 * 而限流器本身的价值与存储无关。多实例时换 {@link LoginAttemptPort} 的适配器即可。</p>
 *
 * <p>为什么按 key 而不是全局计数：只按 IP 会误伤同一出口 NAT 下的正常用户，
 * 只按用户名又给「锁别人账号」留了口子；{@code ip|username} 两者都要受限。</p>
 */
@Component
public class InMemoryLoginAttemptAdapter implements LoginAttemptPort {

    /** 超过该 key 数量时做一次清理，避免被随机用户名撑爆内存。 */
    private static final int CLEANUP_THRESHOLD = 10_000;

    private final PlatformRateLimitProperties properties;
    private final Clock clock;
    private final ConcurrentHashMap<String, AttemptWindow> windows = new ConcurrentHashMap<>();

    public InMemoryLoginAttemptAdapter(PlatformRateLimitProperties properties, Clock clock) {
        this.properties = properties;
        this.clock = clock;
    }

    @Override
    public boolean isBlocked(String key) {
        if (isBlank(key)) {
            return false;
        }
        AttemptWindow window = windows.get(key);
        if (window == null) {
            return false;
        }
        Instant now = clock.instant();
        boolean blocked = window.isBlocked(now,
                Duration.ofSeconds(properties.resolvedWindowSeconds()),
                Duration.ofSeconds(properties.resolvedBlockSeconds()),
                properties.resolvedMaxAttempts());
        if (!blocked && window.isEmpty()) {
            windows.remove(key, window);
        }
        return blocked;
    }

    @Override
    public void recordFailure(String key) {
        if (isBlank(key)) {
            return;
        }
        if (windows.size() > CLEANUP_THRESHOLD) {
            sweep(clock.instant());
        }
        windows.computeIfAbsent(key, ignored -> new AttemptWindow())
                .record(clock.instant(), Duration.ofSeconds(properties.resolvedWindowSeconds()));
    }

    @Override
    public void clear(String key) {
        if (isBlank(key)) {
            return;
        }
        windows.remove(key);
    }

    /** 清理窗口内已无有效失败记录的 key（粗粒度，够用即可）。 */
    private void sweep(Instant now) {
        Duration window = Duration.ofSeconds(properties.resolvedWindowSeconds());
        for (Map.Entry<String, AttemptWindow> entry : windows.entrySet()) {
            entry.getValue().prune(now.minus(window));
            if (entry.getValue().isEmpty()) {
                windows.remove(entry.getKey(), entry.getValue());
            }
        }
    }

    private boolean isBlank(String key) {
        return key == null || key.isBlank();
    }

    /**
     * 单个 key 的失败时间戳队列（同一 key 的读写加锁，不同 key 互不阻塞）。
     */
    private static final class AttemptWindow {

        private final Deque<Instant> failures = new ArrayDeque<>();

        synchronized void record(Instant at, Duration window) {
            pruneLocked(at.minus(window));
            failures.addLast(at);
        }

        synchronized boolean isBlocked(Instant now, Duration window, Duration block, int maxAttempts) {
            pruneLocked(now.minus(window));
            if (failures.size() < maxAttempts) {
                return false;
            }
            Instant last = failures.peekLast();
            return last != null && now.isBefore(last.plus(block));
        }

        synchronized void prune(Instant cutoff) {
            pruneLocked(cutoff);
        }

        synchronized boolean isEmpty() {
            return failures.isEmpty();
        }

        private void pruneLocked(Instant cutoff) {
            while (!failures.isEmpty() && failures.peekFirst().isBefore(cutoff)) {
                failures.pollFirst();
            }
        }
    }
}
