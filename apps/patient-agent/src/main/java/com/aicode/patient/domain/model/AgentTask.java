package com.aicode.patient.domain.model;

import java.util.Objects;

/**
 * Agent 任务。taskId 由用例生成（单次执行标识），sessionId 标识多轮会话（短期记忆作用域），
 * content 为用户提出的原始任务描述。
 */
public record AgentTask(String taskId, String sessionId, String content) {

    public AgentTask {
        Objects.requireNonNull(taskId, "taskId");
        Objects.requireNonNull(sessionId, "sessionId");
        Objects.requireNonNull(content, "content");
    }
}
