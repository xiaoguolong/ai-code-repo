package com.aicode.gateway.infrastructure.feign;

import com.fasterxml.jackson.annotation.JsonIgnoreProperties;

/**
 * 平台 Agent 目录条目的 Feign 侧形态（Week 19）。
 *
 * <p>字段与平台 {@code PlatformAgentResponse} 对齐；多出的字段由
 * {@code ignoreUnknown} 忽略，平台加字段不需要网关同步发布。</p>
 *
 * @param agentKey      Agent 标识
 * @param name          展示名
 * @param description   描述
 * @param agentType     Agent 类型
 * @param enabled       是否启用
 * @param model         模型名
 * @param maxIterations 最大迭代步数
 * @param registeredAt  注册时间
 */
@JsonIgnoreProperties(ignoreUnknown = true)
public record FeignAgentPayload(
        String agentKey,
        String name,
        String description,
        String agentType,
        boolean enabled,
        String model,
        Integer maxIterations,
        String registeredAt
) {
}
