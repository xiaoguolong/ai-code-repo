package com.aicode.framework.infrastructure.tool;

import com.aicode.core.domain.Tool;
import com.aicode.core.domain.exception.ToolExecutionException;
import com.aicode.core.domain.model.ToolDefinition;
import com.aicode.core.domain.model.ToolResult;
import com.aicode.framework.knowledge.domain.model.KnowledgeSearchHit;
import com.aicode.framework.knowledge.domain.model.KnowledgeSearchQuery;
import com.aicode.framework.knowledge.domain.port.KnowledgeSearchPort;
import com.aicode.framework.platform.domain.model.KnowledgeBinding;
import com.aicode.framework.platform.domain.model.PlatformMemoryScope;
import com.aicode.framework.platform.domain.port.KnowledgeBindingPort;
import com.aicode.framework.platform.domain.port.PlatformRunScopePort;
import com.aicode.framework.platform.infrastructure.config.PlatformKnowledgeProperties;
import com.fasterxml.jackson.databind.ObjectMapper;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.stereotype.Component;

import java.util.ArrayList;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;

/**
 * 知识库检索工具（Week 25）。
 *
 * <p>这是「知识库 Agent」唯一接触企业知识库的入口，走平台既有工具链：
 * {@code ObservableToolPort → AuthorizingToolPort（RBAC）→ GuardrailToolPort → ToolRegistry}。
 * 因此工具调用会自动进 {@code tool.call} span / 指标 / 失败率，并被角色授权约束。</p>
 *
 * <p><b>租户校验在工具内完成</b>：按当前 Platform Run 的 {@code tenantKey} 查绑定，
 * 未绑定/已停用一律拒绝——即使模型被注入，也拿不到别的租户的知识库。</p>
 */
@Component
public class KnowledgeSearchTool implements Tool {

    private static final Logger log = LoggerFactory.getLogger(KnowledgeSearchTool.class);

    /** 工具名（与 {@code KnowledgeQaAgent.TOOL_NAME} 一致）。 */
    public static final String NAME = "KnowledgeSearchTool";

    private final KnowledgeBindingPort bindingPort;
    private final KnowledgeSearchPort searchPort;
    private final PlatformRunScopePort runScopePort;
    private final PlatformKnowledgeProperties properties;
    private final ObjectMapper objectMapper;

    public KnowledgeSearchTool(
            KnowledgeBindingPort bindingPort,
            KnowledgeSearchPort searchPort,
            PlatformRunScopePort runScopePort,
            PlatformKnowledgeProperties properties,
            ObjectMapper objectMapper
    ) {
        this.bindingPort = bindingPort;
        this.searchPort = searchPort;
        this.runScopePort = runScopePort;
        this.properties = properties == null ? new PlatformKnowledgeProperties() : properties;
        this.objectMapper = objectMapper;
    }

    @Override
    public ToolDefinition definition() {
        Map<String, Object> props = new LinkedHashMap<>();
        props.put("knowledgeBaseId", Map.of(
                "type", "integer", "description", "已绑定到当前租户的知识库 ID"));
        props.put("query", Map.of("type", "string", "description", "检索问题"));
        props.put("topK", Map.of(
                "type", "integer", "description", "返回条数（可选，默认 " + properties.resolvedDefaultTopK() + "）"));
        Map<String, Object> parameters = new LinkedHashMap<>();
        parameters.put("type", "object");
        parameters.put("properties", props);
        parameters.put("required", List.of("knowledgeBaseId", "query"));
        return new ToolDefinition(NAME, "在租户绑定的企业知识库中检索相关切片（只读，返回引用来源）", parameters);
    }

    @Override
    public ToolResult execute(Map<String, Object> arguments) {
        PlatformMemoryScope scope = runScopePort.currentScope()
                .orElseThrow(() -> new ToolExecutionException(
                        "knowledge search requires a platform run scope"));
        long knowledgeBaseId = requireLong(arguments, "knowledgeBaseId");
        String query = requireText(arguments, "query");
        int topK = resolveTopK(arguments);

        KnowledgeBinding binding = bindingPort
                .findByTenantAndKnowledgeBase(scope.tenantKey(), knowledgeBaseId)
                .filter(KnowledgeBinding::enabled)
                .orElseThrow(() -> new ToolExecutionException(
                        "knowledge base is not available for tenant: " + knowledgeBaseId));

        List<KnowledgeSearchHit> hits = searchPort.search(new KnowledgeSearchQuery(
                binding.enterpriseUserId(), knowledgeBaseId, query, topK));

        Map<String, Object> data = new LinkedHashMap<>();
        data.put("knowledgeBaseId", knowledgeBaseId);
        data.put("knowledgeBaseName", binding.name());
        List<Map<String, Object>> items = new ArrayList<>();
        for (KnowledgeSearchHit hit : hits) {
            Map<String, Object> item = new LinkedHashMap<>();
            item.put("knowledgeBaseId", hit.knowledgeBaseId());
            item.put("documentId", hit.documentId());
            item.put("documentName", hit.documentName());
            item.put("chunkIndex", hit.chunkIndex());
            item.put("content", hit.content());
            item.put("score", hit.score());
            items.add(item);
        }
        data.put("hits", items);
        log.info("[knowledge] tool search tenantKey={} knowledgeBaseId={} hits={}",
                scope.tenantKey(), knowledgeBaseId, items.size());
        return new ToolResult(toJson(data));
    }

    private int resolveTopK(Map<String, Object> arguments) {
        Object raw = arguments == null ? null : arguments.get("topK");
        if (raw == null || raw.toString().isBlank()) {
            return properties.resolvedDefaultTopK();
        }
        int requested;
        try {
            requested = raw instanceof Number number
                    ? number.intValue() : Integer.parseInt(raw.toString().trim());
        } catch (NumberFormatException ex) {
            throw new ToolExecutionException("topK must be an integer");
        }
        if (requested <= 0) {
            return properties.resolvedDefaultTopK();
        }
        return Math.min(requested, properties.resolvedMaxTopK());
    }

    private long requireLong(Map<String, Object> arguments, String key) {
        Object raw = arguments == null ? null : arguments.get(key);
        if (raw == null || raw.toString().isBlank()) {
            throw new ToolExecutionException(key + " must not be blank");
        }
        try {
            long value = raw instanceof Number number
                    ? number.longValue() : Long.parseLong(raw.toString().trim());
            if (value <= 0) {
                throw new ToolExecutionException(key + " must be positive");
            }
            return value;
        } catch (NumberFormatException ex) {
            throw new ToolExecutionException(key + " must be a number");
        }
    }

    private String requireText(Map<String, Object> arguments, String key) {
        Object raw = arguments == null ? null : arguments.get(key);
        String text = raw == null ? "" : raw.toString().trim();
        if (text.isEmpty()) {
            throw new ToolExecutionException(key + " must not be blank");
        }
        return text;
    }

    private String toJson(Map<String, Object> data) {
        try {
            return objectMapper.writeValueAsString(data);
        } catch (Exception ex) {
            throw new ToolExecutionException("failed to serialize knowledge tool result", ex);
        }
    }
}
