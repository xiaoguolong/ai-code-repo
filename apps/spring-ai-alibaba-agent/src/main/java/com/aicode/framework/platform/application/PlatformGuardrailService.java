package com.aicode.framework.platform.application;

import com.aicode.core.domain.model.GuardrailContext;
import com.aicode.core.domain.port.GuardrailPort;
import org.springframework.stereotype.Service;

import java.util.Map;

/**
 * Platform Run 入出参 Guardrail 编排：校验 input Map、脱敏 output Map。
 */
@Service
public class PlatformGuardrailService {

    private final GuardrailPort guardrailPort;

    public PlatformGuardrailService(GuardrailPort guardrailPort) {
        this.guardrailPort = guardrailPort;
    }

    /** 校验 Run 输入（注入、长度等）。 */
    public void validateRunInput(long userId, String agentKey, Map<String, Object> input) {
        if (!guardrailPort.enabled()) {
            return;
        }
        guardrailPort.validateInputMap(GuardrailContext.platformRun(userId, agentKey), input);
    }

    /** 脱敏 Run 输出 Map。 */
    public Map<String, Object> sanitizeRunOutput(long userId, String agentKey, Map<String, Object> output) {
        if (!guardrailPort.enabled()) {
            return output;
        }
        return guardrailPort.sanitizeOutputMap(GuardrailContext.platformRun(userId, agentKey), output);
    }

    /**
     * 脱敏流式文本片段（Week 31）。关闭时原样返回。
     *
     * @param userId   调用者
     * @param agentKey Agent 标识
     * @param text     待脱敏文本
     * @return 脱敏后文本
     */
    public String sanitizeStreamText(long userId, String agentKey, String text) {
        return guardrailPort.sanitizeTextOutput(GuardrailContext.platformRun(userId, agentKey), text);
    }
}
