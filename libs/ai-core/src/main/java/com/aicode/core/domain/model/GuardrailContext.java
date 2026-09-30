package com.aicode.core.domain.model;

/**
 * Guardrail 执行上下文：标识调用通道与可选用户/Agent，供审计与策略分支使用。
 *
 * @param channel  调用通道，如 platform-run、direct-run、tool
 * @param userId   可选用户 ID
 * @param agentKey 可选 Agent 标识
 */
public record GuardrailContext(String channel, Long userId, String agentKey) {

    /** Platform Run 通道。 */
    public static GuardrailContext platformRun(long userId, String agentKey) {
        return new GuardrailContext("platform-run", userId, agentKey);
    }

    /** Tool 执行通道。 */
    public static GuardrailContext tool(Long userId, String toolName) {
        return new GuardrailContext("tool", userId, toolName);
    }

    /** 直连 Run 通道。 */
    public static GuardrailContext directRun(String agentKey) {
        return new GuardrailContext("direct-run", null, agentKey);
    }
}
