package com.aicode.patient.application;

import com.aicode.core.domain.MemoryWindow;
import com.aicode.core.domain.exception.InvalidChatRequestException;
import com.aicode.core.domain.model.ChatMessage;
import com.aicode.core.domain.model.MemoryHit;
import com.aicode.core.domain.model.MemoryRecord;
import com.aicode.core.domain.model.MessageRole;
import com.aicode.core.domain.port.LongTermMemoryPort;
import com.aicode.core.domain.port.MemoryPort;
import com.aicode.patient.domain.ReActAgent;
import com.aicode.patient.domain.model.AgentResult;
import com.aicode.patient.domain.model.AgentTask;
import org.springframework.stereotype.Service;

import java.time.Instant;
import java.util.List;
import java.util.UUID;

/**
 * Agent 运行用例：校验任务 → 确定 sessionId → 加载并截断短期记忆 → 召回长期记忆 →
 * 交给 ReActAgent 执行 → 写回短期记忆并保存长期记忆 → 返回结果。
 */
@Service
public class AgentRunUseCase {

    private final ReActAgent reactAgent;
    private final MemoryPort memoryPort;
    private final LongTermMemoryPort longTermMemoryPort;
    private final AgentRuntimeConfig config;

    public AgentRunUseCase(
            ReActAgent reactAgent,
            MemoryPort memoryPort,
            LongTermMemoryPort longTermMemoryPort,
            AgentRuntimeConfig config
    ) {
        this.reactAgent = reactAgent;
        this.memoryPort = memoryPort;
        this.longTermMemoryPort = longTermMemoryPort;
        this.config = config;
    }

    /**
     * @param sessionId 会话标识，可为空；为空时生成并随结果回传
     * @param task      用户提出的任务描述
     * @return 最终答案 + 步骤轨迹 + 汇总 Token + 会话/召回信息
     * @throws InvalidChatRequestException 任务空白
     */
    public AgentResult run(String sessionId, String task) {
        String content = task == null ? "" : task.trim();
        if (content.isEmpty()) {
            throw new InvalidChatRequestException("task must not be blank");
        }
        String resolvedSessionId = sessionId == null || sessionId.isBlank()
                ? UUID.randomUUID().toString()
                : sessionId.trim();

        List<ChatMessage> history = MemoryWindow.trim(
                memoryPort.load(resolvedSessionId), config.maxMemoryMessages());
        List<MemoryHit> recalled = longTermMemoryPort.search(content, config.longTermTopK());

        AgentResult result = reactAgent.run(
                new AgentTask(UUID.randomUUID().toString(), resolvedSessionId, content),
                history,
                recalled);

        memoryPort.append(resolvedSessionId, List.of(
                new ChatMessage(MessageRole.USER, content),
                new ChatMessage(MessageRole.ASSISTANT, result.answer())));
        longTermMemoryPort.save(new MemoryRecord(
                UUID.randomUUID().toString(), resolvedSessionId, content, result.answer(), Instant.now()));

        return result;
    }
}
