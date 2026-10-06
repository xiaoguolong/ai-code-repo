package com.aicode.gateway.support;

import com.aicode.gateway.domain.exception.GatewayUpstreamException;
import com.aicode.gateway.domain.exception.PlatformBusinessException;
import com.aicode.gateway.domain.model.AgentSummary;
import com.aicode.gateway.domain.model.PlatformLogin;
import com.aicode.gateway.domain.port.PlatformClientPort;
import reactor.core.publisher.Mono;

import java.util.ArrayList;
import java.util.List;
import java.util.Map;

/**
 * 平台出站端口的假实现（单测用）。
 *
 * <p>记录调用次数与最后一次入参（断言「缓存命中不回源」「token 与 traceId 透传」）。</p>
 */
public class FakePlatformClient implements PlatformClientPort {

    /** 登录结果；为 null 时按失败处理。 */
    public PlatformLogin loginResult = new PlatformLogin("platform-token-1", 1L, "admin", "ADMIN");

    /** 登录失败原因；非 null 时优先抛出。 */
    public RuntimeException loginFailure;

    /** 目录结果。 */
    public List<AgentSummary> agents = List.of(
            new AgentSummary("medical-assistant", "医疗助手", "报告与随访", "MEDICAL_ASSISTANT",
                    true, "deepseek-v4-pro", 5, "2026-01-01T00:00:00Z"));

    /** 目录失败原因；非 null 时优先抛出。 */
    public RuntimeException listFailure;

    /** 执行结果。 */
    public Map<String, Object> runResult = Map.of("executionId", "exec-1", "status", "COMPLETED");

    /** 执行失败原因；非 null 时优先抛出。 */
    public RuntimeException runFailure;

    /** listAgents 调用次数。 */
    public int listCount;

    /** runAgent 调用次数。 */
    public int runCount;

    /** 最后一次收到的平台 token。 */
    public String lastToken;

    /** 最后一次收到的 traceId。 */
    public String lastTraceId;

    /** 最后一次收到的 Run 入参。 */
    public Map<String, Object> lastInput = Map.of();

    /** 最后调用的 Agent。 */
    public String lastAgentKey;

    @Override
    public Mono<PlatformLogin> login(String username, String password) {
        if (loginFailure != null) {
            return Mono.error(loginFailure);
        }
        return loginResult == null
                ? Mono.error(new PlatformBusinessException("UNAUTHORIZED", 401, "登录失败"))
                : Mono.just(loginResult);
    }

    @Override
    public Mono<List<AgentSummary>> listAgents(String platformToken, String traceId) {
        listCount++;
        lastToken = platformToken;
        lastTraceId = traceId;
        if (listFailure != null) {
            return Mono.error(listFailure);
        }
        return Mono.just(new ArrayList<>(agents));
    }

    @Override
    public Mono<Map<String, Object>> runAgent(
            String platformToken, String traceId, String agentKey, Map<String, Object> input) {
        runCount++;
        lastToken = platformToken;
        lastTraceId = traceId;
        lastAgentKey = agentKey;
        lastInput = input;
        if (runFailure != null) {
            return Mono.error(runFailure);
        }
        return Mono.just(runResult);
    }

    @Override
    public String baseUri() {
        return "http://fake-platform:8084";
    }

    /**
     * 便捷构造：上游不可达。
     *
     * @return 上游异常
     */
    public static GatewayUpstreamException unreachable() {
        return new GatewayUpstreamException("平台不可达", null);
    }
}
