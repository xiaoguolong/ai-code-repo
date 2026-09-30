package com.aicode.core.domain.exception;

/**
 * Guardrail 校验失败（Prompt 注入、超长输入、Tool 参数键非白名单等）。
 * 与 RBAC 的 403 正交，通常映射为 HTTP 400。
 */
public class GuardrailViolationException extends RuntimeException {

    public GuardrailViolationException(String message) {
        super(message);
    }
}
