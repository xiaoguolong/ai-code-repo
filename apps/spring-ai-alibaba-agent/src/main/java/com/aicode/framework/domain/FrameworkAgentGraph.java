package com.aicode.framework.domain;

import com.aicode.core.domain.model.ChatMessage;
import com.aicode.core.domain.model.ChatOptions;
import com.aicode.core.domain.model.ChatResult;
import com.aicode.core.domain.model.MessageRole;
import com.aicode.core.domain.model.PromptTemplate;
import com.aicode.core.domain.model.TokenUsage;
import com.aicode.core.domain.model.ToolCall;
import com.aicode.core.domain.model.ToolDefinition;
import com.aicode.core.domain.model.ToolResult;
import com.aicode.core.domain.exception.ChatModelException;
import com.aicode.core.domain.exception.ToolExecutionException;
import com.aicode.core.domain.port.ChatModelPort;
import com.aicode.core.domain.port.PromptTemplatePort;
import com.aicode.core.domain.port.ToolPort;
import com.aicode.framework.application.FrameworkRuntimeConfig;
import com.aicode.framework.domain.exception.AgentExecutionException;
import com.aicode.framework.domain.exception.AgentLoopExceededException;
import com.aicode.framework.domain.model.FrameworkAgentResult;
import com.aicode.framework.domain.model.FrameworkAgentStep;
import com.alibaba.cloud.ai.graph.CompiledGraph;
import com.alibaba.cloud.ai.graph.OverAllState;
import com.alibaba.cloud.ai.graph.StateGraph;
import com.alibaba.cloud.ai.graph.exception.GraphStateException;
import com.alibaba.cloud.ai.graph.state.strategy.ReplaceStrategy;

import java.util.ArrayList;
import java.util.HashMap;
import java.util.List;
import java.util.Map;

import static com.alibaba.cloud.ai.graph.action.AsyncEdgeAction.edge_async;
import static com.alibaba.cloud.ai.graph.action.AsyncNodeAction.node_async;

/**
 * Spring AI Alibaba Graph 编排的 Agent（领域服务）。图结构：
 * {@code START → agent →(条件)→ tools → agent} 或 {@code agent → END}。
 * <p>
 * {@code agent} 节点经 {@link ChatModelPort} 调模型（携带 {@link ToolPort} 的工具定义）；
 * 模型要求工具时路由到 {@code tools} 节点，经 {@link ToolPort} 执行并把结果回填为工具消息；
 * 模型给出最终答案时写入 {@code answer} 结束。所有工具执行都走 {@code ToolPort}，是唯一咽喉点。
 */
public class FrameworkAgentGraph {

    private static final String NODE_AGENT = "agent";
    private static final String NODE_TOOLS = "tools";
    private static final String ROUTE_TOOLS = "tools";
    private static final String ROUTE_END = "end";

    static final String KEY_TASK = "task";
    static final String KEY_MESSAGES = "messages";
    static final String KEY_STEPS = "steps";
    static final String KEY_ANSWER = "answer";
    static final String KEY_ROUTE = "route";
    static final String KEY_ITERATIONS = "iterations";
    static final String KEY_USAGE = "usage";
    static final String KEY_MODEL = "model";

    private final ChatModelPort chatModelPort;
    private final PromptTemplatePort promptTemplatePort;
    private final ToolPort toolPort;
    private final FrameworkRuntimeConfig config;
    private final CompiledGraph graph;

    public FrameworkAgentGraph(
            ChatModelPort chatModelPort,
            PromptTemplatePort promptTemplatePort,
            ToolPort toolPort,
            FrameworkRuntimeConfig config
    ) {
        this.chatModelPort = chatModelPort;
        this.promptTemplatePort = promptTemplatePort;
        this.toolPort = toolPort;
        this.config = config;
        this.graph = buildGraph();
    }

    /**
     * 执行一次 Graph Agent。
     *
     * @param taskId 任务标识
     * @param task   任务描述
     * @return 最终答案 + 工具轨迹 + 汇总 Token + 模型名
     * @throws AgentLoopExceededException 超过最大迭代次数
     * @throws AgentExecutionException    模型最终输出空白
     */
    public FrameworkAgentResult run(String taskId, String task) {
        Map<String, Object> input = new HashMap<>();
        input.put(KEY_TASK, task);
        input.put(KEY_MESSAGES, new ArrayList<ChatMessage>());
        input.put(KEY_STEPS, new ArrayList<FrameworkAgentStep>());
        input.put(KEY_ANSWER, "");
        input.put(KEY_ROUTE, ROUTE_END);
        input.put(KEY_ITERATIONS, 0);
        input.put(KEY_USAGE, TokenUsage.unknown());
        input.put(KEY_MODEL, config.model());

        OverAllState state;
        try {
            state = graph.invoke(input)
                    .orElseThrow(() -> new AgentExecutionException("graph produced no state"));
        } catch (RuntimeException ex) {
            throw unwrap(ex);
        }

        List<FrameworkAgentStep> steps = state.value(KEY_STEPS, List.<FrameworkAgentStep>of());
        return new FrameworkAgentResult(
                taskId,
                state.value(KEY_ANSWER, ""),
                steps,
                steps.size(),
                state.value(KEY_USAGE, TokenUsage.unknown()),
                state.value(KEY_MODEL, config.model()));
    }

    private RuntimeException unwrap(Throwable throwable) {
        Throwable current = throwable;
        while (current != null) {
            if (current instanceof AgentLoopExceededException
                    || current instanceof AgentExecutionException
                    || current instanceof ChatModelException
                    || current instanceof ToolExecutionException) {
                return (RuntimeException) current;
            }
            current = current.getCause();
        }
        return throwable instanceof RuntimeException runtime
                ? runtime
                : new AgentExecutionException(throwable.getMessage());
    }

    private CompiledGraph buildGraph() {
        StateGraph stateGraph = new StateGraph(this::initialState);
        try {
            stateGraph.addNode(NODE_AGENT, node_async(this::runAgent));
            stateGraph.addNode(NODE_TOOLS, node_async(this::runTools));
            stateGraph.addEdge(StateGraph.START, NODE_AGENT);
            stateGraph.addConditionalEdges(NODE_AGENT,
                    edge_async(state -> state.value(KEY_ROUTE, ROUTE_END)),
                    Map.of(ROUTE_TOOLS, NODE_TOOLS, ROUTE_END, StateGraph.END));
            stateGraph.addEdge(NODE_TOOLS, NODE_AGENT);
            CompiledGraph compiled = stateGraph.compile();
            // 图级上限作为兜底，取大于节点自检上限的值，确保节点自检先触发并给出明确异常。
            compiled.setMaxIterations(config.maxIterations() * 2 + 5);
            return compiled;
        } catch (GraphStateException ex) {
            throw new IllegalStateException("failed to build framework agent graph", ex);
        }
    }

    private OverAllState initialState() {
        OverAllState state = new OverAllState();
        state.registerKeyAndStrategy(KEY_TASK, new ReplaceStrategy());
        state.registerKeyAndStrategy(KEY_MESSAGES, new ReplaceStrategy());
        state.registerKeyAndStrategy(KEY_STEPS, new ReplaceStrategy());
        state.registerKeyAndStrategy(KEY_ANSWER, new ReplaceStrategy());
        state.registerKeyAndStrategy(KEY_ROUTE, new ReplaceStrategy());
        state.registerKeyAndStrategy(KEY_ITERATIONS, new ReplaceStrategy());
        state.registerKeyAndStrategy(KEY_USAGE, new ReplaceStrategy());
        state.registerKeyAndStrategy(KEY_MODEL, new ReplaceStrategy());
        return state;
    }

    private Map<String, Object> runAgent(OverAllState state) {
        int iterations = state.value(KEY_ITERATIONS, 0);
        if (iterations >= config.maxIterations()) {
            throw new AgentLoopExceededException(
                    "framework agent exceeded max iterations: " + config.maxIterations());
        }

        List<ChatMessage> messages = new ArrayList<>(state.value(KEY_MESSAGES, List.<ChatMessage>of()));
        if (messages.isEmpty()) {
            PromptTemplate system = promptTemplatePort.load("agent");
            messages.add(new ChatMessage(MessageRole.SYSTEM, system.content()));
            messages.add(new ChatMessage(MessageRole.USER, state.value(KEY_TASK, "")));
        }

        List<ToolDefinition> tools = toolPort.definitions();
        ChatOptions options = new ChatOptions(config.model(), config.temperature(), config.maxTokens());
        ChatResult result = chatModelPort.chat(messages, options, tools);

        TokenUsage usage = state.value(KEY_USAGE, TokenUsage.unknown()).plus(result.usage());
        String model = result.model() == null || result.model().isBlank() ? config.model() : result.model();

        if (result.hasToolCalls()) {
            messages.add(ChatMessage.assistant(result.toolCalls()));
            return Map.of(
                    KEY_MESSAGES, messages,
                    KEY_ROUTE, ROUTE_TOOLS,
                    KEY_ITERATIONS, iterations + 1,
                    KEY_USAGE, usage,
                    KEY_MODEL, model);
        }

        String answer = result.content() == null ? "" : result.content().trim();
        if (answer.isEmpty()) {
            throw new AgentExecutionException("framework agent produced empty answer");
        }
        return Map.of(
                KEY_MESSAGES, messages,
                KEY_ANSWER, answer,
                KEY_ROUTE, ROUTE_END,
                KEY_ITERATIONS, iterations + 1,
                KEY_USAGE, usage,
                KEY_MODEL, model);
    }

    private Map<String, Object> runTools(OverAllState state) {
        List<ChatMessage> messages = new ArrayList<>(state.value(KEY_MESSAGES, List.<ChatMessage>of()));
        List<FrameworkAgentStep> steps = new ArrayList<>(state.value(KEY_STEPS, List.<FrameworkAgentStep>of()));

        for (ToolCall call : pendingToolCalls(messages)) {
            ToolResult toolResult = toolPort.execute(call);
            steps.add(new FrameworkAgentStep(steps.size() + 1, call.name(), call.arguments(), toolResult.output()));
            messages.add(ChatMessage.tool(call.id(), toolResult.output()));
        }

        return Map.of(
                KEY_MESSAGES, messages,
                KEY_STEPS, steps);
    }

    private List<ToolCall> pendingToolCalls(List<ChatMessage> messages) {
        for (int i = messages.size() - 1; i >= 0; i--) {
            ChatMessage message = messages.get(i);
            if (message.role() == MessageRole.ASSISTANT && !message.toolCalls().isEmpty()) {
                return message.toolCalls();
            }
        }
        throw new AgentExecutionException("no pending tool calls to execute");
    }
}
