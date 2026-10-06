package com.aicode.framework.observability.infrastructure;

import com.aicode.framework.observability.domain.AgentObservabilityPort;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.beans.factory.ObjectProvider;
import org.springframework.boot.autoconfigure.condition.ConditionalOnMissingBean;
import org.springframework.boot.autoconfigure.condition.ConditionalOnProperty;
import org.springframework.boot.context.properties.EnableConfigurationProperties;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;
import org.springframework.context.annotation.Primary;

/**
 * SkyWalking 装配（Week 19）。
 *
 * <p>两个 bean，各管一件事：</p>
 * <ol>
 *   <li>{@link SkyWalkingSpanBridge}：<b>总是</b>注册。{@code enabled=true} 且 Toolkit 可加载时是
 *       真实桥，否则是空实现 —— 自检接口因此永远能回答「Agent 挂上了吗」，
 *       而不是在 Agent 未挂载时整个端点报错（自检在故障时的价值最大）；</li>
 *   <li>{@link SkyWalkingObservabilityAdapter}：仅 {@code enabled=true} 时注册，且标记
 *       {@link Primary}，装饰既有端口（Week 17 的装配一行未改）。</li>
 * </ol>
 *
 * <p>互斥保证：装饰器依赖容器里「非 Primary 的」{@link AgentObservabilityPort}，
 * 所以用 {@link ObjectProvider} 延迟解析并排除自身，不会形成自依赖。</p>
 */
@Configuration
@EnableConfigurationProperties(SkyWalkingProperties.class)
public class SkyWalkingConfiguration {

    private static final Logger log = LoggerFactory.getLogger(SkyWalkingConfiguration.class);

    /**
     * SkyWalking 桥：真实 Toolkit 优先，不可用时降级为空实现。
     *
     * <p>降级不是「静默失败」：会打一条 WARN 明确说明手动埋点已失效及其原因，
     * 避免出现「配置开了但链路上什么都没有」的迷惑现象。</p>
     *
     * @param properties 配置
     * @return 桥实现
     */
    @Bean
    @ConditionalOnMissingBean(SkyWalkingSpanBridge.class)
    public SkyWalkingSpanBridge skyWalkingSpanBridge(SkyWalkingProperties properties) {
        if (!properties.enabled()) {
            log.info("[skywalking] 手动埋点未启用（observability.skywalking.enabled=false）");
            return NoopSpanBridge.INSTANCE;
        }
        ToolkitSpanBridge bridge = ToolkitSpanBridge.tryCreate();
        if (bridge == null) {
            log.warn("[skywalking] 已启用手动埋点但 Toolkit 不可用：请确认启动参数带 "
                    + "-javaagent:<skywalking-agent.jar>（当前降级为空实现，业务不受影响）");
            return NoopSpanBridge.INSTANCE;
        }
        log.info("[skywalking] 手动埋点已启用 service={} backend={}",
                properties.resolvedServiceName(), properties.resolvedBackendService());
        return bridge;
    }

    /**
     * SkyWalking 埋点装饰器（{@code observability.skywalking.enabled=true} 时注册）。
     *
     * <p>标记 {@link Primary}，因此对所有 {@code AgentObservabilityPort} 注入点生效；
     * 它内部持有的是既有实现（Micrometer/OTel），契约零改动。</p>
     *
     * @param delegates   既有观测端口（含本装饰器自身，需过滤）
     * @param bridge      SkyWalking 桥
     * @param properties  配置
     * @return 装饰后的端口
     */
    @Bean
    @Primary
    @ConditionalOnProperty(prefix = "observability.skywalking", name = "enabled", havingValue = "true")
    public AgentObservabilityPort skyWalkingObservabilityPort(
            ObjectProvider<AgentObservabilityPort> delegates,
            SkyWalkingSpanBridge bridge,
            SkyWalkingProperties properties
    ) {
        AgentObservabilityPort delegate = delegates.orderedStream()
                .filter(candidate -> !(candidate instanceof SkyWalkingObservabilityAdapter))
                .findFirst()
                .orElseThrow(() -> new IllegalStateException(
                        "observability.skywalking.enabled=true 需要既有的 AgentObservabilityPort 装配"
                                + "（请同时保持 observability.enabled=true）"));
        return new SkyWalkingObservabilityAdapter(delegate, bridge, properties.resolvedTagPrefix());
    }
}
