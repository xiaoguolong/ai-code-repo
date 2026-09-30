package com.aicode.core.infrastructure.config;

import org.springframework.boot.context.properties.ConfigurationProperties;

import java.util.List;

/**
 * Guardrail 配置。生产可通过环境变量覆盖开关与白名单。
 *
 * @param enabled                   总开关，false 时全链路放行
 * @param maxInputLength            单字段最大字符数
 * @param blockPromptInjection      是否拦截 Prompt 注入模式
 * @param sanitizeOutput            是否对输出做 PII 脱敏
 * @param allowedToolArgumentKeys   Tool 参数键白名单
 */
@ConfigurationProperties(prefix = "guardrail")
public record GuardrailProperties(
        Boolean enabled,
        Integer maxInputLength,
        Boolean blockPromptInjection,
        Boolean sanitizeOutput,
        List<String> allowedToolArgumentKeys
) {

    public boolean resolvedEnabled() {
        return enabled == null || enabled;
    }

    public int resolvedMaxInputLength() {
        return maxInputLength == null ? 8192 : maxInputLength;
    }

    public boolean resolvedBlockPromptInjection() {
        return blockPromptInjection == null || blockPromptInjection;
    }

    public boolean resolvedSanitizeOutput() {
        return sanitizeOutput == null || sanitizeOutput;
    }

    public List<String> resolvedAllowedToolArgumentKeys() {
        if (allowedToolArgumentKeys == null || allowedToolArgumentKeys.isEmpty()) {
            return List.of("patientId", "task");
        }
        return allowedToolArgumentKeys;
    }
}
