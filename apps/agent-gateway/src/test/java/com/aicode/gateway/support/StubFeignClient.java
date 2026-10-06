package com.aicode.gateway.support;

import com.aicode.gateway.infrastructure.feign.FeignAgentPayload;
import com.aicode.gateway.infrastructure.feign.FeignEnvelope;
import com.aicode.gateway.infrastructure.feign.FeignLoginPayload;
import com.aicode.gateway.infrastructure.feign.FeignLoginRequest;
import com.aicode.gateway.infrastructure.feign.PlatformFeignClient;

import java.util.List;
import java.util.Map;

/**
 * 假 Feign 客户端（单测用）：记录入参、按需返回信封或抛异常。
 *
 * <p>实现 {@link PlatformFeignClient} 而不是 mock 框架：编译期即锁死方法签名，
 * 将来给客户端加参数（例如新的请求头）时测试会立刻编译失败，而不是悄悄漏测新分支。</p>
 */
public class StubFeignClient implements PlatformFeignClient {

    /** 登录响应。 */
    public FeignEnvelope<FeignLoginPayload> loginEnvelope;

    /** 登录异常。 */
    public RuntimeException loginFailure;

    /** 目录响应。 */
    public FeignEnvelope<List<FeignAgentPayload>> agentsEnvelope;

    /** 目录异常。 */
    public RuntimeException listFailure;

    /** 执行响应。 */
    public FeignEnvelope<Map<String, Object>> runEnvelope;

    /** 执行异常。 */
    public RuntimeException runFailure;

    /** 最后一次 Authorization 头。 */
    public String lastAuthorization;

    /** 最后一次 X-Trace-Id 头。 */
    public String lastTraceId;

    /** 最后一次登录请求体。 */
    public FeignLoginRequest lastLoginRequest;

    /** 最后一次执行请求体。 */
    public Map<String, Object> lastBody;

    /** 最后一次调用的 Agent。 */
    public String lastAgentKey;

    @Override
    public FeignEnvelope<FeignLoginPayload> login(FeignLoginRequest request) {
        lastLoginRequest = request;
        if (loginFailure != null) {
            throw loginFailure;
        }
        return loginEnvelope;
    }

    @Override
    public FeignEnvelope<Void> logout(String authorization, String traceId) {
        lastAuthorization = authorization;
        lastTraceId = traceId;
        return new FeignEnvelope<>("SUCCESS", "OK", null, traceId);
    }

    @Override
    public FeignEnvelope<List<FeignAgentPayload>> listAgents(String authorization, String traceId) {
        lastAuthorization = authorization;
        lastTraceId = traceId;
        if (listFailure != null) {
            throw listFailure;
        }
        return agentsEnvelope;
    }

    @Override
    public FeignEnvelope<Map<String, Object>> runAgent(
            String authorization, String traceId, String agentKey, Map<String, Object> body) {
        lastAuthorization = authorization;
        lastTraceId = traceId;
        lastAgentKey = agentKey;
        lastBody = body;
        if (runFailure != null) {
            throw runFailure;
        }
        return runEnvelope;
    }
}
