package com.aicode.core.infrastructure.security;

import com.aicode.core.domain.model.GuardrailContext;
import com.aicode.core.domain.model.ToolCall;
import com.aicode.core.domain.model.ToolDefinition;
import com.aicode.core.domain.model.ToolResult;
import com.aicode.core.domain.port.GuardrailPort;
import com.aicode.core.domain.port.ToolPort;
import com.fasterxml.jackson.core.type.TypeReference;
import com.fasterxml.jackson.databind.ObjectMapper;

import java.util.List;
import java.util.Map;

/**
 * {@link ToolPort} 装饰器：在 RBAC 之后校验 Tool 参数键白名单与注入，并对返回内容脱敏。
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
        String sanitized = guardrailPort.sanitizeToolOutput(context, call.name(), result.output());
        return new ToolResult(sanitized);
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
