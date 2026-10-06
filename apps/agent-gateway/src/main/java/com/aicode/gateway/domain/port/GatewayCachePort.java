package com.aicode.gateway.domain.port;

import reactor.core.publisher.Mono;

import java.time.Duration;

/**
 * 网关通用缓存出站端口（Week 19）。
 *
 * <p>本周只服务两件事：平台 Agent 目录的短 TTL 缓存、写入幂等键的抢占（SETNX）。
 * 之所以单独成端口而不是并进会话端口：两者生命周期与语义完全不同
 * （会话是「有就放行」，缓存是「可缺失、可过期、可重建」），混在一起会让
 * 「缓存不可用」被误判成「会话失效」。</p>
 *
 * <p>失败约定：适配器抛出运行时异常由接入方兜底；<b>缓存故障绝不能让业务请求失败</b>——
 * 用例层必须在使用前 catch 并降级为「缓存未命中」（见 {@code GatewayAgentUseCase}）。</p>
 */
public interface GatewayCachePort {

    /**
     * 读取字符串值。
     *
     * @param key 缓存键
     * @return 值；不存在时 {@link Mono#empty()}
     */
    Mono<String> get(String key);

    /**
     * 写入字符串值（带 TTL）。
     *
     * @param key   缓存键
     * @param value 值
     * @param ttl   有效期
     * @return 写入完成信号
     */
    Mono<Void> put(String key, String value, Duration ttl);

    /**
     * 仅当键不存在时写入（SETNX + TTL），用于写入幂等占位。
     *
     * @param key   键
     * @param value 值
     * @param ttl   有效期
     * @return 抢占成功为 true；键已存在为 false
     */
    Mono<Boolean> putIfAbsent(String key, String value, Duration ttl);

    /**
     * 按前缀统计键数量（自检用；实现应使用 SCAN 而非 KEYS）。
     *
     * @param keyPrefix 键前缀
     * @return 条目数
     */
    Mono<Long> countByPrefix(String keyPrefix);

    /**
     * 存储实现标识（自检展示用）。
     *
     * @return 实现名
     */
    String storeName();
}
