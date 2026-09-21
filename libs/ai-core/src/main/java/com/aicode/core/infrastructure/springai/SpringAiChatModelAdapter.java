package com.aicode.core.infrastructure.springai;

import com.aicode.core.domain.exception.ChatModelException;
import com.aicode.core.domain.model.ChatMessage;
import com.aicode.core.domain.model.ChatOptions;
import com.aicode.core.domain.model.ChatResult;
import com.aicode.core.domain.model.ToolDefinition;
import com.aicode.core.domain.port.ChatModelPort;
import org.springframework.ai.chat.model.ChatModel;
import org.springframework.ai.chat.prompt.Prompt;
import org.springframework.ai.model.tool.DefaultToolCallingChatOptions;

import java.util.List;

/**
 * Spring AI 模型适配器（实现 {@link ChatModelPort}）。把领域消息/工具/采样参数组装为 Spring AI {@link Prompt}，
 * 调用任意 Spring AI {@link ChatModel}（如 OpenAI 兼容端点指向 DeepSeek），再映射回领域结果。
 * <p>
 * 关闭 Spring AI 内部工具执行（{@code internalToolExecutionEnabled=false}）：本仓库所有工具调用统一经
 * {@code ToolPort} 执行，保证工具执行是唯一咽喉点，便于权限/审计。
 */
public class SpringAiChatModelAdapter implements ChatModelPort {

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
            DefaultToolCallingChatOptions chatOptions = new DefaultToolCallingChatOptions();
            chatOptions.setModel(options.model());
            chatOptions.setTemperature(options.temperature());
            chatOptions.setMaxTokens(options.maxTokens());
            chatOptions.setInternalToolExecutionEnabled(false);
            if (tools != null && !tools.isEmpty()) {
                chatOptions.setToolCallbacks(toolCallbackFactory.toDefinitionCallbacks(tools));
            }
            Prompt prompt = new Prompt(messageMapper.toSpringMessages(messages), chatOptions);
            return messageMapper.toChatResult(chatModel.call(prompt));
        } catch (ChatModelException ex) {
            throw ex;
        } catch (Exception ex) {
            throw new ChatModelException("spring ai chat failed: " + ex.getMessage(), ex);
        }
    }
}
