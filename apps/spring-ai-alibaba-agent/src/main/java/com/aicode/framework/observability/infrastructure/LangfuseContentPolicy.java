package com.aicode.framework.observability.infrastructure;

import com.aicode.core.domain.model.ChatMessage;
import com.aicode.core.domain.model.ChatResult;
import com.aicode.core.domain.model.GuardrailContext;
import com.aicode.core.domain.model.ToolCall;
import com.aicode.core.domain.model.ToolResult;
import com.aicode.core.domain.port.GuardrailPort;
import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.ObjectMapper;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import java.util.ArrayList;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;

/**
 * Langfuse 正文采集策略（Week 18）。
 *
 * <p>安全设计（三条硬约束）：</p>
 * <ol>
 *   <li><b>默认关闭</b>：{@code langfuse.capture-content=false} 时所有方法返回 null，一个正文字段都不写；</li>
 *   <li><b>必须脱敏</b>：开启后正文先经 {@link GuardrailPort#sanitizeTextOutput} 做 PII 脱敏，
 *       再按 {@code langfuse.max-content-chars} 截断（先脱敏后截断，避免把手机号截成半截数字串）；</li>
 *   <li><b>fail-safe</b>：若请求了采集但 Guardrail 未启用（未脱敏能力不可用），则<b>采集自动失效</b>并打 WARN，
 *       绝不让未脱敏的患者数据进可观测系统。</li>
 * </ol>
 *
 * <p>截断不仅是为了隐私，也为了让 OTLP 批次保持在 Langfuse 的限制内（单批 3.5 MB）。</p>
 */
public class LangfuseContentPolicy {

    private static final Logger log = LoggerFactory.getLogger(LangfuseContentPolicy.class);

    /** 采集通道标识，进入 Guardrail 上下文（便于审计与策略分支）。 */
    private static final GuardrailContext CONTEXT = new GuardrailContext("observability", null, null);

    /** 截断标记，便于在 Langfuse 上识别「这里被裁过」。 */
    static final String TRUNCATED_SUFFIX = "…(truncated)";

    private final boolean enabled;
    private final int maxChars;
    private final GuardrailPort guardrail;
    private final ObjectMapper objectMapper;

    /**
     * @param properties   Langfuse 配置
     * @param guardrail    脱敏能力（为 null 视为不可用）
     * @param objectMapper JSON 序列化器
     */
    public LangfuseContentPolicy(LangfuseProperties properties, GuardrailPort guardrail, ObjectMapper objectMapper) {
        this.guardrail = guardrail;
        this.objectMapper = objectMapper;
        this.maxChars = properties.resolvedMaxContentChars();
        boolean requested = properties.resolvedCaptureContent();
        boolean sanitizerAvailable = guardrail != null && guardrail.enabled();
        if (requested && !sanitizerAvailable) {
            log.warn("[langfuse] capture-content=true 但 Guardrail 未启用，正文采集已按关闭处理（禁止未脱敏数据出站）");
        }
        this.enabled = requested && sanitizerAvailable;
    }

    /** 正文采集是否真正生效（已计入 Guardrail 联锁）。 */
    public boolean enabled() {
        return enabled;
    }

    /** 消息列表 JSON：{@code [{"role":"user","content":"..."}]}。 */
    public String messagesJson(List<ChatMessage> messages) {
        if (!enabled || messages == null || messages.isEmpty()) {
            return null;
        }
        List<Map<String, Object>> rows = new ArrayList<>();
        for (ChatMessage message : messages) {
            if (message == null) {
                continue;
            }
            Map<String, Object> row = new LinkedHashMap<>();
            row.put("role", message.role() == null ? "unknown" : message.role().apiValue());
            if (message.toolCallId() != null && !message.toolCallId().isBlank()) {
                row.put("toolCallId", message.toolCallId());
            }
            row.put("content", sanitize(message.content()));
            if (!message.toolCalls().isEmpty()) {
                row.put("toolCalls", message.toolCalls().stream().map(ToolCall::name).toList());
            }
            rows.add(row);
        }
        return write(rows);
    }

    /** 模型输出 JSON：{@code {"content":"...","finishReason":"STOP","toolCalls":[...]}}。 */
    public String completionJson(ChatResult result) {
        if (!enabled || result == null) {
            return null;
        }
        Map<String, Object> row = new LinkedHashMap<>();
        row.put("content", sanitize(result.content()));
        row.put("finishReason", String.valueOf(result.finishReason()));
        if (!result.toolCalls().isEmpty()) {
            List<Map<String, Object>> calls = new ArrayList<>();
            for (ToolCall call : result.toolCalls()) {
                Map<String, Object> row2 = new LinkedHashMap<>();
                row2.put("name", safe(call.name()));
                row2.put("arguments", sanitize(call.arguments()));
                calls.add(row2);
            }
            row.put("toolCalls", calls);
        }
        return write(row);
    }

    /** 工具入参 JSON：{@code {"name":"...","arguments":{...}}}。 */
    public String toolInputJson(ToolCall call) {
        if (!enabled || call == null) {
            return null;
        }
        Map<String, Object> row = new LinkedHashMap<>();
        row.put("name", safe(call.name()));
        if (call.id() != null && !call.id().isBlank()) {
            row.put("id", call.id());
        }
        row.put("arguments", jsonOrText(sanitize(call.arguments())));
        return write(row);
    }

    /** 工具出参 JSON：{@code {"output":{...}}}。 */
    public String toolOutputJson(ToolResult result) {
        if (!enabled || result == null) {
            return null;
        }
        Map<String, Object> row = new LinkedHashMap<>();
        row.put("output", jsonOrText(sanitize(result.output())));
        return write(row);
    }

    /** 先脱敏、再截断；null 保持 null。 */
    private String sanitize(String text) {
        if (text == null) {
            return null;
        }
        String masked = guardrail.sanitizeTextOutput(CONTEXT, text);
        if (masked == null) {
            return null;
        }
        if (masked.length() <= maxChars) {
            return masked;
        }
        return masked.substring(0, maxChars) + TRUNCATED_SUFFIX;
    }

    /** 能解析成 JSON 就返回结构化节点（Langfuse 展示更可读），否则原样返回文本。 */
    private Object jsonOrText(String text) {
        if (text == null || text.isBlank()) {
            return text;
        }
        try {
            JsonNode node = objectMapper.readTree(text);
            return node == null ? text : node;
        } catch (Exception ex) {
            return text;
        }
    }

    private String write(Object value) {
        try {
            return objectMapper.writeValueAsString(value);
        } catch (Exception ex) {
            log.warn("[langfuse] 正文序列化失败，本字段按不下发处理: {}", ex.getMessage());
            return null;
        }
    }

    private String safe(String value) {
        return value == null ? "" : value;
    }
}
