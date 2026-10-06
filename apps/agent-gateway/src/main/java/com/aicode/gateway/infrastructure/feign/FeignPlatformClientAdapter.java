package com.aicode.gateway.infrastructure.feign;

import com.aicode.gateway.domain.exception.GatewayUpstreamException;
import com.aicode.gateway.domain.exception.PlatformBusinessException;
import com.aicode.gateway.domain.model.AgentSummary;
import com.aicode.gateway.domain.model.PlatformLogin;
import com.aicode.gateway.domain.port.PlatformClientPort;
import com.aicode.gateway.infrastructure.config.GatewayProperties;
import com.aicode.gateway.infrastructure.logging.GatewayAttributes;
import com.fasterxml.jackson.databind.ObjectMapper;
import feign.FeignException;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.stereotype.Component;
import reactor.core.publisher.Mono;
import reactor.core.scheduler.Schedulers;

import java.util.List;
import java.util.Map;

/**
 * {@link PlatformClientPort} 的 Feign 实现（Week 19）。
 *
 * <p><b>阻塞与响应式的桥接</b>：Feign 是阻塞式 HTTP 客户端，而网关跑在 Netty 事件循环上。
 * 若直接在事件循环线程调用，会把整个网关拖死（这是网关接入最常见的线上事故）。
 * 因此所有调用都放到 {@link Schedulers#boundedElastic()} 上执行，
 * 用「少量弹性线程池」换取事件循环的纯净。</p>
 *
 * <p>错误映射：</p>
 * <ul>
 *   <li>HTTP 4xx → {@link PlatformBusinessException}（可安全透传上游业务码与状态）；</li>
 *   <li>HTTP 5xx / 连接失败 / 超时 / 报文非法 → {@link GatewayUpstreamException}（对外 502）。</li>
 * </ul>
 */
@Component
public class FeignPlatformClientAdapter implements PlatformClientPort {

    private static final Logger log = LoggerFactory.getLogger(FeignPlatformClientAdapter.class);

    private final PlatformFeignClient client;
    private final GatewayProperties routes;
    private final ObjectMapper objectMapper;

    /**
     * @param client       Feign 客户端
     * @param routes       路由配置（基础地址）
     * @param objectMapper 用于解析上游失败报文里的业务码
     */
    public FeignPlatformClientAdapter(
            PlatformFeignClient client, GatewayProperties routes, ObjectMapper objectMapper) {
        this.client = client;
        this.routes = routes;
        this.objectMapper = objectMapper;
    }

    @Override
    public Mono<PlatformLogin> login(String username, String password) {
        return blocking(() -> client.login(new FeignLoginRequest(username, password)))
                .flatMap(envelope -> {
                    FeignLoginPayload payload = envelope.data();
                    if (!envelope.succeeded() || payload == null || payload.token() == null) {
                        return Mono.error(new PlatformBusinessException(
                                envelope.code(), 401, "平台登录失败：" + envelope.message()));
                    }
                    return Mono.just(new PlatformLogin(
                            payload.token(), payload.userId(), payload.username(), payload.roleKey()));
                });
    }

    @Override
    public Mono<List<AgentSummary>> listAgents(String platformToken, String traceId) {
        return blocking(() -> client.listAgents(bearer(platformToken), traceId))
                .flatMap(envelope -> {
                    if (!envelope.succeeded()) {
                        return Mono.error(new PlatformBusinessException(
                                envelope.code(), 400, "平台目录查询失败：" + envelope.message()));
                    }
                    List<FeignAgentPayload> data = envelope.data() == null ? List.of() : envelope.data();
                    return Mono.just(data.stream().map(FeignPlatformClientAdapter::toSummary).toList());
                });
    }

    @Override
    public Mono<Map<String, Object>> runAgent(
            String platformToken, String traceId, String agentKey, Map<String, Object> input) {
        Map<String, Object> body = Map.of("input", input == null ? Map.of() : input);
        // 只记长度与首尾字符，不记凭据本身（规范 5.5 禁令：密钥/token 不入日志）
        log.info("[gateway] 调平台执行 agentKey={} traceId={} tokenLength={}",
                agentKey, traceId, platformToken == null ? 0 : platformToken.length());
        return blocking(() -> client.runAgent(bearer(platformToken), traceId, agentKey, body))
                .flatMap(envelope -> {
                    if (!envelope.succeeded()) {
                        return Mono.error(new PlatformBusinessException(
                                envelope.code(), 502, "平台执行失败：" + envelope.message()));
                    }
                    return Mono.just(envelope.data() == null ? Map.<String, Object>of() : envelope.data());
                });
    }

    @Override
    public String baseUri() {
        return routes.resolvedPlatformUri();
    }

    /**
     * 把阻塞调用放到弹性线程池，并把 Feign 异常映射为领域异常。
     *
     * @param call 阻塞调用
     * @param <T>  返回类型
     * @return 响应式结果
     */
    private <T> Mono<T> blocking(java.util.concurrent.Callable<T> call) {
        return Mono.fromCallable(call)
                .subscribeOn(Schedulers.boundedElastic())
                .onErrorMap(this::mapFailure);
    }

    /** Feign 异常 → 领域异常（4xx 透传业务码，其余统一上游错误）。 */
    private Throwable mapFailure(Throwable throwable) {
        if (throwable instanceof PlatformBusinessException || throwable instanceof GatewayUpstreamException) {
            return throwable;
        }
        if (throwable instanceof FeignException feignException) {
            int status = feignException.status();
            String body = feignException.contentUTF8();
            // status=-1 表示「连接层/客户端层就没发出请求」（Feign 对本地异常给出的哨兵值）：
            // 这类失败不看异常名与 cause 无法定位（本项目实测踩过），故把类名与原因一起记进日志。
            log.warn("[gateway] 平台调用失败 status={} bodyLength={} exception={} cause={}",
                    status, body == null ? 0 : body.length(),
                    feignException.getClass().getName(),
                    feignException.getCause() == null ? "-" : feignException.getCause().toString());
            if (status >= 400 && status < 500) {
                return new PlatformBusinessException(extractCode(body), status, "平台返回 " + status);
            }
            return new GatewayUpstreamException("平台返回 " + status, feignException);
        }
        return new GatewayUpstreamException("平台调用异常：" + throwable.getClass().getSimpleName(), throwable);
    }
    /** 从上游失败报文里取业务码；解析失败返回空串（不猜测）。 */
    private String extractCode(String body) {
        if (body == null || body.isBlank()) {
            return "";
        }
        try {
            FeignEnvelope<?> envelope = objectMapper.readValue(body, FeignEnvelope.class);
            return envelope.code() == null ? "" : envelope.code();
        } catch (Exception ex) {
            return "";
        }
    }

    /** Authorization 头值：平台 sa-token 要求 Bearer 前缀，故这里拼完整头值。 */
    private static String bearer(String platformToken) {
        String token = platformToken == null ? "" : platformToken;
        return token.regionMatches(true, 0, GatewayAttributes.BEARER_PREFIX, 0,
                GatewayAttributes.BEARER_PREFIX.length())
                ? token
                : GatewayAttributes.BEARER_PREFIX + token;
    }

    /** Feign 载荷 → 领域模型。 */
    private static AgentSummary toSummary(FeignAgentPayload payload) {
        return new AgentSummary(
                payload.agentKey(),
                payload.name(),
                payload.description(),
                payload.agentType(),
                payload.enabled(),
                payload.model(),
                payload.maxIterations(),
                payload.registeredAt());
    }
}
