package com.aicode.framework.observability.domain;

import com.aicode.core.domain.model.TokenUsage;

/**
 * 一次 LLM 生成的可上报事实（Week 18）。
 *
 * <p>它是「领域事实 → Langfuse 属性」之间的数据载体：所有字段都由调用方在真实调用结束后填入，
 * 映射逻辑集中在 {@code LangfuseGenerationAttributes}（纯函数，可单测）。</p>
 *
 * <p>字段为空表示「不可得」而不是「0」：例如 usage 全为 0 表示上游没返回用量，
 * 此时不下发 usage / cost，避免把未知值混进成本统计。</p>
 *
 * @param model               真实生效模型名，可为空
 * @param modelParametersJson 模型参数 JSON（temperature / max_tokens），可为空
 * @param usage               Token 用量，可为 null
 * @param cost                估算成本，可为 null（未配单价）
 * @param prompt              关联的 Langfuse Prompt 名与版本，可为 null
 * @param inputJson           输入 JSON（仅内容采集开启时非空）
 * @param outputJson          输出 JSON（仅内容采集开启时非空）
 */
public record LlmGenerationRecord(
        String model,
        String modelParametersJson,
        TokenUsage usage,
        LlmCost cost,
        PromptReference prompt,
        String inputJson,
        String outputJson
) {
}
