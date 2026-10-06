package com.aicode.gateway.support;

import com.aicode.gateway.domain.port.GatewayCachePort;
import reactor.core.publisher.Mono;

import java.time.Duration;
import java.util.LinkedHashMap;
import java.util.Map;

/**
 * 缓存端口的进程内假实现（单测用）。
 *
 * <p>记录 put / putIfAbsent 的次数与最后一次 TTL，便于断言「命中缓存不再回源」与 TTL 传递。</p>
 */
public class FakeCacheStore implements GatewayCachePort {

    private final Map<String, String> values = new LinkedHashMap<>();

    /** 是否让所有操作失败（断言降级：缓存坏了业务仍要成功）。 */
    public boolean failing;

    /** put 调用次数。 */
    public int putCount;

    /** putIfAbsent 调用次数。 */
    public int putIfAbsentCount;

    /** 最后一次写入的 TTL（秒）。 */
    public long lastTtlSeconds;

    /** 上一次 putIfAbsent 的返回值（true=抢占成功）。 */
    public boolean lastAcquired = true;

    @Override
    public Mono<String> get(String key) {
        if (failing) {
            return Mono.error(new IllegalStateException("redis down"));
        }
        String value = values.get(key);
        return value == null ? Mono.empty() : Mono.just(value);
    }

    @Override
    public Mono<Void> put(String key, String value, Duration ttl) {
        if (failing) {
            return Mono.error(new IllegalStateException("redis down"));
        }
        putCount++;
        lastTtlSeconds = ttl.getSeconds();
        values.put(key, value);
        return Mono.empty();
    }

    @Override
    public Mono<Boolean> putIfAbsent(String key, String value, Duration ttl) {
        if (failing) {
            return Mono.error(new IllegalStateException("redis down"));
        }
        putIfAbsentCount++;
        lastTtlSeconds = ttl.getSeconds();
        if (values.containsKey(key)) {
            lastAcquired = false;
            return Mono.just(false);
        }
        values.put(key, value);
        lastAcquired = true;
        return Mono.just(true);
    }

    @Override
    public Mono<Long> countByPrefix(String keyPrefix) {
        if (failing) {
            return Mono.error(new IllegalStateException("redis down"));
        }
        return Mono.just(values.keySet().stream().filter(key -> key.startsWith(keyPrefix)).count());
    }

    @Override
    public String storeName() {
        return "fake";
    }

    /**
     * 直接放入一个缓存值（测试夹具）。
     *
     * @param key   键
     * @param value 值
     */
    public void putDirect(String key, String value) {
        values.put(key, value);
    }

    /**
     * 清空缓存与计数器（测试隔离用）。
     *
     * <p>单测里本类是被 Spring 上下文共享的单例；不隔离会让「回源次数」这类断言
     * 随执行顺序变化。</p>
     */
    public void clear() {
        values.clear();
        putCount = 0;
        putIfAbsentCount = 0;
        lastTtlSeconds = 0L;
        lastAcquired = true;
    }
}
