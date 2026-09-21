package com.aicode.framework.infrastructure.config;

import org.springframework.boot.context.properties.ConfigurationProperties;

/**
 * Graph Agent 配置。
 *
 * @param maxIterations 模型带工具循环的最大迭代次数，默认 5；非正数取默认值
 */
@ConfigurationProperties(prefix = "framework.agent")
public record FrameworkAgentProperties(Integer maxIterations) {

    /**
     * @return 有效迭代上限，缺失或非正数返回 5
     */
    public int resolvedMaxIterations() {
        return maxIterations == null || maxIterations <= 0 ? 5 : maxIterations;
    }
}
