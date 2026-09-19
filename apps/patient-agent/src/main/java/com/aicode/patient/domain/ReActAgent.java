package com.aicode.patient.domain;

import com.aicode.core.domain.model.ChatMessage;
import com.aicode.core.domain.model.ChatOptions;
import com.aicode.core.domain.model.ChatResult;
import com.aicode.core.domain.model.MessageRole;
import com.aicode.core.domain.model.PromptTemplate;
import com.aicode.core.domain.model.TokenUsage;
import com.aicode.core.domain.model.ToolCall;
import com.aicode.core.domain.model.ToolDefinition;
import com.aicode.core.domain.model.ToolResult;
import com.aicode.core.domain.port.ChatModelPort;
import com.aicode.core.domain.port.PromptTemplatePort;
import com.aicode.core.domain.port.ToolPort;
import com.aicode.patient.application.AgentRuntimeConfig;
import com.aicode.patient.domain.exception.AgentExecutionException;
import com.aicode.patient.domain.exception.AgentLoopExceededException;
import com.aicode.patient.domain.model.AgentResult;
import com.aicode.patient.domain.model.AgentStep;
import com.aicode.patient.domain.model.AgentTask;

import java.util.ArrayList;
import java.util.List;

/**
 * 原生 Function Calling Agent 编排（领域服务）。循环：调模型（带工具）→ 若要求工具则执行并回填观察 → 直到模型给出最终答案或超迭代。
 */
public class ReActAgent {

    private final ChatModelPort chatModelPort;
    private final PromptTemplatePort promptTemplatePort;
    private final ToolPort toolPort;
    private final AgentRuntimeConfig config;

    public ReActAgent(
            ChatModelPort chatModelPort,
            PromptTemplatePort promptTemplatePort,
            ToolPort toolPort,
            AgentRuntimeConfig config
    ) {
        this.chatModelPort = chatModelPort;
        this.promptTemplatePort = promptTemplatePort;
        this.toolPort = toolPort;
        this.config = config;
    }

    /**
     * 执行 Function Calling 循环并返回最终结果与完整工具调用轨迹。
     *
     * @throws AgentLoopExceededException 达到最大迭代次数仍未收敛
     * @throws AgentExecutionException    模型输出空白且无工具调用，无法推进循环
     */
    public AgentResult run(AgentTask task) {
        PromptTemplate system = promptTemplatePort.load("agent");
        List<ChatMessage> messages = new ArrayList<>();
        messages.add(new ChatMessage(MessageRole.SYSTEM, system.content()));
        messages.add(new ChatMessage(MessageRole.USER, task.content()));

        List<AgentStep> steps = new ArrayList<>();
        TokenUsage totalUsage = TokenUsage.unknown();
        ChatOptions options = new ChatOptions(config.model(), config.temperature(), config.maxTokens());
        String model = config.model();
        List<ToolDefinition> tools = toolPort.definitions();

        for (int i = 0; i < config.maxIterations(); i++) {
            ChatResult result = chatModelPort.chat(messages, options, tools);
            totalUsage = totalUsage.plus(result.usage());
            model = result.model() == null || result.model().isBlank() ? model : result.model();

            if (result.hasToolCalls()) {
                messages.add(ChatMessage.assistant(result.toolCalls()));
                for (ToolCall call : result.toolCalls()) {
                    ToolResult toolResult = toolPort.execute(call);
                    steps.add(new AgentStep(steps.size() + 1, call.name(), call.arguments(), toolResult.output()));
                    messages.add(ChatMessage.tool(call.id(), toolResult.output()));
                }
                continue;
            }

            String output = result.content() == null ? "" : result.content().trim();
            if (output.isEmpty()) {
                throw new AgentExecutionException("agent produced empty output");
            }
            return new AgentResult(task.taskId(), output, List.copyOf(steps), steps.size(), totalUsage, model);
        }
        throw new AgentLoopExceededException("agent exceeded max iterations: " + config.maxIterations());
    }
}
