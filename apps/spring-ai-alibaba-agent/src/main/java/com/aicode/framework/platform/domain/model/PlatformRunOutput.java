package com.aicode.framework.platform.domain.model;

import com.aicode.core.domain.model.TokenUsage;

import java.util.Map;

/**
 * 平台调度一次 Agent 的运行结果（尚未持久化）。
 */
public record PlatformRunOutput(
        Map<String, Object> output,
        TokenUsage usage,
        String model
) {
}
