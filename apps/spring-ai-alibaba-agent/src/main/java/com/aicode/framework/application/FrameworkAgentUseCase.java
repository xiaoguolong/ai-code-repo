package com.aicode.framework.application;

import com.aicode.core.domain.exception.InvalidChatRequestException;
import com.aicode.framework.domain.FrameworkAgentGraph;
import com.aicode.framework.domain.model.FrameworkAgentResult;
import org.springframework.stereotype.Service;

import java.util.UUID;

/**
 * Graph Agent 运行用例：校验任务 → 交给 {@link FrameworkAgentGraph} 执行 → 返回结果。
 */
@Service
public class FrameworkAgentUseCase {

    private final FrameworkAgentGraph frameworkAgentGraph;

    public FrameworkAgentUseCase(FrameworkAgentGraph frameworkAgentGraph) {
        this.frameworkAgentGraph = frameworkAgentGraph;
    }

    /**
     * @param task 用户任务描述
     * @return 最终答案 + 工具轨迹 + 汇总 Token
     * @throws InvalidChatRequestException 任务空白
     */
    public FrameworkAgentResult run(String task) {
        String content = task == null ? "" : task.trim();
        if (content.isEmpty()) {
            throw new InvalidChatRequestException("task must not be blank");
        }
        return frameworkAgentGraph.run(UUID.randomUUID().toString(), content);
    }
}
