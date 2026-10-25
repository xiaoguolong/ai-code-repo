package com.aicode.framework.infrastructure.tool;

import com.aicode.core.domain.exception.ToolExecutionException;
import com.aicode.core.domain.model.ToolResult;
import com.aicode.framework.knowledge.domain.model.KnowledgeSearchHit;
import com.aicode.framework.knowledge.domain.model.KnowledgeSearchQuery;
import com.aicode.framework.knowledge.domain.port.KnowledgeSearchPort;
import com.aicode.framework.platform.domain.model.KnowledgeBinding;
import com.aicode.framework.platform.domain.model.PlatformMemoryScope;
import com.aicode.framework.platform.domain.port.PlatformRunScopePort;
import com.aicode.framework.platform.infrastructure.config.PlatformKnowledgeProperties;
import com.aicode.framework.platform.infrastructure.persistence.InMemoryKnowledgeBindingAdapter;
import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.ObjectMapper;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;

import java.time.Instant;
import java.util.List;
import java.util.Map;
import java.util.Optional;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

/**
 * 知识库检索工具（Week 25）：租户绑定校验、topK 夹取、无范围拒绝。
 */
class KnowledgeSearchToolTest {

    private static final long KB_ID = 11L;
    private static final long ENTERPRISE_USER = 7L;

    private InMemoryKnowledgeBindingAdapter bindingPort;
    private KnowledgeSearchPort searchPort;
    private PlatformRunScopePort scopePort;
    private KnowledgeSearchTool tool;

    @BeforeEach
    void setUp() {
        bindingPort = new InMemoryKnowledgeBindingAdapter();
        searchPort = mock(KnowledgeSearchPort.class);
        scopePort = () -> Optional.of(new PlatformMemoryScope("default", 2L));
        tool = new KnowledgeSearchTool(
                bindingPort, searchPort, scopePort, new PlatformKnowledgeProperties(), new ObjectMapper());
    }

    @Test
    void returnsHitsForBoundKnowledgeBase() throws Exception {
        bindingPort.save(binding(true));
        when(searchPort.search(new KnowledgeSearchQuery(ENTERPRISE_USER, KB_ID, "糖尿病随访", 4)))
                .thenReturn(List.of(new KnowledgeSearchHit(
                        KB_ID, "D001", "糖尿病指南.pdf", 2, "HbA1c 每 3 个月复查", 0.91)));

        ToolResult result = tool.execute(Map.of("knowledgeBaseId", KB_ID, "query", "糖尿病随访"));

        JsonNode json = new ObjectMapper().readTree(result.output());
        assertThat(json.path("knowledgeBaseName").asText()).isEqualTo("慢病指南");
        assertThat(json.path("hits")).hasSize(1);
        assertThat(json.path("hits").get(0).path("documentName").asText()).isEqualTo("糖尿病指南.pdf");
        verify(searchPort).search(new KnowledgeSearchQuery(ENTERPRISE_USER, KB_ID, "糖尿病随访", 4));
    }

    @Test
    void rejectsUnboundKnowledgeBase() {
        assertThatThrownBy(() -> tool.execute(Map.of("knowledgeBaseId", 999L, "query", "q")))
                .isInstanceOf(ToolExecutionException.class)
                .hasMessageContaining("not available");
    }

    @Test
    void rejectsDisabledBinding() {
        bindingPort.save(binding(false));

        assertThatThrownBy(() -> tool.execute(Map.of("knowledgeBaseId", KB_ID, "query", "q")))
                .isInstanceOf(ToolExecutionException.class);
    }

    @Test
    void rejectsCallOutsidePlatformRun() {
        KnowledgeSearchTool noScope = new KnowledgeSearchTool(
                bindingPort, searchPort, PlatformRunScopePort.NONE,
                new PlatformKnowledgeProperties(), new ObjectMapper());

        assertThatThrownBy(() -> noScope.execute(Map.of("knowledgeBaseId", KB_ID, "query", "q")))
                .isInstanceOf(ToolExecutionException.class)
                .hasMessageContaining("platform run scope");
    }

    @Test
    void clampsTopKToConfiguredMaximum() {
        bindingPort.save(binding(true));
        PlatformKnowledgeProperties properties = new PlatformKnowledgeProperties();
        properties.setMaxTopK(5);
        KnowledgeSearchTool limited = new KnowledgeSearchTool(
                bindingPort, searchPort, scopePort, properties, new ObjectMapper());
        when(searchPort.search(new KnowledgeSearchQuery(ENTERPRISE_USER, KB_ID, "q", 5)))
                .thenReturn(List.of());

        limited.execute(Map.of("knowledgeBaseId", KB_ID, "query", "q", "topK", 99));

        verify(searchPort).search(new KnowledgeSearchQuery(ENTERPRISE_USER, KB_ID, "q", 5));
    }

    @Test
    void rejectsMissingRequiredArguments() {
        bindingPort.save(binding(true));

        assertThatThrownBy(() -> tool.execute(Map.of("query", "q")))
                .isInstanceOf(ToolExecutionException.class);
        assertThatThrownBy(() -> tool.execute(Map.of("knowledgeBaseId", KB_ID)))
                .isInstanceOf(ToolExecutionException.class);
    }

    private KnowledgeBinding binding(boolean enabled) {
        Instant now = Instant.parse("2026-10-25T10:00:00Z");
        return new KnowledgeBinding(
                "b-1", "default", ENTERPRISE_USER, KB_ID, "慢病指南", "内部", enabled, 1L, now, now);
    }
}
