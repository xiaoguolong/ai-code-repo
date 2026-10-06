package com.aicode.gateway.application;

import com.aicode.gateway.domain.exception.GatewayException;
import com.aicode.gateway.domain.exception.GatewayUnauthorizedException;
import com.aicode.gateway.domain.exception.GatewayUpstreamException;
import com.aicode.gateway.domain.model.GatewaySession;
import com.aicode.gateway.domain.model.IssuedSession;
import com.aicode.gateway.domain.model.PlatformLogin;
import com.aicode.gateway.domain.port.GatewaySessionPort;
import com.aicode.gateway.domain.port.PlatformClientPort;
import com.aicode.gateway.dto.GatewayErrorCode;
import com.aicode.gateway.infrastructure.config.GatewaySessionProperties;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.stereotype.Service;
import reactor.core.publisher.Mono;

import java.time.Duration;
import java.time.Instant;

/**
 * 网关联会话用例（Week 19）。
 *
 * <p>流程：</p>
 * <ol>
 *   <li><b>登录</b>：经 {@link PlatformClientPort} 调平台登录接口（唯一事实源），
 *       成功后把「平台 token → 身份摘要」写入 {@link GatewaySessionPort}（Redis，TTL 可配）；</li>
 *   <li><b>登出</b>：删除网关侧会话记录。</li>
 * </ol>
 *
 * <p><b>关键设计：网关联会话 token 就是平台 token 本身</b>。这样做有两个直接好处：</p>
 * <ul>
 *   <li>转发时<b>不需要换发任何凭据</b>：客户端带来的 {@code Authorization: Bearer <token>}
 *       原样透传给平台，平台认它自己的 token。此前「网关会话 token ≠ 平台 token」的写法
 *       必须先改写请求头，而在 Spring Cloud Gateway 里改写请求只能靠
 *       {@code exchange.mutate()}，那会让派生交换对象的响应变成只读，
 *       代理写出上游响应时抛 {@code UnsupportedOperationException: setContentLength}
 *       （现象是「平台侧 200、客户端连接被关闭」，实测踩过、排查成本极高）；</li>
 *   <li>语义更诚实：网关不签发凭据，只是给平台的凭据加一层「这个 token 已被网关接纳」的登记，
 *       权限裁决始终在平台。</li>
 * </ul>
 *
 * <p><b>为什么不自己签 token</b>：那需要网关持有用户库并复刻 RBAC，等于把权限体系复制两份。</p>
 */
@Service
public class GatewaySessionUseCase {

    private static final Logger log = LoggerFactory.getLogger(GatewaySessionUseCase.class);

    private final PlatformClientPort platformClient;
    private final GatewaySessionPort sessions;
    private final GatewaySessionProperties properties;

    /**
     * @param platformClient 平台出站端口
     * @param sessions       会话存储端口
     * @param properties     会话配置
     */
    public GatewaySessionUseCase(
            PlatformClientPort platformClient,
            GatewaySessionPort sessions,
            GatewaySessionProperties properties
    ) {
        this.platformClient = platformClient;
        this.sessions = sessions;
        this.properties = properties;
    }

    /**
     * 登录并建立网关联会话。
     *
     * @param username 登录名，空白视为参数错误
     * @param password 密码，空白视为参数错误
     * @return 已签发的会话（<b>不含</b>平台 token）
     */
    public Mono<IssuedSession> login(String username, String password) {
        if (username == null || username.isBlank() || password == null || password.isBlank()) {
            return Mono.error(new GatewayException(GatewayErrorCode.VALIDATION_ERROR, "username/password 不能为空"));
        }
        return platformClient.login(username.trim(), password)
                .flatMap(login -> issue(login))
                .onErrorMap(this::mapUpstream);
    }

    /**
     * 登出：删除网关会话。平台侧 token 失效失败不阻塞登出（只记日志）。
     *
     * @param sessionToken 网关联会话 token
     * @return 完成信号
     */
    public Mono<Void> logout(String sessionToken) {
        if (sessionToken == null || sessionToken.isBlank()) {
            return Mono.error(new GatewayUnauthorizedException("缺少会话 token"));
        }
        return sessions.remove(sessionToken).doOnSuccess(ignored -> log.info("[gateway] 会话已登出"));
    }

    /** 生成会话并落库（会话 token 即平台 token）。 */
    private Mono<IssuedSession> issue(PlatformLogin login) {
        long ttlSeconds = properties.resolvedTtlSeconds();
        String token = login.platformToken();
        if (token == null || token.isBlank()) {
            return Mono.error(new GatewayUpstreamException(
                    GatewayErrorCode.GATEWAY_UPSTREAM_ERROR, "平台登录未返回 token", "", 0));
        }
        long expiresAt = Instant.now().getEpochSecond() + ttlSeconds;
        GatewaySession session = new GatewaySession(
                login.platformToken(), login.userId(), login.username(), login.roleKey(), expiresAt);
        return sessions.save(token, session, Duration.ofSeconds(ttlSeconds))
                .thenReturn(new IssuedSession(token, login.userId(), login.username(), login.roleKey(), ttlSeconds))
                .doOnSuccess(issued -> log.info("[gateway] 会话已建立 userId={} username={} ttl={}s",
                        login.userId(), login.username(), ttlSeconds));
    }

    /** 上游失败映射：平台 4xx 的业务码可透传（安全文案）；5xx 收敛为网关侧 502；连接类故障保持原样。 */
    private Throwable mapUpstream(Throwable throwable) {
        if (throwable instanceof GatewayUpstreamException upstream
                && upstream.upstreamStatus() >= 500) {
            return new GatewayUpstreamException(
                    GatewayErrorCode.UPSTREAM_SERVER_ERROR,
                    "平台登录接口返回 " + upstream.upstreamStatus(),
                    upstream.upstreamCode(),
                    upstream.upstreamStatus());
        }
        return throwable;
    }
}
