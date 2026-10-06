package com.aicode.gateway.application;

import com.aicode.gateway.domain.exception.GatewayUpstreamException;
import com.aicode.gateway.dto.GatewayErrorCode;
import com.aicode.gateway.domain.model.AgentSummary;
import com.aicode.gateway.domain.model.SessionPrincipal;
import com.aicode.gateway.domain.port.GatewayCachePort;
import com.aicode.gateway.domain.port.PlatformClientPort;
import com.aicode.gateway.infrastructure.config.GatewayCatalogProperties;
import com.fasterxml.jackson.core.type.TypeReference;
import com.fasterxml.jackson.databind.ObjectMapper;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.stereotype.Service;
import reactor.core.publisher.Mono;

import java.time.Duration;
import java.util.List;
import java.util.Map;

/**
 * 网关 Agent 目录与执行用例（Week 19）。
 *
 * <p>目录查询走「缓存优先」：命中原样返回；未命中（或缓存故障）经 Feign 读平台并回填缓存。
 * 这条路径同时是本周 Feign 监控与 Redis 监控的载体。</p>
 *
 * <p><b>降级约定</b>：Redis 不可用时只失去缓存，请求仍应成功（缓存不是单点）；
 * 平台不可用则必须如实报 502，不能拿缓存当答案糊弄调用方。</p>
 */
@Service
public class GatewayAgentUseCase {

    private static final Logger log = LoggerFactory.getLogger(GatewayAgentUseCase.class);

    private final PlatformClientPort platformClient;
    private final GatewayCachePort cache;
    private final GatewayCatalogProperties properties;
    private final ObjectMapper objectMapper;

    /**
     * @param platformClient 平台出站端口
     * @param cache          缓存端口
     * @param properties     目录缓存配置
     * @param objectMapper   JSON 序列化器（缓存值编解码）
     */
    public GatewayAgentUseCase(
            PlatformClientPort platformClient,
            GatewayCachePort cache,
            GatewayCatalogProperties properties,
            ObjectMapper objectMapper
    ) {
        this.platformClient = platformClient;
        this.cache = cache;
        this.properties = properties;
        this.objectMapper = objectMapper;
    }

    /**
     * 读取 Agent 目录（缓存优先）。
     *
     * @param principal 当前调用者身份（提供平台 token）
     * @param traceId   业务链路 ID
     * @param refresh   true 表示跳过缓存强制回源
     * @return Agent 列表；无数据时为空列表
     */
    public Mono<List<AgentSummary>> listAgents(SessionPrincipal principal, String traceId, boolean refresh) {
        String key = cacheKey();
        // 必须 defer：否则每次调用都会「先建好回源 Mono」，即使命中缓存也会真的打平台，
        // 缓存等于没有（这是本用例最容易写错的一处，已有单测锁死）。
        Mono<List<AgentSummary>> fromPlatform = Mono.defer(() -> platformClient
                .listAgents(principal.platformToken(), traceId)
                .flatMap(agents -> cache.put(key, encode(agents),
                                Duration.ofSeconds(properties.resolvedCacheTtlSeconds()))
                        // 回填失败不影响本次结果
                        .onErrorResume(ex -> {
                            log.warn("[gateway] 目录缓存回填失败，忽略：{}", ex.toString());
                            return Mono.empty();
                        })
                        .thenReturn(agents)));
        if (refresh) {
            return fromPlatform;
        }
        return cache.get(key)
                .flatMap(cached -> {
                    List<AgentSummary> decoded = decode(cached);
                    return decoded == null ? Mono.empty() : Mono.just(decoded);
                })
                .switchIfEmpty(Mono.defer(() -> {
                    log.debug("[gateway] 目录缓存未命中，回源平台");
                    return fromPlatform;
                }))
                // 缓存读取失败只降级为「未命中」，不能让 Redis 故障变成业务失败
                .onErrorResume(ex -> {
                    log.warn("[gateway] 目录缓存读取失败，降级回源：{}", ex.toString());
                    return fromPlatform;
                });
    }

    /**
     * 触发一次 Agent 执行（透传平台结果）。
     *
     * <p>执行前用 Redis SETNX 抢一个幂等键：抢占结果只记录（{@code gw.idempotent.hit} 标签），
     * 本周不做「重复请求直接拒绝」——那需要与平台确认幂等语义，属第 20 周范围。</p>
     *
     * @param principal 当前调用者身份
     * @param traceId   业务链路 ID
     * @param agentKey  Agent 标识
     * @param input     Run 入参
     * @return 平台返回的执行结果
     */
    public Mono<Map<String, Object>> runAgent(
            SessionPrincipal principal, String traceId, String agentKey, Map<String, Object> input) {
        if (agentKey == null || agentKey.isBlank()) {
            return Mono.error(new GatewayUpstreamException(
                    GatewayErrorCode.VALIDATION_ERROR, "agentKey 不能为空", "VALIDATION_ERROR", 400));
        }
        Map<String, Object> safeInput = input == null ? Map.of() : input;
        String idempotencyKey = "gw:idem:" + principal.userId() + ":" + agentKey + ":" + traceId;
        return cache.putIfAbsent(idempotencyKey, "1", Duration.ofSeconds(properties.resolvedIdempotencyTtlSeconds()))
                .doOnNext(acquired -> log.info("[gateway] 幂等键 acquired={} agentKey={} traceId={}",
                        acquired, agentKey, traceId))
                .onErrorResume(ex -> {
                    log.warn("[gateway] 幂等键写入失败，忽略：{}", ex.toString());
                    return Mono.just(Boolean.TRUE);
                })
                .then(platformClient.runAgent(principal.platformToken(), traceId, agentKey, safeInput))
                .onErrorMap(this::mapUpstream);
    }

    /**
     * 缓存键（前缀可配，便于多环境共用 Redis 时隔离）。
     *
     * @return 形如 {@code gw:catalog:agents:v1}
     */
    public String cacheKey() {
        return properties.resolvedKeyPrefix() + ":v1";
    }

    /** 目录 → JSON；序列化失败返回空串（调用方视为不缓存）。 */
    private String encode(List<AgentSummary> agents) {
        try {
            return objectMapper.writeValueAsString(agents);
        } catch (Exception ex) {
            log.warn("[gateway] 目录序列化失败，跳过缓存：{}", ex.toString());
            return "";
        }
    }

    /** JSON → 目录；内容为空或损坏返回 null（调用方视为未命中）。 */
    private List<AgentSummary> decode(String json) {
        if (json == null || json.isBlank()) {
            return null;
        }
        try {
            return objectMapper.readValue(json, new TypeReference<List<AgentSummary>>() { });
        } catch (Exception ex) {
            log.warn("[gateway] 目录缓存内容损坏，视为未命中：{}", ex.toString());
            return null;
        }
    }

    /** 上游失败映射：平台 5xx 收敛为网关侧 502；4xx 与连接类故障保持原样（由适配器已分清）。 */
    private Throwable mapUpstream(Throwable throwable) {
        if (throwable instanceof GatewayUpstreamException upstream
                && upstream.upstreamStatus() >= 500) {
            return new GatewayUpstreamException(
                    GatewayErrorCode.UPSTREAM_SERVER_ERROR,
                    "平台执行接口返回 " + upstream.upstreamStatus(),
                    upstream.upstreamCode(),
                    upstream.upstreamStatus());
        }
        return throwable;
    }
}
