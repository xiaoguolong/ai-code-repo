package com.aicode.core.domain.model;

import java.time.Instant;
import java.util.Objects;

/**
 * 长期记忆记录：一次已完成任务及其结论。用于跨会话的语义检索与复用。
 *
 * @param memoryId  记忆唯一标识
 * @param sessionId 产生该记忆的会话标识
 * @param task      用户任务原文
 * @param answer    任务最终结论
 * @param createdAt 创建时间，可为空（由调用方决定是否填充）
 */
public record MemoryRecord(String memoryId, String sessionId, String task, String answer, Instant createdAt) {

    public MemoryRecord {
        Objects.requireNonNull(memoryId, "memoryId");
        Objects.requireNonNull(sessionId, "sessionId");
        Objects.requireNonNull(task, "task");
        Objects.requireNonNull(answer, "answer");
    }
}
