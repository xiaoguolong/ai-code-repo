package com.aicode.enterprise.application;

import com.aicode.enterprise.domain.RagContextAssembler;
import com.aicode.enterprise.domain.exception.ForbiddenException;
import com.aicode.enterprise.domain.exception.InvalidChatRequestException;
import com.aicode.enterprise.domain.exception.NotFoundException;
import com.aicode.enterprise.domain.model.ChatMessage;
import com.aicode.enterprise.domain.model.ChatOptions;
import com.aicode.enterprise.domain.model.ChatResult;
import com.aicode.enterprise.domain.model.KnowledgeBase;
import com.aicode.enterprise.domain.model.MessageRole;
import com.aicode.enterprise.domain.model.PromptTemplate;
import com.aicode.enterprise.domain.model.TokenRecord;
import com.aicode.enterprise.domain.model.VectorSearchHit;
import com.aicode.enterprise.domain.port.AuditPort;
import com.aicode.enterprise.domain.port.ChatModelPort;
import com.aicode.enterprise.domain.port.ConversationPort;
import com.aicode.enterprise.domain.port.EmbeddingModelPort;
import com.aicode.enterprise.domain.port.KnowledgeBasePort;
import com.aicode.enterprise.domain.port.PromptTemplatePort;
import com.aicode.enterprise.domain.port.VectorStorePort;
import org.springframework.stereotype.Service;

import java.time.Clock;
import java.util.List;
import java.util.UUID;

/**
 * 知识库问答用例：向量化提问 → 限定知识库检索 → 组装上下文 → 调用模型 → 落历史与 Token。
 */
@Service
public class AskKnowledgeUseCase {

    private final KnowledgeBasePort knowledgeBasePort;
    private final EmbeddingModelPort embeddingModelPort;
    private final VectorStorePort vectorStorePort;
    private final ChatModelPort chatModelPort;
    private final PromptTemplatePort promptTemplatePort;
    private final ConversationPort conversationPort;
    private final AuditPort auditPort;
    private final RagRuntimeConfig ragConfig;
    private final ChatRuntimeConfig chatConfig;
    private final Clock clock;

    public AskKnowledgeUseCase(
            KnowledgeBasePort knowledgeBasePort,
            EmbeddingModelPort embeddingModelPort,
            VectorStorePort vectorStorePort,
            ChatModelPort chatModelPort,
            PromptTemplatePort promptTemplatePort,
            ConversationPort conversationPort,
            AuditPort auditPort,
            RagRuntimeConfig ragConfig,
            ChatRuntimeConfig chatConfig,
            Clock clock
    ) {
        this.knowledgeBasePort = knowledgeBasePort;
        this.embeddingModelPort = embeddingModelPort;
        this.vectorStorePort = vectorStorePort;
        this.chatModelPort = chatModelPort;
        this.promptTemplatePort = promptTemplatePort;
        this.conversationPort = conversationPort;
        this.auditPort = auditPort;
        this.ragConfig = ragConfig;
        this.chatConfig = chatConfig;
        this.clock = clock;
    }

    /**
     * @throws InvalidChatRequestException 问题空白
     * @throws NotFoundException           知识库不存在
     * @throws ForbiddenException          知识库不属于当前用户
     */
    public KnowledgeAnswer ask(KnowledgeQuestion question) {
        String query = question.question() == null ? "" : question.question().trim();
        if (query.isEmpty()) {
            throw new InvalidChatRequestException("question must not be blank");
        }
        KnowledgeBase knowledgeBase = knowledgeBasePort.findById(question.knowledgeBaseId())
                .orElseThrow(() -> new NotFoundException("knowledge base not found"));
        if (!knowledgeBase.userId().equals(question.userId())) {
            throw new ForbiddenException("access denied");
        }
        String sessionId = question.sessionId() == null || question.sessionId().isBlank()
                ? UUID.randomUUID().toString()
                : question.sessionId();
        conversationPort.ensureSession(sessionId, question.userId(), question.knowledgeBaseId(), null);

        int topK = question.topK() == null || question.topK() <= 0
                ? ragConfig.defaultTopK()
                : question.topK();
        float[] queryVector = embeddingModelPort.embed(query);
        List<VectorSearchHit> hits = vectorStorePort.search(queryVector, topK, question.knowledgeBaseId());
        PromptTemplate system = promptTemplatePort.load("rag");
        String userContent = RagContextAssembler.buildUserMessage(hits, query);
        List<ChatMessage> messages = List.of(
                new ChatMessage(MessageRole.SYSTEM, system.content()),
                new ChatMessage(MessageRole.USER, userContent)
        );
        ChatOptions options = new ChatOptions(chatConfig.model(), chatConfig.temperature(), chatConfig.maxTokens());
        ChatResult result = chatModelPort.chat(messages, options);

        conversationPort.append(sessionId, new ChatMessage(MessageRole.USER, query));
        conversationPort.append(sessionId, new ChatMessage(MessageRole.ASSISTANT, result.content()));
        auditPort.record(new TokenRecord(
                question.userId(),
                sessionId,
                result.model(),
                result.usage().promptTokens(),
                result.usage().completionTokens(),
                result.usage().totalTokens(),
                clock.instant()
        ));
        return new KnowledgeAnswer(sessionId, result.content(), hits);
    }
}
