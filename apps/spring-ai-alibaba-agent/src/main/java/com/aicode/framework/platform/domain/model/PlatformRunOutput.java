package com.aicode.framework.platform.domain.model;

import com.aicode.core.domain.model.TokenUsage;

import java.util.Map;

/**
 * 平台调度一次 Agent 的运行结果（尚未持久化）。
 *
 * @param output          结构化输出（写入 execution_record.output_json）
 * @param usage           token 用量
 * @param model           模型名
 * @param pendingApproval 是否停在人工审批（Week 23，HIGH 风险患者分析）
 */
public record PlatformRunOutput(
        Map<String, Object> output,
        TokenUsage usage,
        String model,
        boolean pendingApproval
) {

    /**
     * Week 8–22 兼容构造器：默认不进入人工审批。
     *
     * @param output 结构化输出
     * @param usage  token 用量
     * @param model  模型名
     */
    public PlatformRunOutput(Map<String, Object> output, TokenUsage usage, String model) {
        this(output, usage, model, false);
    }
}
