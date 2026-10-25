package com.aicode.framework.platform.dto;

import com.aicode.framework.dto.FrameworkUsageDto;
import com.aicode.framework.platform.domain.model.ExecutionRecord;

/**
 * 执行记录响应。
 *
 * @param executionId 执行 ID
 * @param userId      发起用户
 * @param tenantKey   归属租户（Week 23）
 * @param agentKey    Agent 标识
 * @param agentType   Agent 类型
 * @param status      状态
 * @param inputJson   入参 JSON
 * @param outputJson  输出 JSON
 * @param model       模型
 * @param usage       token 用量
 * @param errorMessage 错误信息
 * @param startedAt   开始时间
 * @param finishedAt  结束时间
 */
public record ExecutionRecordResponse(
        String executionId,
        long userId,
        String tenantKey,
        String agentKey,
        String agentType,
        String status,
        String inputJson,
        String outputJson,
        String model,
        FrameworkUsageDto usage,
        String errorMessage,
        String startedAt,
        String finishedAt
) {

    /**
     * 由领域模型转换。
     *
     * @param record 执行记录
     * @return 响应体
     */
    public static ExecutionRecordResponse from(ExecutionRecord record) {
        return new ExecutionRecordResponse(
                record.executionId(),
                record.userId(),
                record.tenantKey(),
                record.agentKey(),
                record.agentType().name(),
                record.status().name(),
                record.inputJson(),
                record.outputJson(),
                record.model(),
                new FrameworkUsageDto(
                        record.usage().promptTokens(),
                        record.usage().completionTokens(),
                        record.usage().totalTokens()),
                record.errorMessage(),
                record.startedAt().toString(),
                record.finishedAt() == null ? null : record.finishedAt().toString());
    }
}
