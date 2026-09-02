package com.aicode.demo.application;

import com.aicode.demo.domain.ChatContextAssembler;
import com.aicode.demo.domain.ChatContextAssembler;
import com.aicode.demo.domain.exception.ChatModelException;
import com.aicode.demo.domain.exception.InvalidChatRequestException;
import com.aicode.demo.domain.exception.StructuredOutputException;
import com.aicode.demo.domain.model.AuditStatus;
import com.aicode.demo.domain.model.ChatAuditRecord;
import com.aicode.demo.domain.model.ChatMessage;
import com.aicode.demo.domain.model.ChatOptions;
import com.aicode.demo.domain.model.ChatResult;
import com.aicode.demo.domain.model.MessageRole;
import com.aicode.demo.domain.model.OutputFormat;
import com.aicode.demo.domain.model.PromptTemplate;
import com.aicode.demo.domain.model.TokenUsage;
import com.aicode.demo.domain.port.AuditPort;
import com.aicode.demo.domain.port.ChatModelPort;
import com.aicode.demo.domain.port.ConversationPort;
import com.aicode.demo.domain.port.MemoryPort;
import com.aicode.demo.domain.port.PromptTemplatePort;
import com.aicode.demo.domain.port.StructuredOutputPort;
import org.springframework.stereotype.Service;

import java.time.Clock;
import java.time.Duration;
import java.time.Instant;
import java.util.List;
import java.util.Map;
import java.util.UUID;

/**
 * 聊天用例：组装 system/user 消息、调用模型、无论成败都写审计。
 */
@Service
public class ChatUseCase {

    /** 审计预览截断长度，避免日志被超长输入撑爆。 */
    private static final int PREVIEW_MAX_LENGTH = 2000;

    private final ChatModelPort chatModelPort;
    private final PromptTemplatePort promptTemplatePort;
    private final AuditPort auditPort;
    private final MemoryPort memoryPort;
    private final ConversationPort conversationPort;
    private final StructuredOutputPort structuredOutputPort;
    private final ChatRuntimeConfig config;
    private final Clock clock;

    public ChatUseCase(
            ChatModelPort chatModelPort,
            PromptTemplatePort promptTemplatePort,
            AuditPort auditPort,
            MemoryPort memoryPort,
            ConversationPort conversationPort,
            StructuredOutputPort structuredOutputPort,
            ChatRuntimeConfig config,
            Clock clock
    ) {
        this.chatModelPort = chatModelPort;
        this.promptTemplatePort = promptTemplatePort;
        this.auditPort = auditPort;
        this.memoryPort = memoryPort;
        this.conversationPort = conversationPort;
        this.structuredOutputPort = structuredOutputPort;
        this.config = config;
        this.clock = clock;
    }

    /**
     * 执行一轮聊天。
     *
     * @param command 会话与用户消息
     * @return 助手回复与 Token
     * @throws InvalidChatRequestException 消息空白
     * @throws ChatModelException          模型调用失败（已审计）
     */
    public ChatOutcome chat(ChatCommand command) {
        String message = command.message() == null ? "" : command.message().trim();
        if (message.isEmpty()) {
            throw new InvalidChatRequestException("message must not be blank");
        }
        OutputFormat format = command.outputFormat() == null ? OutputFormat.TEXT : command.outputFormat();
        String sessionId = resolveSessionId(command.sessionId());
        Instant startedAt = clock.instant();
        PromptTemplate prompt = loadTemplate(command.template());
        conversationPort.ensureSession(sessionId, config.model());
        List<ChatMessage> history = loadHistory(sessionId);
        // 系统提示与用户输入分 role 传递，防止提示注入把用户文本当成指令。
        List<ChatMessage> messages = ChatContextAssembler.assemble(
                prompt, history, message, config.maxMemoryMessages()
        );
        ChatOptions options = new ChatOptions(config.model(), config.temperature(), config.maxTokens());
        ChatResult result = null;
        try {
            result = chatModelPort.chat(messages, options);
            ChatMessage userMessage = new ChatMessage(MessageRole.USER, message);
            ChatMessage assistantMessage = new ChatMessage(MessageRole.ASSISTANT, result.content());
            List<ChatMessage> nextWindow = ChatContextAssembler.appendAndTruncate(
                    history, userMessage, assistantMessage, config.maxMemoryMessages()
            );
            memoryPort.replace(sessionId, nextWindow);
            conversationPort.append(sessionId, userMessage);
            conversationPort.append(sessionId, assistantMessage);
            auditPort.record(buildRecord(
                    startedAt, sessionId, result.model(), message, result.content(),
                    result.usage(), AuditStatus.SUCCESS, null
            ));
            Map<String, Object> payload = format == OutputFormat.JSON
                    ? structuredOutputPort.parseObject(result.content())
                    : null;
            return new ChatOutcome(
                    sessionId,
                    UUID.randomUUID().toString(),
                    result.content(),
                    result.usage(),
                    result.model(),
                    payload
            );
        } catch (ChatModelException ex) {
            auditPort.record(buildRecord(
                    startedAt, sessionId, config.model(), message, "",
                    TokenUsage.unknown(), AuditStatus.FAILED, "CHAT_MODEL_ERROR"
            ));
            throw ex;
        } catch (StructuredOutputException ex) {
            auditPort.record(buildRecord(
                    startedAt, sessionId, config.model(), message, resultContentIfAvailable(result),
                    TokenUsage.unknown(), AuditStatus.FAILED, "STRUCTURED_OUTPUT_ERROR"
            ));
            throw ex;
        }
    }

    private PromptTemplate loadTemplate(String templateName) {
        if (templateName == null || templateName.isBlank()) {
            return promptTemplatePort.loadSystemPrompt();
        }
        return promptTemplatePort.load(templateName.trim());
    }

    private List<ChatMessage> loadHistory(String sessionId) {
        List<ChatMessage> cached = memoryPort.load(sessionId);
        if (!cached.isEmpty()) {
            return cached;
        }
        List<ChatMessage> persisted = conversationPort.list(sessionId);
        memoryPort.replace(sessionId, persisted);
        return persisted;
    }

    private String resultContentIfAvailable(ChatResult result) {
        return result == null ? "" : result.content();
    }

    private String resolveSessionId(String sessionId) {
        if (sessionId == null || sessionId.isBlank()) {
            return UUID.randomUUID().toString();
        }
        return sessionId.trim();
    }

    private ChatAuditRecord buildRecord(
            Instant startedAt,
            String sessionId,
            String model,
            String input,
            String output,
            TokenUsage usage,
            AuditStatus status,
            String errorCode
    ) {
        long latencyMs = Duration.between(startedAt, clock.instant()).toMillis();
        return new ChatAuditRecord(
                startedAt,
                sessionId,
                model,
                preview(input),
                preview(output),
                usage.promptTokens(),
                usage.completionTokens(),
                usage.totalTokens(),
                latencyMs,
                status,
                errorCode
        );
    }

    private String preview(String text) {
        if (text == null) {
            return "";
        }
        if (text.length() <= PREVIEW_MAX_LENGTH) {
            return text;
        }
        return text.substring(0, PREVIEW_MAX_LENGTH);
    }
}
