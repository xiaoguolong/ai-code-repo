package com.aicode.gateway.support;

import com.aicode.gateway.domain.model.GatewaySession;
import com.aicode.gateway.domain.port.GatewaySessionPort;
import reactor.core.publisher.Mono;

import java.time.Duration;
import java.util.ArrayList;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;

/**
 * 会话端口的进程内假实现（单测用）。
 *
 * <p>只实现端口语义，不模拟 Redis 命令细节（命令形态由真机验收与部署文档保证）。</p>
 */
public class FakeSessionStore implements GatewaySessionPort {

    private final Map<String, GatewaySession> sessions = new LinkedHashMap<>();

    /** 是否让所有操作失败（用于断言降级行为）。 */
    public boolean failing;

    @Override
    public Mono<Void> save(String sessionToken, GatewaySession session, Duration ttl) {
        if (failing) {
            return Mono.error(new IllegalStateException("redis down"));
        }
        sessions.put(sessionToken, session);
        return Mono.empty();
    }

    @Override
    public Mono<GatewaySession> find(String sessionToken) {
        if (failing) {
            return Mono.error(new IllegalStateException("redis down"));
        }
        GatewaySession session = sessions.get(sessionToken);
        return session == null ? Mono.empty() : Mono.just(session);
    }

    @Override
    public Mono<Void> remove(String sessionToken) {
        sessions.remove(sessionToken);
        return Mono.empty();
    }

    @Override
    public Mono<List<GatewaySession>> list() {
        if (failing) {
            return Mono.error(new IllegalStateException("redis down"));
        }
        return Mono.just(new ArrayList<>(sessions.values()));
    }

    @Override
    public String storeName() {
        return "fake";
    }

    /**
     * 直接放入一个会话（测试夹具）。
     *
     * @param token   会话 token
     * @param session 会话
     */
    public void put(String token, GatewaySession session) {
        sessions.put(token, session);
    }

    /**
     * 已写入的会话数量。
     *
     * @return 数量
     */
    public int size() {
        return sessions.size();
    }

    /**
     * 清空全部会话（测试隔离用）。
     *
     * <p>单测里本类是被 Spring 上下文共享的单例，不做隔离会让「会话数」这类断言
     * 随执行顺序变化（实测踩过：断言 1 实际 4）。</p>
     */
    public void clear() {
        sessions.clear();
    }
}
