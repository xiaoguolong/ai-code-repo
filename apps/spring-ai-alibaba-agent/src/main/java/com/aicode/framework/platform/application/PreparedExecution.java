package com.aicode.framework.platform.application;

import com.aicode.framework.platform.domain.model.PlatformAgentDefinition;

import java.util.Map;

/**
 * 流式执行的预检结果（Week 31）。
 *
 * <p>预检（鉴权 / RBAC / Guardrail / Agent 存在与启用）在<b>请求线程</b>同步完成并抛类型化异常，
 * 这样 401/403/404/400 仍能以正确 HTTP 状态 + JSON 信封返回；只有预检通过才进入 SSE。
 * 真正执行在工作线程由 {@link PlatformExecutionRunner#execute} 完成。</p>
 *
 * @param userId   调用者
 * @param agentKey 注册表中的 Agent 标识
 * @param agent    Agent 定义（已确认存在且启用）
 * @param input    Run 入参
 */
public record PreparedExecution(
        long userId,
        String agentKey,
        PlatformAgentDefinition agent,
        Map<String, Object> input
) {
}
