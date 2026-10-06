package com.aicode.gateway.infrastructure.redis;

import com.aicode.gateway.domain.port.GatewayCachePort;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.data.redis.core.ReactiveStringRedisTemplate;
import org.springframework.data.redis.core.ScanOptions;
import org.springframework.stereotype.Component;
import reactor.core.publisher.Mono;

import java.time.Duration;

/**
 * 网关缓存的 Redis 适配器（Week 19）——只做协议转换。
 *
 * <p>键空间：目录缓存 {@code gw:catalog:agents:v1}、幂等键 {@code gw:idem:<user>:<agent>:<traceId>}。</p>
 *
 * <p>统计用 {@code SCAN}（不用 {@code KEYS}）：自检接口每次调用都会扫一遍前缀，
 * 用 {@code KEYS} 在 key 多时会阻塞 Redis 单线程。</p>
 */
@Component
public class RedisGatewayCacheAdapter implements GatewayCachePort {

    private static final Logger log = LoggerFactory.getLogger(RedisGatewayCacheAdapter.class);

    /** 前缀统计的扫描上限（防御性：自检接口不该成为慢查询来源）。 */
    private static final long MAX_SCAN_COUNT = 1000L;

    private final ReactiveStringRedisTemplate redisTemplate;

    /**
     * @param redisTemplate 响应式字符串模板
     */
    public RedisGatewayCacheAdapter(ReactiveStringRedisTemplate redisTemplate) {
        this.redisTemplate = redisTemplate;
    }

    @Override
    public Mono<String> get(String key) {
        return redisTemplate.opsForValue().get(key);
    }

    @Override
    public Mono<Void> put(String key, String value, Duration ttl) {
        return redisTemplate.opsForValue().set(key, value, ttl).then();
    }

    @Override
    public Mono<Boolean> putIfAbsent(String key, String value, Duration ttl) {
        return redisTemplate.opsForValue().setIfAbsent(key, value, ttl).defaultIfEmpty(Boolean.FALSE);
    }

    @Override
    public Mono<Long> countByPrefix(String keyPrefix) {
        String pattern = keyPrefix + "*";
        return redisTemplate.scan(ScanOptions.scanOptions().match(pattern).count(200).build())
                .take(MAX_SCAN_COUNT)
                .count()
                .onErrorResume(ex -> {
                    log.warn("[gateway] 缓存前缀统计失败，按 0 处理：{}", ex.toString());
                    return Mono.just(0L);
                });
    }

    @Override
    public String storeName() {
        return "redis";
    }
}
