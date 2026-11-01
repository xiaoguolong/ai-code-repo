package com.aicode.core.infrastructure.springai;

import com.aicode.core.domain.exception.ChatModelException;
import com.aicode.core.domain.exception.StreamCancelledException;
import com.aicode.core.domain.model.ChatMessage;
import com.aicode.core.domain.model.ChatOptions;
import com.aicode.core.domain.model.ChatResult;
import com.aicode.core.domain.model.ChatStreamListener;
import com.aicode.core.domain.model.FinishReason;
import com.aicode.core.domain.model.TokenUsage;
import com.aicode.core.domain.model.ToolDefinition;
import com.aicode.core.domain.port.ChatModelPort;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.ai.chat.metadata.Usage;
import org.springframework.ai.chat.model.ChatModel;
import org.springframework.ai.chat.model.ChatResponse;
import org.springframework.ai.chat.prompt.Prompt;
import org.springframework.ai.model.tool.DefaultToolCallingChatOptions;

import java.util.List;
import java.util.concurrent.atomic.AtomicReference;

/**
 * Spring AI 模型适配器（实现 {@link ChatModelPort}）。把领域消息/工具/采样参数组装为 Spring AI {@link Prompt}，
 * 调用任意 Spring AI {@link ChatModel}（如 OpenAI 兼容端点指向 DeepSeek），再映射回领域结果。
 * <p>
 * 关闭 Spring AI 内部工具执行（{@code internalToolExecutionEnabled=false}）：本仓库所有工具调用统一经
 * {@code ToolPort} 执行，保证工具执行是唯一咽喉点，便于权限/审计。
 * <p>
 * Week 31 起支持增量输出：{@link #chatStream} 用 {@code ChatModel.stream(Prompt)} 逐块回调，
 * 累加正文、取末包 usage 与 finish_reason；监听器取消时用 {@code takeWhile} 退订上游，
 * 并抛 {@link StreamCancelledException}，避免"用户已停止却仍落完成记录"。
 */
public class SpringAiChatModelAdapter implements ChatModelPort {

    private static final Logger log = LoggerFactory.getLogger(SpringAiChatModelAdapter.class);

    private final ChatModel chatModel;
    private final SpringAiToolCallbackFactory toolCallbackFactory;
    private final SpringAiMessageMapper messageMapper;

    public SpringAiChatModelAdapter(
            ChatModel chatModel,
            SpringAiToolCallbackFactory toolCallbackFactory,
            SpringAiMessageMapper messageMapper
    ) {
        this.chatModel = chatModel;
        this.toolCallbackFactory = toolCallbackFactory;
        this.messageMapper = messageMapper;
    }

    /**
     * 发起一次聊天补全。Spring AI 抛出的任何异常统一包为 {@link ChatModelException}，不泄漏框架细节。
     */
    @Override
    public ChatResult chat(List<ChatMessage> messages, ChatOptions options, List<ToolDefinition> tools) {
        try {
            Prompt prompt = new Prompt(messageMapper.toSpringMessages(messages), buildOptions(options, tools));
            return messageMapper.toChatResult(chatModel.call(prompt));
        } catch (ChatModelException ex) {
            throw ex;
        } catch (Exception ex) {
            throw new ChatModelException("spring ai chat failed: " + ex.getMessage(), ex);
        }
    }

    /**
     * 发起一次增量聊天补全（Week 31）。
     *
     * <p>约定：只处理正文增量，不启用工具（需要原生 Function Calling 的链路仍走同步 {@link #chat}）；
     * usage 只认最后一个非空包（OpenAI 在 {@code stream_options.include_usage} 时末包才带）；
     * finish_reason 缺失时按正常结束处理。</p>
     *
     * @throws StreamCancelledException 监听器在结束前取消
     */
    @Override
    public ChatResult chatStream(List<ChatMessage> messages, ChatOptions options, ChatStreamListener listener) {
        try {
            Prompt prompt = new Prompt(messageMapper.toSpringMessages(messages), buildOptions(options, List.of()));
            StringBuilder content = new StringBuilder();
            AtomicReference<TokenUsage> usage = new AtomicReference<>(TokenUsage.unknown());
            AtomicReference<String> finishReason = new AtomicReference<>();
            // Week 31 排障：区分"调用发出晚"与"上游首包晚"（云端首字异常时定位用）。
            long subscribeAt = System.nanoTime();
            java.util.concurrent.atomic.AtomicBoolean firstChunk = new java.util.concurrent.atomic.AtomicBoolean();
            log.debug("[llm] stream subscribe model={} messages={}", options.model(), messages.size());

            chatModel.stream(prompt)
                    .takeWhile(response -> !listener.isCancelled())
                    .doOnNext(response -> {
                        if (firstChunk.compareAndSet(false, true)) {
                            log.debug("[llm] stream first chunk after {} ms",
                                    (System.nanoTime() - subscribeAt) / 1_000_000L);
                        }
                        String delta = deltaOf(response);
                        if (!delta.isEmpty()) {
                            content.append(delta);
                            listener.onDelta(delta);
                        }
                        TokenUsage chunkUsage = usageOf(response);
                        if (chunkUsage != null) {
                            usage.set(chunkUsage);
                        }
                        String chunkFinish = finishReasonOf(response);
                        if (chunkFinish != null && !chunkFinish.isBlank()) {
                            finishReason.set(chunkFinish);
                        }
                    })
                    .blockLast();
            log.debug("[llm] stream completed after {} ms chars={}",
                    (System.nanoTime() - subscribeAt) / 1_000_000L, content.length());

            if (listener.isCancelled()) {
                throw new StreamCancelledException("spring ai stream cancelled by listener");
            }
            String finish = finishReason.get();
            FinishReason resolved = (finish == null || finish.isBlank())
                    ? FinishReason.STOP : FinishReason.fromApi(finish);
            return new ChatResult(content.toString(), List.of(), resolved, usage.get(), options.model());
        } catch (StreamCancelledException | ChatModelException ex) {
            throw ex;
        } catch (Exception ex) {
            throw new ChatModelException("spring ai stream failed: " + ex.getMessage(), ex);
        }
    }

    private DefaultToolCallingChatOptions buildOptions(ChatOptions options, List<ToolDefinition> tools) {
        DefaultToolCallingChatOptions chatOptions = new DefaultToolCallingChatOptions();
        chatOptions.setModel(options.model());
        chatOptions.setTemperature(options.temperature());
        chatOptions.setMaxTokens(options.maxTokens());
        chatOptions.setInternalToolExecutionEnabled(false);
        if (tools != null && !tools.isEmpty()) {
            chatOptions.setToolCallbacks(toolCallbackFactory.toDefinitionCallbacks(tools));
        }
        return chatOptions;
    }

    /** 取本块的文本增量；结构缺失或为空时返回空串（usage-only 末包就是这种）。 */
    private String deltaOf(ChatResponse response) {
        if (response == null || response.getResult() == null || response.getResult().getOutput() == null) {
            return "";
        }
        String text = response.getResult().getOutput().getText();
        return text == null ? "" : text;
    }

    /** 取本块的 usage；缺失或为全 0（Spring AI 元数据默认值）返回 null，由调用方保留上一次的非空值。 */
    private TokenUsage usageOf(ChatResponse response) {
        if (response == null || response.getMetadata() == null) {
            return null;
        }
        Usage usage = response.getMetadata().getUsage();
        if (usage == null) {
            return null;
        }
        int prompt = usage.getPromptTokens() == null ? 0 : usage.getPromptTokens();
        int completion = usage.getCompletionTokens() == null ? 0 : usage.getCompletionTokens();
        int total = usage.getTotalTokens() == null ? prompt + completion : usage.getTotalTokens();
        if (prompt == 0 && completion == 0 && total == 0) {
            return null;
        }
        return new TokenUsage(prompt, completion, total);
    }

    private String finishReasonOf(ChatResponse response) {
        if (response == null || response.getResult() == null || response.getResult().getMetadata() == null) {
            return null;
        }
        return response.getResult().getMetadata().getFinishReason();
    }
}
