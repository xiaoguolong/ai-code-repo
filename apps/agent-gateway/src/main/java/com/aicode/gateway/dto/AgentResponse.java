package com.aicode.gateway.dto;

import com.aicode.gateway.domain.model.AgentSummary;

/**
 * Agent 目录条目响应（Week 19）。
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
public record AgentResponse(
        String agentKey,
        String name,
        String description,
        String agentType,
        boolean enabled,
        String model,
        Integer maxIterations,
        String registeredAt
) {

    /**
     * 领域模型 → 响应体。
     *
     * @param agent 目录条目
     * @return 响应体
     */
    public static AgentResponse from(AgentSummary agent) {
        return new AgentResponse(
                agent.agentKey(), agent.name(), agent.description(), agent.agentType(),
                agent.enabled(), agent.model(), agent.maxIterations(), agent.registeredAt());
    }
}
