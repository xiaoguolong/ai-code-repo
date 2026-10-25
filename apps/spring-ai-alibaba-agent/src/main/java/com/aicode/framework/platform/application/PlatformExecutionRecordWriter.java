package com.aicode.framework.platform.application;

import com.aicode.framework.platform.domain.model.ExecutionRecord;
import com.aicode.framework.platform.domain.model.ExecutionStatus;
import com.aicode.framework.platform.domain.port.ExecutionRecordPort;
import com.fasterxml.jackson.databind.ObjectMapper;
import org.springframework.stereotype.Component;

import java.time.Instant;
import java.util.Map;

/**
 * 执行记录终态落库（Week 23）。
 *
 * <p>审批决策需要把执行记录从 {@code PENDING_APPROVAL} 迁移到 {@code COMPLETED/REJECTED}
 * 并回写 output；抽成独立 writer，避免决策用例同时承担「状态机 + JSON + 持久化」三件事。</p>
 */
@Component
public class PlatformExecutionRecordWriter {

    private final ExecutionRecordPort executionRecordPort;
    private final ObjectMapper objectMapper;

    public PlatformExecutionRecordWriter(ExecutionRecordPort executionRecordPort, ObjectMapper objectMapper) {
        this.executionRecordPort = executionRecordPort;
        this.objectMapper = objectMapper;
    }

    /**
     * 以新状态与输出覆盖写入执行记录。
     *
     * @param execution  原执行记录（保留 id/入参/用量/开始时间）
     * @param status     终态
     * @param output     新输出 Map
     * @param finishedAt 完成时间
     */
    public void saveTerminal(
            ExecutionRecord execution,
            ExecutionStatus status,
            Map<String, Object> output,
            Instant finishedAt
    ) {
        ExecutionRecord updated = new ExecutionRecord(
                execution.executionId(),
                execution.userId(),
                execution.agentKey(),
                execution.agentType(),
                status,
                execution.inputJson(),
                PlatformRunOutputJson.toJson(objectMapper, output),
                execution.model(),
                execution.usage(),
                execution.errorMessage(),
                execution.startedAt(),
                finishedAt,
                execution.tenantKey());
        executionRecordPort.save(updated);
    }
}
