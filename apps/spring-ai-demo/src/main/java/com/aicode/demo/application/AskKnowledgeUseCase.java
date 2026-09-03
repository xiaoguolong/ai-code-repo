package com.aicode.demo.application;

import com.aicode.demo.domain.RagContextAssembler;
import com.aicode.demo.domain.exception.InvalidChatRequestException;
import com.aicode.demo.domain.model.ChatMessage;
import com.aicode.demo.domain.model.ChatOptions;
import com.aicode.demo.domain.model.ChatResult;
import com.aicode.demo.domain.model.MessageRole;
import com.aicode.demo.domain.model.PromptTemplate;
import com.aicode.demo.domain.model.VectorSearchHit;
import com.aicode.demo.domain.port.ChatModelPort;
import com.aicode.demo.domain.port.EmbeddingModelPort;
import com.aicode.demo.domain.port.PromptTemplatePort;
import com.aicode.demo.domain.port.VectorStorePort;
import org.springframework.stereotype.Service;

import java.util.List;

/**
 * RAG 问答用例：向量化提问 → 检索 → 组装上下文 → 调用模型 → 返回答案与来源。
 */
@Service
public class AskKnowledgeUseCase {

    private final EmbeddingModelPort embeddingModelPort;
    private final VectorStorePort vectorStorePort;
    private final ChatModelPort chatModelPort;
    private final PromptTemplatePort promptTemplatePort;
    private final RagRuntimeConfig ragConfig;
    private final ChatRuntimeConfig chatConfig;

    public AskKnowledgeUseCase(
            EmbeddingModelPort embeddingModelPort,
            VectorStorePort vectorStorePort,
            ChatModelPort chatModelPort,
            PromptTemplatePort promptTemplatePort,
            RagRuntimeConfig ragConfig,
            ChatRuntimeConfig chatConfig
    ) {
        this.embeddingModelPort = embeddingModelPort;
        this.vectorStorePort = vectorStorePort;
        this.chatModelPort = chatModelPort;
        this.promptTemplatePort = promptTemplatePort;
        this.ragConfig = ragConfig;
        this.chatConfig = chatConfig;
    }

    /**
     * 基于知识库回答一个问题。
     *
     * @param question 问题与可选 topK
     * @return 模型答案与本次检索来源
     * @throws InvalidChatRequestException 问题空白
     */
    public KnowledgeAnswer ask(KnowledgeQuestion question) {
        String query = question.question() == null ? "" : question.question().trim();
        if (query.isEmpty()) {
            throw new InvalidChatRequestException("question must not be blank");
        }
        int topK = question.topK() == null || question.topK() <= 0
                ? ragConfig.defaultTopK()
                : question.topK();
        float[] queryVector = embeddingModelPort.embed(query);
        List<VectorSearchHit> hits = vectorStorePort.search(queryVector, topK);
        PromptTemplate system = promptTemplatePort.load("rag");
        String userContent = RagContextAssembler.buildUserMessage(hits, query);
        List<ChatMessage> messages = List.of(
                new ChatMessage(MessageRole.SYSTEM, system.content()),
                new ChatMessage(MessageRole.USER, userContent)
        );
        ChatOptions options = new ChatOptions(chatConfig.model(), chatConfig.temperature(), chatConfig.maxTokens());
        ChatResult result = chatModelPort.chat(messages, options);
        return new KnowledgeAnswer(result.content(), hits);
    }
}
