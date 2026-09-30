package com.aicode.core.domain.port;

import com.aicode.core.domain.exception.GuardrailViolationException;
import com.aicode.core.domain.model.GuardrailContext;

import java.util.Map;

/**
 * 内容安全护栏端口：输入校验、Prompt 注入拦截、输出 PII 脱敏、Tool 参数白名单。
 * 与身份授权（RBAC）正交，Week 14 落地。
 */
public interface GuardrailPort {

    /**
     * 校验单字段文本（长度、注入模式等）。
     *
     * @throws GuardrailViolationException 违例时
     */
    void validateTextInput(GuardrailContext context, String fieldName, String text);

    /**
     * 递归校验 Run 输入 Map 中所有字符串值。
     *
     * @throws GuardrailViolationException 违例时
     */
    void validateInputMap(GuardrailContext context, Map<String, Object> input);

    /**
     * 对输出文本做 PII 脱敏。
     */
    String sanitizeTextOutput(GuardrailContext context, String text);

    /**
     * 递归脱敏输出 Map 中所有字符串值，返回新 Map。
     */
    Map<String, Object> sanitizeOutputMap(GuardrailContext context, Map<String, Object> output);

    /**
     * 校验 Tool 调用参数键是否在白名单内，并校验字符串值。
     *
     * @throws GuardrailViolationException 违例时
     */
    void validateToolArguments(GuardrailContext context, String toolName, Map<String, Object> arguments);

    /**
     * 对 Tool 返回内容做脱敏。
     */
    String sanitizeToolOutput(GuardrailContext context, String toolName, String content);

    /** 是否启用 Guardrail（配置关闭时全链路放行）。 */
    boolean enabled();
}
