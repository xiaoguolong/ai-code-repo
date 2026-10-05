package com.aicode.framework.observability.infrastructure;

import com.aicode.framework.observability.domain.ModelPrice;
import com.aicode.framework.observability.domain.ModelPriceCatalog;
import org.springframework.boot.context.properties.ConfigurationProperties;

import java.nio.charset.StandardCharsets;
import java.util.Base64;
import java.util.LinkedHashMap;
import java.util.Map;
import java.util.Optional;

/**
 * Langfuse 接入配置（Week 18）。
 *
 * <p>默认<b>全关</b>：{@code langfuse.enabled=false} 时不注册导出器与富化器、不采集正文、Prompt 走本地模板，
 * 单测与无 Langfuse 环境零网络、零开销。</p>
 *
 * <p>两个能力相互独立：追踪看 {@code enabled}，Prompt 管理看 {@code prompt.enabled}（只需要 host + 密钥）。
 * 密钥只从环境变量 / 模块 {@code .env} 读取，任何接口都不回显 secret key。</p>
 *
 * @param enabled        追踪总开关（默认 false）
 * @param host           Langfuse 根地址，如 {@code http://<langfuse-host>:3000}（不要带 /api 路径）
 * @param publicKey      项目 public key（Basic 认证用户名）
 * @param secretKey      项目 secret key（Basic 认证密码，禁止入库）
 * @param projectId      项目 ID，用于拼 UI 深链（可空）
 * @param environment    环境标识，写入 {@code langfuse.environment}
 * @param release        发布版本，写入 {@code langfuse.release}
 * @param captureContent 是否采集 Prompt / 输出正文（默认 false；需 guardrail 脱敏开启才生效）
 * @param maxContentChars 单条正文最大字符数（默认 2000）
 * @param timeoutMs      导出与 Prompt 拉取超时（默认 10000）
 * @param prompt         Prompt 管理子配置
 * @param modelPrices    模型价目表：模型名 → 每百万 token 单价（USD）
 */
@ConfigurationProperties(prefix = "langfuse")
public record LangfuseProperties(
        Boolean enabled,
        String host,
        String publicKey,
        String secretKey,
        String projectId,
        String environment,
        String release,
        Boolean captureContent,
        Integer maxContentChars,
        Integer timeoutMs,
        Prompt prompt,
        Map<String, ModelPriceProperties> modelPrices
) {

    /** 默认 Langfuse 本地地址。 */
    static final String DEFAULT_HOST = "http://localhost:3000";

    /** OTLP 摄取路径（Langfuse 只支持 HTTP/protobuf，不支持 gRPC）。 */
    static final String OTLP_PATH = "/api/public/otel/v1/traces";

    /** 默认环境标识。 */
    static final String DEFAULT_ENVIRONMENT = "local";

    /**
     * Prompt 管理子配置。
     *
     * @param enabled          是否从 Langfuse 拉取 Prompt（默认 false）
     * @param label            Prompt 标签（默认 production）
     * @param cacheTtlSeconds  客户端缓存 TTL 秒（默认 60，与官方 SDK 默认一致；0 表示不缓存）
     */
    public record Prompt(Boolean enabled, String label, Integer cacheTtlSeconds) {

        /** 是否启用（null 视为 false）。 */
        public boolean resolvedEnabled() {
            return enabled != null && enabled;
        }

        /** 标签，空则 production。 */
        public String resolvedLabel() {
            return label == null || label.isBlank() ? "production" : label.trim();
        }

        /** 缓存 TTL，null 取 60，负值取 0。 */
        public int resolvedCacheTtlSeconds() {
            if (cacheTtlSeconds == null) {
                return 60;
            }
            return Math.max(cacheTtlSeconds, 0);
        }
    }

    /**
     * 价目表行（每百万 token 单价，USD）。
     *
     * @param inputPer1m  提示单价
     * @param outputPer1m 补全单价
     */
    public record ModelPriceProperties(Double inputPer1m, Double outputPer1m) {

        /** 转领域模型。 */
        public ModelPrice toDomain() {
            return new ModelPrice(nonNull(inputPer1m), nonNull(outputPer1m));
        }

        private static double nonNull(Double value) {
            return value == null ? 0.0 : value;
        }
    }

    /** 追踪开关（null 视为关闭）。 */
    public boolean resolvedEnabled() {
        return enabled != null && enabled;
    }

    /** 根地址：去空格与结尾斜杠，空则用本地默认值。 */
    public String resolvedHost() {
        if (host == null || host.isBlank()) {
            return DEFAULT_HOST;
        }
        String trimmed = host.trim();
        while (trimmed.endsWith("/")) {
            trimmed = trimmed.substring(0, trimmed.length() - 1);
        }
        return trimmed;
    }

    /** 环境标识。 */
    public String resolvedEnvironment() {
        return environment == null || environment.isBlank() ? DEFAULT_ENVIRONMENT : environment.trim();
    }

    /** 发布版本，未配置为空串。 */
    public String resolvedRelease() {
        return release == null ? "" : release.trim();
    }

    /** 是否采集正文（是否真正生效还要看 guardrail 联锁，见 {@code LangfuseContentPolicy}）。 */
    public boolean resolvedCaptureContent() {
        return captureContent != null && captureContent;
    }

    /** 单条正文最大字符数。 */
    public int resolvedMaxContentChars() {
        return maxContentChars == null || maxContentChars <= 0 ? 2000 : maxContentChars;
    }

    /** 超时毫秒。 */
    public int resolvedTimeoutMs() {
        return timeoutMs == null || timeoutMs <= 0 ? 10_000 : timeoutMs;
    }

    /** Prompt 子配置（未配置时给出默认值，避免调用方判空）。 */
    public Prompt resolvedPrompt() {
        return prompt == null ? new Prompt(false, "production", 60) : prompt;
    }

    /** OTLP 导出端点（端口是 Langfuse 的 3000，与通用 collector 的 4318 不同）。 */
    public String otlpEndpoint() {
        return resolvedHost() + OTLP_PATH;
    }

    /** 是否配置了可用的 API Key。 */
    public boolean hasCredentials() {
        return publicKey != null && !publicKey.isBlank() && secretKey != null && !secretKey.isBlank();
    }

    /**
     * Basic 认证头值（public key 作用户名、secret key 作密码）。
     *
     * @return 形如 {@code Basic xxx}；未配置密钥时返回空串
     */
    public String authorizationHeader() {
        if (!hasCredentials()) {
            return "";
        }
        String raw = publicKey.trim() + ":" + secretKey.trim();
        return "Basic " + Base64.getEncoder().encodeToString(raw.getBytes(StandardCharsets.UTF_8));
    }

    /**
     * 校验密钥是否齐备，缺失时抛出配置错误（启动即失败，好过「数据悄悄不出现」）。
     *
     * @throws IllegalStateException 缺少 public key 或 secret key 时
     */
    public void requireCredentials() {
        if (!hasCredentials()) {
            throw new IllegalStateException(
                    "langfuse.public-key / langfuse.secret-key are required when langfuse features are enabled");
        }
    }

    /** public key 掩码（只保留前 6 位），供自检接口展示。 */
    public String publicKeyMasked() {
        if (publicKey == null || publicKey.isBlank()) {
            return "";
        }
        String trimmed = publicKey.trim();
        return trimmed.length() <= 6 ? "***" : trimmed.substring(0, 6) + "***";
    }

    /**
     * 价目表（剔除无效行）。
     *
     * @return 价目表，永不为 null
     */
    public ModelPriceCatalog modelPriceCatalog() {
        if (modelPrices == null || modelPrices.isEmpty()) {
            return ModelPriceCatalog.empty();
        }
        Map<String, ModelPrice> prices = new LinkedHashMap<>();
        modelPrices.forEach((model, row) -> {
            if (row != null) {
                prices.put(model, row.toDomain());
            }
        });
        return new ModelPriceCatalog(prices);
    }

    /**
     * 构造 Langfuse UI 深链。
     *
     * <p>只在「追踪开启 + 配了 project ID + traceId 非空」时返回，避免给出必然 404 的链接。</p>
     *
     * @param otelTraceId OTel traceId（Langfuse 用它作为 trace 主键）
     * @return 深链；不可构造时为空
     */
    public Optional<String> traceUrl(String otelTraceId) {
        if (!resolvedEnabled() || projectId == null || projectId.isBlank()
                || otelTraceId == null || otelTraceId.isBlank()) {
            return Optional.empty();
        }
        return Optional.of(resolvedHost() + "/project/" + projectId.trim() + "/traces/" + otelTraceId.trim());
    }
}
