package com.aicode.framework.observability;

import com.aicode.core.domain.model.GuardrailContext;
import com.aicode.core.domain.port.GuardrailPort;

import java.util.Map;
import java.util.regex.Pattern;

/**
 * 测试用 Guardrail 假实现（Week 18）：只做手机号掩码并记录调用次数，
 * 用于断言「正文在写入可观测系统之前确实经过了脱敏」以及「guardrail 关闭时采集自动失效」。
 */
public final class GuardrailStub implements GuardrailPort {

    private static final Pattern MOBILE = Pattern.compile("(1[3-9]\\d)(\\d{4})(\\d{4})");

    private final boolean enabled;
    private int sanitizeCalls;

    public GuardrailStub(boolean enabled) {
        this.enabled = enabled;
    }

    /** 启用脱敏的实例（生产默认口径）。 */
    public static GuardrailStub active() {
        return new GuardrailStub(true);
    }

    /** 关闭 Guardrail 的实例：用于验证内容采集 fail-safe 到关闭。 */
    public static GuardrailStub inactive() {
        return new GuardrailStub(false);
    }

    /** 已发生的脱敏调用次数。 */
    public int sanitizeCalls() {
        return sanitizeCalls;
    }

    @Override
    public void validateTextInput(GuardrailContext context, String fieldName, String text) {
    }

    @Override
    public void validateInputMap(GuardrailContext context, Map<String, Object> input) {
    }

    @Override
    public String sanitizeTextOutput(GuardrailContext context, String text) {
        sanitizeCalls++;
        if (text == null || !enabled) {
            return text;
        }
        return MOBILE.matcher(text).replaceAll("$1****$3");
    }

    @Override
    public Map<String, Object> sanitizeOutputMap(GuardrailContext context, Map<String, Object> output) {
        return output;
    }

    @Override
    public void validateToolArguments(GuardrailContext context, String toolName, Map<String, Object> arguments) {
    }

    @Override
    public String sanitizeToolOutput(GuardrailContext context, String toolName, String content) {
        return sanitizeTextOutput(context, content);
    }

    @Override
    public boolean enabled() {
        return enabled;
    }
}
