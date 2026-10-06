package com.aicode.gateway.domain.model;

/**
 * Agent 目录条目（网关侧视图，经 Feign 从平台读取）。
 *
 * <p>字段与平台 {@code GET /api/v1/platform/agents} 的响应一一对应，但网关<b>只读不判</b>：
 * 是否可执行、是否越权仍由平台裁决（Week 13 RBAC 一行未改）。</p>
 *
 * @param agentKey       Agent 标识
 * @param name           展示名
 * @param description    描述
 * @param agentType      Agent 类型
 * @param enabled        是否启用（平台侧配置，网关只透传）
 * @param model          配置的模型名，可为 null
 * @param maxIterations  最大迭代步数，可为 null
 * @param registeredAt   注册时间（ISO-8601 文本）
 */
public record AgentSummary(
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
