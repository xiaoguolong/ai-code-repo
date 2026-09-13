package com.aicode.patient.domain;

import com.aicode.patient.application.AgentRuntimeConfig;
import com.aicode.patient.domain.exception.AgentExecutionException;
import com.aicode.patient.domain.exception.AgentLoopExceededException;
import com.aicode.patient.domain.model.AgentResult;
import com.aicode.patient.domain.model.AgentStep;
import com.aicode.patient.domain.model.AgentTask;
import com.aicode.patient.domain.model.ChatMessage;
import com.aicode.patient.domain.model.ChatOptions;
import com.aicode.patient.domain.model.ChatResult;
import com.aicode.patient.domain.model.MessageRole;
import com.aicode.patient.domain.model.PromptTemplate;
import com.aicode.patient.domain.model.ReActTurn;
import com.aicode.patient.domain.model.TokenUsage;
import com.aicode.patient.domain.port.ChatModelPort;
import com.aicode.patient.domain.port.PromptTemplatePort;

import java.util.ArrayList;
import java.util.List;

/**
 * ReAct 循环编排（领域服务）。多轮迭代：思考 → 行动 → 观察，直到产出 Final Answer 或超最大迭代。
 * 第 5 周 Action 为模型内部推理；第 6 周把 Action 升级为调用 {@code ToolPort}。
 */
public class ReActAgent {

    private final ChatModelPort chatModelPort;
    private final PromptTemplatePort promptTemplatePort;
    private final ReActOutputParser parser;
    private final AgentRuntimeConfig config;

    public ReActAgent(
            ChatModelPort chatModelPort,
            PromptTemplatePort promptTemplatePort,
            ReActOutputParser parser,
            AgentRuntimeConfig config
    ) {
        this.chatModelPort = chatModelPort;
        this.promptTemplatePort = promptTemplatePort;
        this.parser = parser;
        this.config = config;
    }

    /**
     * 执行 ReAct 循环并返回最终结果与完整轨迹。
     *
     * @throws AgentLoopExceededException 达到最大迭代次数仍未收敛
     * @throws AgentExecutionException    模型输出空白，无法推进循环
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

        for (int i = 0; i < config.maxIterations(); i++) {
            ChatResult result = chatModelPort.chat(messages, options);
            totalUsage = totalUsage.plus(result.usage());
            model = result.model() == null || result.model().isBlank() ? model : result.model();

            String output = result.content() == null ? "" : result.content().trim();
            if (output.isEmpty()) {
                throw new AgentExecutionException("agent produced empty output");
            }
            ReActTurn turn = parser.parse(output);
            steps.add(new AgentStep(i + 1, turn.thought(), turn.action(), turn.observation()));
            if (turn.finished()) {
                return new AgentResult(task.taskId(), turn.answer(), List.copyOf(steps), steps.size(), totalUsage, model);
            }
            messages.add(new ChatMessage(MessageRole.ASSISTANT, output));
        }
        throw new AgentLoopExceededException("agent exceeded max iterations: " + config.maxIterations());
    }
}
