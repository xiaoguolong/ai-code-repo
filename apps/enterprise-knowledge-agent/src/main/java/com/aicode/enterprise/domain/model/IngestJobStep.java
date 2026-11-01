package com.aicode.enterprise.domain.model;

import com.aicode.core.domain.model.TokenUsage;

import java.time.Instant;

/**
 * 入库作业的一个步骤记录（Week 32）。
 *
 * <p>这是"Dify 式节点视图"的数据源：状态、耗时、输入/输出摘要、prompt 原文与 token 全在这里。
 * 只有 {@link IngestStepKey#CLEAN} 步骤会写 {@code promptText}；其它步骤显式留空。</p>
 *
 * @param jobId            作业 ID
 * @param stepNo           序号（从 1 开始，按执行顺序）
 * @param key              步骤标识
 * @param status           状态
 * @param inputSummary     输入摘要（如 file=xx (2.1MB)）
 * @param outputSummary    输出摘要（如 页数=20、块数=143）
 * @param promptVersion    prompt 版本（本地模板 v1 / Langfuse 版本）
 * @param promptText       渲染后的 prompt 原文
 * @param promptTokens     prompt token
 * @param completionTokens 生成 token
 * @param durationMs       耗时毫秒
 * @param errorMessage     失败原因
 * @param createdAt        创建时间
 * @param updatedAt        更新时间
 */
public record IngestJobStep(
        String jobId,
        int stepNo,
        IngestStepKey key,
        IngestStepStatus status,
        String inputSummary,
        String outputSummary,
        String promptVersion,
        String promptText,
        int promptTokens,
        int completionTokens,
        long durationMs,
        String errorMessage,
        Instant createdAt,
        Instant updatedAt
) {

    public IngestJobStep {
        inputSummary = inputSummary == null ? "" : inputSummary;
        outputSummary = outputSummary == null ? "" : outputSummary;
        promptVersion = promptVersion == null ? "" : promptVersion;
        promptText = promptText == null ? "" : promptText;
        errorMessage = errorMessage == null ? "" : errorMessage;
    }

    /**
     * 新建一步（PENDING）。
     *
     * @param jobId        作业
     * @param stepNo       序号
     * @param key          步骤
     * @param inputSummary 输入摘要
     * @param now          当前时间
     * @return PENDING 步骤
     */
    public static IngestJobStep pending(
            String jobId, int stepNo, IngestStepKey key, String inputSummary, Instant now) {
        return new IngestJobStep(jobId, stepNo, key, IngestStepStatus.PENDING, inputSummary, "",
                "", "", 0, 0, 0, "", now, now);
    }

    /**
     * @param now 当前时间
     * @return RUNNING 步骤
     */
    public IngestJobStep running(Instant now) {
        return new IngestJobStep(jobId, stepNo, key, IngestStepStatus.RUNNING, inputSummary, outputSummary,
                promptVersion, promptText, promptTokens, completionTokens, durationMs, "", createdAt, now);
    }

    /**
     * 成功步骤（可带 prompt 与 token；不需要的步骤传空/0）。
     *
     * @param output       输出摘要
     * @param promptVersion prompt 版本
     * @param prompt       prompt 原文
     * @param usage        token 用量，可为 null
     * @param durationMs   耗时
     * @param now          当前时间
     * @return SUCCEEDED 步骤
     */
    public IngestJobStep succeeded(
            String output,
            String promptVersion,
            String prompt,
            TokenUsage usage,
            long durationMs,
            Instant now
    ) {
        TokenUsage safe = usage == null ? TokenUsage.unknown() : usage;
        return new IngestJobStep(jobId, stepNo, key, IngestStepStatus.SUCCEEDED, inputSummary, output,
                promptVersion, prompt, safe.promptTokens(), safe.completionTokens(), durationMs, "",
                createdAt, now);
    }

    /**
     * @param error      错误摘要
     * @param durationMs 耗时
     * @param now        当前时间
     * @return FAILED 步骤
     */
    public IngestJobStep failed(String error, long durationMs, Instant now) {
        return new IngestJobStep(jobId, stepNo, key, IngestStepStatus.FAILED, inputSummary, outputSummary,
                promptVersion, promptText, promptTokens, completionTokens, durationMs, error, createdAt, now);
    }

    /**
     * @param reason 跳过原因
     * @param now    当前时间
     * @return SKIPPED 步骤
     */
    public IngestJobStep skipped(String reason, Instant now) {
        return new IngestJobStep(jobId, stepNo, key, IngestStepStatus.SKIPPED, inputSummary, reason,
                promptVersion, promptText, promptTokens, completionTokens, durationMs, "", createdAt, now);
    }
}
