package com.aicode.patient.infrastructure.config;

import org.springframework.boot.context.properties.ConfigurationProperties;

/**
 * Agent 循环配置。
 *
 * @param maxIterations ReAct 循环最大迭代次数，兜底防死循环
 */
@ConfigurationProperties(prefix = "agent")
public record AgentProperties(int maxIterations) {
}
