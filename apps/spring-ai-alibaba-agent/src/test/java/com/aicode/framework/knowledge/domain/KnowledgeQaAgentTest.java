package com.aicode.framework.knowledge.domain;

import com.aicode.core.domain.exception.ChatModelException;
import com.aicode.core.domain.exception.ToolExecutionException;
import com.aicode.core.domain.model.ChatOptions;
import com.aicode.core.domain.model.ChatResult;
import com.aicode.core.domain.model.FinishReason;
import com.aicode.core.domain.model.PromptDescriptor;
import com.aicode.core.domain.model.PromptTemplate;
import com.aicode.core.domain.model.TokenUsage;
import com.aicode.core.domain.model.ToolCall;
import com.aicode.core.domain.model.ToolDefinition;
import com.aicode.core.domain.model.ToolResult;
import com.aicode.core.domain.port.ChatModelPort;
import com.aicode.core.domain.port.PromptTemplatePort;
import com.aicode.core.domain.port.ToolPort;
import com.aicode.framework.knowledge.application.KnowledgeRuntimeConfig;
import com.aicode.framework.knowledge.domain.model.KnowledgeAnswer;
import com.fasterxml.jackson.databind.ObjectMapper;
import org.junit.jupiter.api.Test;

import java.util.List;
import java.util.Map;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.anyList;
import static org.mockito.ArgumentMatchers.anyString;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.verifyNoInteractions;
import static org.mockito.Mockito.when;

/**
 * 知识库问答 Agent（Week 25）：命中生成答案、无命中不调模型、模型降级、工具失败。
 */
class KnowledgeQaAgentTest {

    private static final long KB_ID = 11L;
    private static final ObjectMapper MAPPER = new ObjectMapper();

    @Test
    void answersWithCitationsFromToolHits() {
        ChatModelPort chat = mock(ChatModelPort.class);
        when(chat.chat(anyList(), any(ChatOptions.class))).thenReturn(new ChatResult(
                "HbA1c 应每 3 个月复查 [糖尿病指南.pdf#2]", List.of(), FinishReason.STOP,
                new TokenUsage(10, 20, 30), "test-model"));
        KnowledgeQaAgent agent = agent(toolWithHits(), chat);

        KnowledgeAnswer answer = agent.run(KB_ID, "糖尿病随访要点");

        assertThat(answer.answer()).contains("每 3 个月");
        assertThat(answer.degraded()).isFalse();
        assertThat(answer.hitCount()).isEqualTo(2);
        assertThat(answer.citations()).hasSize(2);
        assertThat(answer.citations().get(0).documentName()).isEqualTo("糖尿病指南.pdf");
        assertThat(answer.usage().totalTokens()).isEqualTo(30);
        assertThat(answer.model()).isEqualTo("test-model");
        assertThat(answer.steps()).extracting("step")
                .containsExactly("knowledge_search", "answer_generation");
    }

    @Test
    void noHitsReturnsExplicitMessageWithoutCallingModel() {
        ChatModelPort chat = mock(ChatModelPort.class);
        KnowledgeQaAgent agent = agent(toolWithoutHits(), chat);

        KnowledgeAnswer answer = agent.run(KB_ID, "不存在的问题");

        assertThat(answer.answer()).contains("没有检索到");
        assertThat(answer.hitCount()).isZero();
        assertThat(answer.citations()).isEmpty();
        assertThat(answer.degraded()).isFalse();
        verifyNoInteractions(chat);
    }

    @Test
    void modelFailureDegradesToExcerpts() {
        ChatModelPort chat = mock(ChatModelPort.class);
        when(chat.chat(anyList(), any(ChatOptions.class)))
                .thenThrow(new ChatModelException("model down"));
        KnowledgeQaAgent agent = agent(toolWithHits(), chat);

        KnowledgeAnswer answer = agent.run(KB_ID, "糖尿病随访要点");

        assertThat(answer.degraded()).isTrue();
        assertThat(answer.answer()).contains("知识库检索结果").contains("糖尿病指南.pdf");
        assertThat(answer.citations()).hasSize(2);
        assertThat(answer.usage().totalTokens()).isZero();
    }

    @Test
    void blankModelContentDegrades() {
        ChatModelPort chat = mock(ChatModelPort.class);
        when(chat.chat(anyList(), any(ChatOptions.class))).thenReturn(new ChatResult(
                "  ", List.of(), FinishReason.STOP, TokenUsage.unknown(), "test-model"));

        KnowledgeAnswer answer = agent(toolWithHits(), chat).run(KB_ID, "q");

        assertThat(answer.degraded()).isTrue();
    }

    @Test
    void toolFailureIsPropagated() {
        ToolPort failing = new FakeToolPort() {
            @Override
            public ToolResult execute(ToolCall call) {
                throw new ToolExecutionException("knowledge service unavailable");
            }
        };

        assertThatThrownBy(() -> agent(failing, mock(ChatModelPort.class)).run(KB_ID, "q"))
                .isInstanceOf(ToolExecutionException.class);
    }

    private KnowledgeQaAgent agent(ToolPort toolPort, ChatModelPort chatModelPort) {
        return new KnowledgeQaAgent(
                toolPort, chatModelPort, new StubPromptTemplatePort(), MAPPER,
                new KnowledgeRuntimeConfig("test-model", 0.2, 512));
    }

    private ToolPort toolWithHits() {
        return new FakeToolPort();
    }

    private ToolPort toolWithoutHits() {
        return new FakeToolPort() {
            @Override
            public ToolResult execute(ToolCall call) {
                return new ToolResult("{\"knowledgeBaseId\":11,\"knowledgeBaseName\":\"慢病指南\",\"hits\":[]}");
            }
        };
    }

    /**
     * 工具桩：返回两份切片，字段与真实工具一致。
     */
    private static class FakeToolPort implements ToolPort {

        @Override
        public List<ToolDefinition> definitions() {
            return List.of();
        }

        @Override
        public ToolResult execute(ToolCall call) {
            return new ToolResult("{\"knowledgeBaseId\":11,\"knowledgeBaseName\":\"慢病指南\",\"hits\":["
                    + "{\"knowledgeBaseId\":11,\"documentId\":\"D001\",\"documentName\":\"糖尿病指南.pdf\","
                    + "\"chunkIndex\":2,\"content\":\"HbA1c 每 3 个月复查\",\"score\":0.91},"
                    + "{\"knowledgeBaseId\":11,\"documentId\":\"D002\",\"documentName\":\"随访规范.pdf\","
                    + "\"chunkIndex\":0,\"content\":\"血压目标 <140/90\",\"score\":0.82}]}");
        }
    }

    /** Prompt 桩：不读 classpath。 */
    private static final class StubPromptTemplatePort implements PromptTemplatePort {

        @Override
        public PromptTemplate load(String name) {
            return new PromptTemplate("v1", "你是知识库助手，只依据片段作答。");
        }

        @Override
        public PromptTemplate render(String name, Map<String, Object> variables) {
            return load(name);
        }

        @Override
        public List<PromptDescriptor> list() {
            return List.of();
        }
    }
}
