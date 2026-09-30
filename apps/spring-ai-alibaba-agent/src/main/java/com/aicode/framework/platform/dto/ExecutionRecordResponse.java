package com.aicode.framework.platform.dto;

import com.aicode.framework.dto.FrameworkUsageDto;
import com.aicode.framework.platform.domain.model.ExecutionRecord;

/**
 * 执行记录响应。
 */
public record ExecutionRecordResponse(
        String executionId,
        long userId,
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

    public static ExecutionRecordResponse from(ExecutionRecord record) {
        return new ExecutionRecordResponse(
                record.executionId(),
                record.userId(),
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
