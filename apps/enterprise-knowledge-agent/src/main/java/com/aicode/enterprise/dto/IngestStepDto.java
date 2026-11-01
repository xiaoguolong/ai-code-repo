package com.aicode.enterprise.dto;

import com.aicode.enterprise.domain.model.IngestJobStep;

/**
 * 入库步骤 DTO（Dify 式节点视图的数据源）。
 *
 * @param stepNo           序号
 * @param stepKey          步骤 key（upload/ocr/clean/chunk/embed/index）
 * @param status           状态
 * @param durationMs       耗时
 * @param inputSummary     输入摘要
 * @param outputSummary    输出摘要
 * @param promptVersion    prompt 版本（无 prompt 的步骤为空）
 * @param promptText       prompt 原文（仅 clean）
 * @param promptTokens     prompt token
 * @param completionTokens 生成 token
 * @param errorMessage     错误
 */
public record IngestStepDto(
        int stepNo,
        String stepKey,
        String status,
        long durationMs,
        String inputSummary,
        String outputSummary,
        String promptVersion,
        String promptText,
        int promptTokens,
        int completionTokens,
        String errorMessage
) {

    /**
     * @param step 步骤
     * @return DTO
     */
    public static IngestStepDto from(IngestJobStep step) {
        return new IngestStepDto(step.stepNo(), step.key().apiKey(), step.status().name(), step.durationMs(),
                step.inputSummary(), step.outputSummary(), step.promptVersion(), step.promptText(),
                step.promptTokens(), step.completionTokens(), step.errorMessage());
    }
}
