package com.aicode.patient.domain.model;

import java.util.Objects;

/**
 * Agent 任务。taskId 由用例生成，content 为用户提出的原始任务描述。
 */
public record AgentTask(String taskId, String content) {

    public AgentTask {
        Objects.requireNonNull(taskId, "taskId");
        Objects.requireNonNull(content, "content");
    }
}
