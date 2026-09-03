package com.aicode.demo.application;

import com.aicode.demo.domain.exception.InvalidChatRequestException;
import com.aicode.demo.domain.model.ChatMessage;
import com.aicode.demo.domain.model.ChatResult;
import com.aicode.demo.domain.model.DocumentChunk;
import com.aicode.demo.domain.model.MessageRole;
import com.aicode.demo.domain.model.PromptTemplate;
import com.aicode.demo.domain.model.TokenUsage;
import com.aicode.demo.domain.port.ChatModelPort;
import com.aicode.demo.domain.port.EmbeddingModelPort;
import com.aicode.demo.domain.port.PromptTemplatePort;
import com.aicode.demo.infrastructure.vector.InMemoryVectorStoreAdapter;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.ArgumentCaptor;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;

import java.util.List;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

/**
 * RAG 问答用例：向量化提问 → 检索 → 组装上下文 → 调用模型 → 返回答案与来源。
 */
@ExtendWith(MockitoExtension.class)
class AskKnowledgeUseCaseTest {

    private static final PromptTemplate RAG_SYSTEM = new PromptTemplate("v1", "RAG_SYSTEM_RULES");

    @Mock
    private EmbeddingModelPort embedding;
    @Mock
    private ChatModelPort chatModel;
    @Mock
    private PromptTemplatePort promptTemplate;

    private InMemoryVectorStoreAdapter vectorStore;
    private AskKnowledgeUseCase useCase;

    @BeforeEach
    void setUp() {
        vectorStore = new InMemoryVectorStoreAdapter();
        useCase = new AskKnowledgeUseCase(
                embedding,
                vectorStore,
                chatModel,
                promptTemplate,
                new RagRuntimeConfig(4, 10, 0),
                new ChatRuntimeConfig("deepseek-chat", 0.7, 1024)
        );
    }

    @Test
    void shouldRetrieveAndAnswer_withSources() {
        when(embedding.embed("公司请假流程")).thenReturn(new float[]{1f, 0f, 0f});
        vectorStore.put(new DocumentChunk("d1", 0, "请假流程：员工提交申请后由主管审批。"), new float[]{1f, 0f, 0f});
        when(promptTemplate.load("rag")).thenReturn(RAG_SYSTEM);
        when(chatModel.chat(any(), any()))
                .thenReturn(new ChatResult("员工提交申请后由主管审批。", TokenUsage.unknown(), "deepseek-chat"));

        KnowledgeAnswer answer = useCase.ask(new KnowledgeQuestion("公司请假流程", 4));

        assertThat(answer.answer()).isEqualTo("员工提交申请后由主管审批。");
        assertThat(answer.sources()).hasSize(1);
        assertThat(answer.sources().get(0).content()).contains("请假流程");

        @SuppressWarnings("unchecked")
        ArgumentCaptor<List<ChatMessage>> captor = ArgumentCaptor.forClass(List.class);
        verify(chatModel).chat(captor.capture(), any());
        List<ChatMessage> messages = captor.getValue();
        assertThat(messages).hasSize(2);
        assertThat(messages.get(0).role()).isEqualTo(MessageRole.SYSTEM);
        assertThat(messages.get(0).content()).isEqualTo("RAG_SYSTEM_RULES");
        assertThat(messages.get(1).role()).isEqualTo(MessageRole.USER);
        assertThat(messages.get(1).content()).contains("请假流程", "公司请假流程");
    }

    @Test
    void shouldAnswerWithoutSources_whenStoreEmpty() {
        when(embedding.embed("无资料问题")).thenReturn(new float[]{1f, 0f, 0f});
        when(promptTemplate.load("rag")).thenReturn(RAG_SYSTEM);
        when(chatModel.chat(any(), any()))
                .thenReturn(new ChatResult("资料中没有相关信息", TokenUsage.unknown(), "deepseek-chat"));

        KnowledgeAnswer answer = useCase.ask(new KnowledgeQuestion("无资料问题", 4));

        assertThat(answer.sources()).isEmpty();
        assertThat(answer.answer()).isEqualTo("资料中没有相关信息");
    }

    @Test
    void shouldRejectBlankQuestion() {
        assertThatThrownBy(() -> useCase.ask(new KnowledgeQuestion("   ", 4)))
                .isInstanceOf(InvalidChatRequestException.class);
    }
}
