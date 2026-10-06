package com.aicode.gateway.infrastructure.redis;

import com.aicode.gateway.domain.model.GatewaySession;
import com.aicode.gateway.domain.port.GatewaySessionPort;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.core.io.ClassPathResource;
import org.springframework.data.redis.core.ReactiveStringRedisTemplate;
import org.springframework.data.redis.core.script.RedisScript;
import org.springframework.stereotype.Component;
import reactor.core.publisher.Mono;

import java.nio.charset.StandardCharsets;
import java.time.Duration;
import java.util.ArrayList;
import java.util.List;

/**
 * 会话存储的 Redis 适配器（Week 19）——本类只做协议转换，不含业务判断。
 *
 * <p>键空间：{@code gw:session:<token>}，值为 {@link GatewaySessionCodec} 产出的 JSON，TTL 由写入时指定。</p>
 *
 * <p>命令选择：写入用 {@code SET key value PXAT/TTL}（{@code setEx}），删除用 {@code DEL}，
 * 概况统计用 <b>SCAN + GET 的 Lua 脚本</b>（不用 {@code KEYS}，见脚本注释）。</p>
 */
@Component
public class RedisGatewaySessionAdapter implements GatewaySessionPort {

    private static final Logger log = LoggerFactory.getLogger(RedisGatewaySessionAdapter.class);

    /** 会话键前缀。 */
    public static final String KEY_PREFIX = "gw:session:";

    /** 单次概况扫描最多返回的会话数（防御性上限）。 */
    private static final int MAX_SCAN_RESULTS = 500;

    private final ReactiveStringRedisTemplate redisTemplate;
    private final GatewaySessionCodec codec;
    private final RedisScript<List> scanValuesScript;

    /**
     * @param redisTemplate 响应式字符串模板
     * @param codec         会话编解码
     */
    public RedisGatewaySessionAdapter(ReactiveStringRedisTemplate redisTemplate, GatewaySessionCodec codec) {
        this.redisTemplate = redisTemplate;
        this.codec = codec;
        this.scanValuesScript = RedisScript.of(new ClassPathResource("redis/scan-values.lua"), List.class);
    }

    @Override
    public Mono<Void> save(String sessionToken, GatewaySession session, Duration ttl) {
        return redisTemplate.opsForValue()
                .set(key(sessionToken), codec.encode(session), ttl)
                .then();
    }

    @Override
    public Mono<GatewaySession> find(String sessionToken) {
        return redisTemplate.opsForValue()
                .get(key(sessionToken))
                .map(codec::decode)
                .filter(session -> session != null);
    }

    @Override
    public Mono<Void> remove(String sessionToken) {
        return redisTemplate.delete(key(sessionToken)).then();
    }

    @Override
    public Mono<List<GatewaySession>> list() {
        return redisTemplate
                .execute(scanValuesScript, List.of(), List.of(KEY_PREFIX, String.valueOf(MAX_SCAN_RESULTS)))
                .next()
                .map(this::toSessions)
                .defaultIfEmpty(List.of())
                .onErrorResume(ex -> {
                    log.warn("[gateway] 会话概况扫描失败，按空处理：{}", ex.toString());
                    return Mono.just(List.of());
                });
    }

    @Override
    public String storeName() {
        return "redis";
    }

    /** 会话键：{@code gw:session:<token>}。 */
    private static String key(String sessionToken) {
        return KEY_PREFIX + sessionToken;
    }

    /** 脚本返回值（byte[] 列表）→ 会话列表，无法解析的条目跳过。 */
    private List<GatewaySession> toSessions(Object raw) {
        List<GatewaySession> sessions = new ArrayList<>();
        if (!(raw instanceof List<?> values)) {
            return sessions;
        }
        for (Object value : values) {
            String json = value instanceof byte[] bytes
                    ? new String(bytes, StandardCharsets.UTF_8)
                    : String.valueOf(value);
            GatewaySession session = codec.decode(json);
            if (session != null) {
                sessions.add(session);
            }
        }
        return sessions;
    }
}
