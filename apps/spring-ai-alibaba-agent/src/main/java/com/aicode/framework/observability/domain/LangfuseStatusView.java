package com.aicode.framework.observability.domain;

import java.util.List;

/**
 * Langfuse 接入自检视图（Week 18）。
 *
 * <p>用于回答「开关生效了吗、端点对不对、维度写对了吗」。<b>不含任何密钥</b>：
 * public key 只给掩码，secret key 连字段都不存在（不是靠约定不填，而是类型上就没有）。</p>
 *
 * @param enabled                  追踪开关
 * @param host                     Langfuse 根地址
 * @param otlpEndpoint             实际导出端点
 * @param projectId                项目 ID（用于拼 UI 深链，可为空）
 * @param environment              环境标识
 * @param release                  发布版本
 * @param captureContent           是否采集 Prompt / 输出正文（已反映 guardrail 联锁后的<b>有效值</b>）
 * @param maxContentChars          单条正文最大字符数
 * @param publicKeyMasked          掩码后的 public key
 * @param promptManagementEnabled  是否从 Langfuse 拉取 Prompt
 * @param promptLabel              Prompt 标签
 * @param promptCacheTtlSeconds    Prompt 客户端缓存 TTL
 * @param configuredModels         已配置单价的模型
 * @param trace                    本次请求的链路与维度
 */
public record LangfuseStatusView(
        boolean enabled,
        String host,
        String otlpEndpoint,
        String projectId,
        String environment,
        String release,
        boolean captureContent,
        int maxContentChars,
        String publicKeyMasked,
        boolean promptManagementEnabled,
        String promptLabel,
        int promptCacheTtlSeconds,
        List<String> configuredModels,
        Trace trace
) {

    /**
     * 本次请求的链路上下文与 Langfuse 维度。
     *
     * @param traceId          自研 traceId（= 响应头 {@code X-Trace-Id}）
     * @param otelTraceId      OTel traceId（= Langfuse 的 traceId）
     * @param langfuseTraceName  trace 名，未开启或无维度时为 null
     * @param langfuseUserId     用户维度，可为 null
     * @param langfuseSessionId  会话维度，可为 null
     * @param langfuseTraceUrl   Langfuse UI 深链，无法构造时为 null
     */
    public record Trace(
            String traceId,
            String otelTraceId,
            String langfuseTraceName,
            String langfuseUserId,
            String langfuseSessionId,
            String langfuseTraceUrl
    ) {
    }
}
