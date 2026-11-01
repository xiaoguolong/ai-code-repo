package com.aicode.core.infrastructure.security;

import com.aicode.core.domain.model.GuardrailContext;
import com.aicode.core.domain.model.ToolCall;
import com.aicode.core.domain.model.ToolDefinition;
import com.aicode.core.domain.model.ToolResult;
import com.aicode.core.domain.port.GuardrailPort;
import com.aicode.core.domain.port.ToolPort;
import com.fasterxml.jackson.core.type.TypeReference;
import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.ObjectMapper;
import com.fasterxml.jackson.databind.node.ArrayNode;
import com.fasterxml.jackson.databind.node.ObjectNode;
import com.fasterxml.jackson.databind.node.TextNode;

import java.util.List;
import java.util.Map;

/**
 * {@link ToolPort} 装饰器：在 RBAC 之后校验 Tool 参数键白名单与注入，并对返回内容脱敏。
 *
 * <p><b>Week 31 真机修复</b>：脱敏不能对<b>整段 JSON 文本</b>做正则替换——工具结果里
 * {@code "score":0.486819416284103} 这类 15 位小数会被身份证规则命中，替换后带上 {@code *}，
 * 直接变成非法 JSON（knowledge-qa 报 "invalid knowledge tool result"）。</p>
 *
 * <p>现在的策略：能解析成 JSON 就<b>只脱敏字符串值</b>（递归对象/数组），数字与布尔原样保留；
 * 解析失败才退回整段文本脱敏（例如纯文本工具返回）。</p>
 */
public class GuardrailToolPort implements ToolPort {

    private final ToolPort delegate;
    private final GuardrailPort guardrailPort;
    private final ObjectMapper objectMapper;

    public GuardrailToolPort(ToolPort delegate, GuardrailPort guardrailPort, ObjectMapper objectMapper) {
        this.delegate = delegate;
        this.guardrailPort = guardrailPort;
        this.objectMapper = objectMapper;
    }

    @Override
    public List<ToolDefinition> definitions() {
        return delegate.definitions();
    }

    @Override
    public ToolResult execute(ToolCall call) {
        if (guardrailPort.enabled()) {
            GuardrailContext context = GuardrailContext.tool(null, call.name());
            guardrailPort.validateToolArguments(context, call.name(), parseArguments(call.arguments()));
        }
        ToolResult result = delegate.execute(call);
        if (!guardrailPort.enabled()) {
            return result;
        }
        GuardrailContext context = GuardrailContext.tool(null, call.name());
        return new ToolResult(sanitizeOutput(context, call.name(), result.output()));
    }

    /**
     * 优先按 JSON 结构脱敏，保留数字/布尔；非 JSON 文本退回整体脱敏。
     */
    private String sanitizeOutput(GuardrailContext context, String toolName, String output) {
        if (output == null || output.isBlank()) {
            return output;
        }
        JsonNode root;
        try {
            root = objectMapper.readTree(output);
        } catch (Exception ex) {
            return guardrailPort.sanitizeToolOutput(context, toolName, output);
        }
        if (root == null || root.isMissingNode()) {
            return guardrailPort.sanitizeToolOutput(context, toolName, output);
        }
        try {
            return objectMapper.writeValueAsString(sanitizeNode(context, root));
        } catch (Exception ex) {
            return guardrailPort.sanitizeToolOutput(context, toolName, output);
        }
    }

    /** 递归：只替换文本节点，对象键、数字、布尔、null 原样保留。 */
    private JsonNode sanitizeNode(GuardrailContext context, JsonNode node) {
        if (node == null) {
            return null;
        }
        if (node.isTextual()) {
            return TextNode.valueOf(guardrailPort.sanitizeTextOutput(context, node.asText()));
        }
        if (node.isObject()) {
            ObjectNode copy = objectMapper.createObjectNode();
            node.fields().forEachRemaining(entry ->
                    copy.set(entry.getKey(), sanitizeNode(context, entry.getValue())));
            return copy;
        }
        if (node.isArray()) {
            ArrayNode copy = objectMapper.createArrayNode();
            node.forEach(item -> copy.add(sanitizeNode(context, item)));
            return copy;
        }
        return node;
    }

    private Map<String, Object> parseArguments(String arguments) {
        if (arguments == null || arguments.isBlank()) {
            return Map.of();
        }
        try {
            return objectMapper.readValue(arguments, new TypeReference<>() {
            });
        } catch (Exception ex) {
            return Map.of();
        }
    }
}
