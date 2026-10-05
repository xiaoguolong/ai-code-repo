package com.aicode.framework.observability.dto;

import com.aicode.framework.observability.domain.LangfuseStatusView;

import java.util.List;

/**
 * Langfuse 接入自检响应体（Week 18）。
 *
 * <p><b>不包含 secret key</b>：字段上就没有它；public key 只回掩码。可安全暴露给已登录用户。</p>
 *
 * @param enabled                 追踪开关
 * @param host                    Langfuse 根地址
 * @param otlpEndpoint            实际导出端点
 * @param projectId               项目 ID
 * @param environment             环境标识
 * @param release                 发布版本
 * @param captureContent          正文采集有效值（已计入 Guardrail 联锁）
 * @param maxContentChars         单条正文最大字符数
 * @param publicKeyMasked         掩码后的 public key
 * @param promptManagementEnabled 是否启用 Prompt 管理
 * @param promptLabel             Prompt 标签
 * @param promptCacheTtlSeconds   Prompt 缓存 TTL
 * @param configuredModels        已配置单价的模型
 * @param trace                   本次请求的链路与维度
 */
public record LangfuseStatusResponse(
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
     * 本次请求的链路与 Langfuse 维度。
     *
     * @param traceId            自研 traceId
     * @param otelTraceId        OTel traceId（= Langfuse traceId）
     * @param langfuseTraceName  trace 名
     * @param langfuseUserId     用户维度
     * @param langfuseSessionId  会话维度
     * @param langfuseTraceUrl   UI 深链
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

    /**
     * 由领域视图转换（Controller 只做协议转换）。
     *
     * @param view 领域视图，非 null
     * @return 响应体
     */
    public static LangfuseStatusResponse from(LangfuseStatusView view) {
        return new LangfuseStatusResponse(
                view.enabled(),
                view.host(),
                view.otlpEndpoint(),
                view.projectId(),
                view.environment(),
                view.release(),
                view.captureContent(),
                view.maxContentChars(),
                view.publicKeyMasked(),
                view.promptManagementEnabled(),
                view.promptLabel(),
                view.promptCacheTtlSeconds(),
                view.configuredModels(),
                new Trace(
                        view.trace().traceId(),
                        view.trace().otelTraceId(),
                        view.trace().langfuseTraceName(),
                        view.trace().langfuseUserId(),
                        view.trace().langfuseSessionId(),
                        view.trace().langfuseTraceUrl()));
    }
}
