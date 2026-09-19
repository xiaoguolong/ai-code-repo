package com.aicode.patient.application;

import com.aicode.patient.domain.ReActAgent;
import com.aicode.core.domain.exception.InvalidChatRequestException;
import com.aicode.patient.domain.model.AgentResult;
import com.aicode.patient.domain.model.AgentTask;
import org.springframework.stereotype.Service;

import java.util.UUID;

/**
 * Agent 运行用例：校验任务非空 → 生成 taskId → 交给 ReActAgent 循环执行 → 返回结果。
 */
@Service
public class AgentRunUseCase {

    private final ReActAgent reactAgent;

    public AgentRunUseCase(ReActAgent reactAgent) {
        this.reactAgent = reactAgent;
    }

    /**
     * @param task 用户提出的任务描述
     * @return 最终答案 + 步骤轨迹 + 汇总 Token
     * @throws InvalidChatRequestException 任务空白
     */
    public AgentResult run(String task) {
        String content = task == null ? "" : task.trim();
        if (content.isEmpty()) {
            throw new InvalidChatRequestException("task must not be blank");
        }
        return reactAgent.run(new AgentTask(UUID.randomUUID().toString(), content));
    }
}
