package com.aicode.patient.infrastructure.config;

import org.springframework.boot.context.properties.ConfigurationProperties;

/**
 * Agent 循环配置。
 *
 * @param promptVersion  系统提示版本，对应 prompts/{name}-{version}.txt
 * @param maxIterations  ReAct 循环最大迭代次数，兜底防死循环
 */
@ConfigurationProperties(prefix = "agent")
public record AgentProperties(String promptVersion, int maxIterations) {
}
