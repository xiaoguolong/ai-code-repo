package com.aicode.patient.application;

/**
 * Agent 运行时配置。由基础设施把配置文件映射进来，领域不读 Environment。
 *
 * @param model           模型名
 * @param temperature     采样温度
 * @param maxTokens       单次生成 Token 上限
 * @param maxIterations   ReAct 循环最大迭代次数，兜底防死循环
 * @param maxMemoryMessages 短期记忆窗口条数上限（不含 system）
 * @param longTermTopK    长期记忆召回条数
 */
public record AgentRuntimeConfig(
        String model,
        double temperature,
        int maxTokens,
        int maxIterations,
        int maxMemoryMessages,
        int longTermTopK
) {
}
