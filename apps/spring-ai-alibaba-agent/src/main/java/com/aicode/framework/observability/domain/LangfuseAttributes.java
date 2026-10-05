package com.aicode.framework.observability.domain;

/**
 * Langfuse 专有属性键与观测类型常量（Week 18）。
 *
 * <p>集中定义，禁止在业务代码里散落魔法字符串（规范 5.5）。这些键名来自 Langfuse 官方
 * OpenTelemetry 属性映射表，改动即为协议变更，必须同时改文档与测试。</p>
 *
 * <p><b>格式红线</b>：{@link #OBSERVATION_USAGE_DETAILS} / {@link #OBSERVATION_COST_DETAILS} /
 * {@link #OBSERVATION_MODEL_PARAMETERS} / {@link #OBSERVATION_INPUT} / {@link #OBSERVATION_OUTPUT}
 * 的值都是<b>一个 JSON 字符串</b>，不是扁平键——Langfuse 对非 string 值会告警并丢弃。</p>
 *
 * <p><b>观测类型必须显式写</b>：Langfuse 只在没有识别到显式 {@link #OBSERVATION_TYPE} 时才用 model 兜底
 * 推断为 generation；显式值永远胜出。</p>
 */
public final class LangfuseAttributes {

    /** trace 名（值如 {@code agent:medical-assistant}）。 */
    public static final String TRACE_NAME = "langfuse.trace.name";

    /** trace 级用户 ID（官方要求传播到每个 span，否则无法按用户过滤）。 */
    public static final String USER_ID = "langfuse.user.id";

    /** trace 级会话 ID（本项目取 Run 入参 patientId）。 */
    public static final String SESSION_ID = "langfuse.session.id";

    /** trace 级标签（字符串数组）。 */
    public static final String TRACE_TAGS = "langfuse.trace.tags";

    /** trace 级元数据前缀（可过滤）。 */
    public static final String TRACE_METADATA_PREFIX = "langfuse.trace.metadata.";

    /** 环境（local / vm-lab / prod 等）。 */
    public static final String ENVIRONMENT = "langfuse.environment";

    /** 发布版本（用于对比不同版本的观测质量）。 */
    public static final String RELEASE = "langfuse.release";

    /** 观测类型（span / generation / tool / agent / event / ...）。 */
    public static final String OBSERVATION_TYPE = "langfuse.observation.type";

    /** 观测级别（WARNING / ERROR）。 */
    public static final String OBSERVATION_LEVEL = "langfuse.observation.level";

    /** 观测输入（JSON 字符串；仅内容采集开启时写入）。 */
    public static final String OBSERVATION_INPUT = "langfuse.observation.input";

    /** 观测输出（JSON 字符串；仅内容采集开启时写入）。 */
    public static final String OBSERVATION_OUTPUT = "langfuse.observation.output";

    /** 模型名（generation 观测用）。 */
    public static final String OBSERVATION_MODEL_NAME = "langfuse.observation.model.name";

    /** 模型参数（JSON 字符串，如 temperature / max_tokens）。 */
    public static final String OBSERVATION_MODEL_PARAMETERS = "langfuse.observation.model.parameters";

    /** Token 用量明细（JSON 字符串，互斥桶 input / output / total）。 */
    public static final String OBSERVATION_USAGE_DETAILS = "langfuse.observation.usage_details";

    /** 成本明细（JSON 字符串，USD）。 */
    public static final String OBSERVATION_COST_DETAILS = "langfuse.observation.cost_details";

    /** 关联的 Langfuse Prompt 名。 */
    public static final String OBSERVATION_PROMPT_NAME = "langfuse.observation.prompt.name";

    /** 关联的 Langfuse Prompt 版本号。 */
    public static final String OBSERVATION_PROMPT_VERSION = "langfuse.observation.prompt.version";

    /** 观测级元数据前缀。 */
    public static final String OBSERVATION_METADATA_PREFIX = "langfuse.observation.metadata.";

    /** 观测类型值：Agent 执行。 */
    public static final String TYPE_AGENT = "agent";

    /** 观测类型值：模型生成。 */
    public static final String TYPE_GENERATION = "generation";

    /** 观测类型值：工具调用。 */
    public static final String TYPE_TOOL = "tool";

    /** 观测类型值：普通 span。 */
    public static final String TYPE_SPAN = "span";

    /** 观测级别值：错误。 */
    public static final String LEVEL_ERROR = "ERROR";

    private LangfuseAttributes() {
    }
}
