package com.aicode.framework.observability.domain;

/**
 * span 属性键名与指标标签名常量（Week 17）。
 *
 * <p>集中定义，避免魔法字符串散落（规范 5.5）。LLM 相关键名对齐
 * <a href="https://opentelemetry.io/docs/specs/semconv/gen-ai/">OpenTelemetry GenAI 语义约定</a>，
 * 便于后续接入 Langfuse（第 18 周）等专业 LLM 观测平台时字段直接对上。</p>
 *
 * <p><b>白名单原则</b>：这里出现的键都只承载标识 / 模型名 / 长度 / token 数 / 状态，
 * 不承载 Prompt 或输出正文、患者身份等敏感内容。</p>
 */
public final class ObservabilityAttributes {

    /** span 自身的种类编码（值取 {@link SpanKind#name()}）。 */
    public static final String SPAN_KIND = "span.kind";

    /** 平台 Agent 标识（低基数，可作指标标签）。 */
    public static final String AGENT_KEY = "agent.key";

    /** Agent 类型（低基数，可作指标标签）。 */
    public static final String AGENT_TYPE = "agent.type";

    /** 执行记录 ID（高基数，只作 span 属性，禁止作指标标签）。 */
    public static final String EXECUTION_ID = "execution.id";

    /** 执行终态（COMPLETED / FAILED）。 */
    public static final String EXECUTION_STATUS = "execution.status";

    /** 调用者用户 ID（高基数，只作 span 属性）。 */
    public static final String USER_ID = "user.id";

    /** 模型厂商标识（语义约定 {@code gen_ai.system}）。 */
    public static final String GEN_AI_SYSTEM = "gen_ai.system";

    /** 请求模型名（低基数，可作指标标签）。 */
    public static final String GEN_AI_REQUEST_MODEL = "gen_ai.request.model";

    /** 提示 token 数。 */
    public static final String GEN_AI_USAGE_PROMPT_TOKENS = "gen_ai.usage.prompt_tokens";

    /** 补全 token 数。 */
    public static final String GEN_AI_USAGE_COMPLETION_TOKENS = "gen_ai.usage.completion_tokens";

    /** 总 token 数（span 属性用；Prometheus 出口名为 {@code llm_tokens_total}，按 token_type 分两条）。 */
    public static final String GEN_AI_USAGE_TOTAL_TOKENS = "gen_ai.usage.total_tokens";

    /** 结束原因（stop / tool_calls / length / unknown）。 */
    public static final String GEN_AI_RESPONSE_FINISH_REASON = "gen_ai.response.finish_reason";

    /** 本轮模型返回的工具调用数量。 */
    public static final String GEN_AI_TOOL_CALL_COUNT = "gen_ai.tool.call.count";

    /** 工具名（低基数，可作指标标签）。 */
    public static final String TOOL_NAME = "tool.name";

    /** 工具调用 ID（高基数，只作 span 属性）。 */
    public static final String TOOL_CALL_ID = "tool.call.id";

    /** 工具结果字符数（只记长度，不记内容）。 */
    public static final String TOOL_RESULT_CHARS = "tool.result.chars";

    /** 请求 / 调用耗时（毫秒，仅 span 属性用）。 */
    public static final String DURATION_MS = "duration.ms";

    /** 调用结果标签：成功。 */
    public static final String OUTCOME_SUCCESS = "SUCCESS";

    /** 调用结果标签：失败。 */
    public static final String OUTCOME_FAILURE = "FAILURE";

    /** 调用结果标签键。 */
    public static final String LLM_OUTCOME = "llm.outcome";

    /** 工具调用结果标签键。 */
    public static final String TOOL_OUTCOME = "tool.outcome";

    /** token 类型标签键。 */
    public static final String TOKEN_TYPE = "token.type";

    /** token 类型标签值：提示。 */
    public static final String TOKEN_TYPE_PROMPT = "prompt";

    /** token 类型标签值：补全。 */
    public static final String TOKEN_TYPE_COMPLETION = "completion";

    private ObservabilityAttributes() {
    }
}
