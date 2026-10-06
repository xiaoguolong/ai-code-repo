package com.aicode.gateway.domain.port;

import com.aicode.gateway.domain.model.AgentSummary;
import com.aicode.gateway.domain.model.PlatformLogin;
import reactor.core.publisher.Mono;

import java.util.List;
import java.util.Map;

/**
 * 平台服务出站端口（Week 19）：登录、Agent 目录、Agent 执行。
 *
 * <p>实现（Feign）负责协议与认证头，端口只表达业务语义；上层用例据此做缓存与错误映射，
 * 因此换掉 Feign（例如改 WebClient）不影响用例。</p>
 *
 * <p>失败约定：上游 4xx/5xx/不可达统一抛
 * {@link com.aicode.gateway.domain.exception.GatewayUpstreamException}，
 * 由用例决定透传还是替换文案。</p>
 */
public interface PlatformClientPort {

    /**
     * 调用平台登录接口。
     *
     * @param username 登录名
     * @param password 密码（只在内存中传递，禁止打日志）
     * @return 平台登录结果
     */
    Mono<PlatformLogin> login(String username, String password);

    /**
     * 读取平台 Agent 目录。
     *
     * @param platformToken 平台 token（用于换发 Authorization）
     * @param traceId       业务链路 ID，透传给上游
     * @return Agent 列表；无数据时为空列表
     */
    Mono<List<AgentSummary>> listAgents(String platformToken, String traceId);

    /**
     * 触发一次 Agent 执行。
     *
     * @param platformToken 平台 token
     * @param traceId       业务链路 ID
     * @param agentKey      Agent 标识
     * @param input         Run 入参
     * @return 平台返回的执行结果（原样透传的信封 data）
     */
    Mono<Map<String, Object>> runAgent(
            String platformToken, String traceId, String agentKey, Map<String, Object> input);

    /**
     * 基础地址（自检展示用）。
     *
     * @return 平台服务基础地址
     */
    String baseUri();
}
