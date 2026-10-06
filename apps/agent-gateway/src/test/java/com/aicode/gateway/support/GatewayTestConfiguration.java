package com.aicode.gateway.support;

import com.aicode.gateway.application.GatewayAgentUseCase;
import com.aicode.gateway.application.GatewaySessionUseCase;
import com.aicode.gateway.application.GatewayStatusUseCase;
import com.aicode.gateway.domain.port.GatewayCachePort;
import com.aicode.gateway.domain.port.GatewaySessionPort;
import com.aicode.gateway.domain.port.PlatformClientPort;
import com.aicode.gateway.infrastructure.config.GatewayCatalogProperties;
import com.aicode.gateway.infrastructure.config.GatewayProperties;
import com.aicode.gateway.infrastructure.config.GatewaySessionProperties;
import com.aicode.gateway.infrastructure.logging.TraceIdWebFilter;
import com.aicode.gateway.infrastructure.observability.SkyWalkingAttachmentProbe;
import com.aicode.gateway.infrastructure.security.GatewayAuthFilter;
import com.aicode.gateway.infrastructure.security.GatewayAuthSupport;
import com.fasterxml.jackson.databind.ObjectMapper;
import org.springframework.boot.test.context.TestConfiguration;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Primary;

/**
 * 网关控制器测试的装配（Week 19）。
 *
 * <p>只注册控制器测试真正需要的 bean：三个用例 + 三个端口假实现 + 配置 + 两个过滤器。
 * 不加载 Feign / Redis 真实客户端：把「协议与鉴权行为」与「基础设施装配」分开测，
 * 前者用假实现（快、确定），后者由真机验收覆盖。</p>
 *
 * <p><b>bean 名必须显式给</b>：Spring Cloud Gateway 的自动配置里已经有名为
 * {@code gatewayProperties} 的 bean（它自己的 {@code GatewayProperties}），
 * 与我们的配置类同名会导致 {@code BeanDefinitionOverrideException} —— 启动直接失败。
 * 这是接入 Spring Cloud Gateway 时第一个会踩到的坑（实测，见实现日志）。</p>
 *
 * <p><b>假实现必须标 {@link Primary}</b>：生产侧的 {@code RedisGatewaySessionAdapter} /
 * {@code RedisGatewayCacheAdapter} / {@code FeignPlatformClientAdapter} 也是
 * {@code @Component}，同为端口实现。不打主候选标记时按字段名解析，会出现
 * 「登录走了假存储、鉴权查了真 Redis」这类<b>同一个上下文里两套实现混用</b>的诡异失败
 * （实测踩过一次，见实现日志）。</p>
 */
@TestConfiguration
public class GatewayTestConfiguration {

    /**
     * 路由配置（测试固定值，bean 名加前缀避开 Gateway 自动配置的同名 bean）。
     *
     * <p>标 {@link Primary}：主配置的 {@code @EnableConfigurationProperties} 也会按 YAML 绑定一份，
     * 不指定主候选会直接报 {@code NoUniqueBeanDefinitionException}。</p>
     */
    @Bean("testGatewayRoutes")
    @Primary
    public GatewayProperties testGatewayRoutes() {
        return new GatewayProperties("http://platform-under-test:8084", null, null);
    }

    /** 会话配置。 */
    @Bean("testGatewaySessionProperties")
    @Primary
    public GatewaySessionProperties testGatewaySessionProperties() {
        // token-name 与平台 sa-token 的 token-name 保持一致（Authorization + Bearer 前缀）
        return new GatewaySessionProperties("Authorization", 600, "redis");
    }

    /** 缓存配置。 */
    @Bean("testGatewayCatalogProperties")
    @Primary
    public GatewayCatalogProperties testGatewayCatalogProperties() {
        return new GatewayCatalogProperties(30, "gw:catalog:agents", 300);
    }

    /** 会话端口假实现。 */
    @Bean
    @Primary
    public GatewaySessionPort gatewaySessionPort() {
        return new FakeSessionStore();
    }

    /** 缓存端口假实现。 */
    @Bean
    @Primary
    public GatewayCachePort gatewayCachePort() {
        return new FakeCacheStore();
    }

    /** 平台出站端口假实现。 */
    @Bean
    @Primary
    public PlatformClientPort platformClientPort() {
        return new FakePlatformClient();
    }

    /** Agent 挂载探针。 */
    @Bean
    public SkyWalkingAttachmentProbe attachmentProbe() {
        return new SkyWalkingAttachmentProbe();
    }

    /** 会话用例。 */
    @Bean
    public GatewaySessionUseCase gatewaySessionUseCase(
            PlatformClientPort platformClientPort,
            GatewaySessionPort gatewaySessionPort,
            GatewaySessionProperties testGatewaySessionProperties) {
        return new GatewaySessionUseCase(
                platformClientPort, gatewaySessionPort, testGatewaySessionProperties);
    }

    /** 目录与执行用例。 */
    @Bean
    public GatewayAgentUseCase gatewayAgentUseCase(
            PlatformClientPort platformClientPort,
            GatewayCachePort gatewayCachePort,
            GatewayCatalogProperties testGatewayCatalogProperties,
            ObjectMapper objectMapper) {
        return new GatewayAgentUseCase(
                platformClientPort, gatewayCachePort, testGatewayCatalogProperties, objectMapper);
    }

    /** 自检用例。 */
    @Bean
    public GatewayStatusUseCase gatewayStatusUseCase(
            GatewayProperties testGatewayRoutes,
            GatewaySessionProperties testGatewaySessionProperties,
            GatewayCatalogProperties testGatewayCatalogProperties,
            GatewaySessionPort gatewaySessionPort,
            GatewayCachePort gatewayCachePort,
            PlatformClientPort platformClientPort,
            SkyWalkingAttachmentProbe attachmentProbe) {
        return new GatewayStatusUseCase(
                testGatewayRoutes, testGatewaySessionProperties, testGatewayCatalogProperties,
                gatewaySessionPort, gatewayCachePort, platformClientPort, attachmentProbe);
    }

    /** 鉴权支撑（过滤器与 Controller 共用）。 */
    @Bean
    public GatewayAuthSupport gatewayAuthSupport(
            GatewaySessionPort gatewaySessionPort,
            GatewaySessionProperties testGatewaySessionProperties,
            GatewayProperties testGatewayRoutes,
            ObjectMapper objectMapper) {
        return new GatewayAuthSupport(
                gatewaySessionPort, testGatewaySessionProperties, testGatewayRoutes, objectMapper);
    }

    /** 自有用例鉴权过滤器。 */
    @Bean
    public GatewayAuthFilter gatewayAuthFilter(
            GatewayAuthSupport gatewayAuthSupport, GatewayProperties testGatewayRoutes) {
        return new GatewayAuthFilter(gatewayAuthSupport, testGatewayRoutes);
    }

    /** 链路 ID 过滤器。 */
    @Bean
    public TraceIdWebFilter traceIdWebFilter() {
        return new TraceIdWebFilter();
    }
}
