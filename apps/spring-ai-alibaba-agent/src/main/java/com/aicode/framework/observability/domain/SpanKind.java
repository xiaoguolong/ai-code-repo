package com.aicode.framework.observability.domain;

/**
 * span 种类（Week 17）。
 *
 * <p>用于统一 span 语义。本枚举<b>刻意不引用任何 Micrometer / OTel 类型</b>：
 * 领域与应用层只使用本枚举，框架类型与「枚举 → {@code Span.Kind}」的映射只在适配器内出现
 * （规范 3.2：框架类型不得越界到领域层）。</p>
 */
public enum SpanKind {

    /** 入站 HTTP 请求（服务端 span）。 */
    SERVER,

    /** 一次 Agent 执行（含 RBAC、终态落库与审计）。 */
    AGENT_RUN,

    /** 一次大模型调用，成本核算的最小单位。 */
    LLM_CALL,

    /** 一次工具调用。 */
    TOOL_CALL
}
