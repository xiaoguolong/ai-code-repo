package com.aicode.core.infrastructure.config;

import org.springframework.boot.context.properties.ConfigurationProperties;

import java.time.Duration;

/**
 * Agent 记忆配置。
 *
 * @param shortTermProvider memory 或 redis，默认 memory（进程内）
 * @param longTermProvider  memory，本周仅内存向量实现
 * @param maxMessages       短期记忆窗口条数上限，默认 20
 * @param longTermTopK      长期记忆召回条数，默认 3
 * @param redisTtlHours     Redis 短期记忆过期小时数，默认 24
 */
@ConfigurationProperties(prefix = "memory")
public record MemoryProperties(
        String shortTermProvider,
        String longTermProvider,
        Integer maxMessages,
        Integer longTermTopK,
        Integer redisTtlHours
) {

    /**
     * @return 有效短期窗口条数，缺省或非法时回退 20
     */
    public int resolvedMaxMessages() {
        return maxMessages == null || maxMessages <= 0 ? 20 : maxMessages;
    }

    /**
     * @return 有效长期召回条数，缺省或非法时回退 3
     */
    public int resolvedLongTermTopK() {
        return longTermTopK == null || longTermTopK <= 0 ? 3 : longTermTopK;
    }

    /**
     * @return 有效 Redis 过期时长，缺省或非法时回退 24 小时
     */
    public Duration resolvedRedisTtl() {
        return Duration.ofHours(redisTtlHours == null || redisTtlHours <= 0 ? 24 : redisTtlHours);
    }
}
