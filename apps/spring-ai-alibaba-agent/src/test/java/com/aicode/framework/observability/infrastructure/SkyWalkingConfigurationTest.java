package com.aicode.framework.observability.infrastructure;

import com.aicode.framework.observability.domain.AgentObservabilityPort;
import com.aicode.framework.observability.support.RecordingObservabilityPort;
import com.aicode.framework.observability.support.RecordingSpanBridge;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.springframework.boot.test.context.runner.ApplicationContextRunner;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;
import org.springframework.context.annotation.Primary;

import static org.assertj.core.api.Assertions.assertThat;

/**
 * SkyWalking 装配测试（Week 19）。
 *
 * <p>验证「开关 + Agent 是否挂载」的四种组合各自装出什么：</p>
 * <ol>
 *   <li>关闭（默认）→ 桥是空实现、不装配装饰器，既有端口原样可用；</li>
 *   <li>打开但容器已有桥（真机挂 Agent 的等价情形）→ 装饰器生效且为 {@code @Primary}；</li>
 *   <li>打开但 Toolkit 不可用（本地与单测默认情形）→ 桥降级为空实现，应用照常启动；</li>
 *   <li>关闭但容器提供桥 → 不装配装饰器，零额外开销。</li>
 * </ol>
 *
 * <p>注意：本测试<b>不能</b>注册两个 {@code AgentObservabilityPort}，否则装配会取先解析到的那个，
 * 断言就变成「碰运气」（写这类装配测试时踩过的点）。</p>
 */
class SkyWalkingConfigurationTest {

    private final ApplicationContextRunner runner = new ApplicationContextRunner()
            .withUserConfiguration(SkyWalkingConfiguration.class, BasePortConfiguration.class);

    @Test
    @DisplayName("默认关闭：桥为空实现，端口仍是既有实现（无装饰器）")
    void disabledByDefault() {
        runner.run(context -> {
            assertThat(context).hasSingleBean(SkyWalkingSpanBridge.class);
            assertThat(context.getBean(SkyWalkingSpanBridge.class)).isInstanceOf(NoopSpanBridge.class);
            assertThat(context).hasSingleBean(AgentObservabilityPort.class);
            assertThat(context.getBean(AgentObservabilityPort.class))
                    .isInstanceOf(RecordingObservabilityPort.class);
        });
    }

    @Test
    @DisplayName("打开且容器提供桥：装饰器为 @Primary，内部持有既有端口")
    void enabledWithBridgeDecorates() {
        runner.withUserConfiguration(StubBridgeConfiguration.class)
                .withPropertyValues("observability.skywalking.enabled=true")
                .run(context -> {
                    assertThat(context).hasNotFailed();
                    assertThat(context.getBean(SkyWalkingSpanBridge.class))
                            .isInstanceOf(RecordingSpanBridge.class);

                    AgentObservabilityPort port = context.getBean(AgentObservabilityPort.class);
                    assertThat(port).isInstanceOf(SkyWalkingObservabilityAdapter.class);
                    SkyWalkingObservabilityAdapter adapter = (SkyWalkingObservabilityAdapter) port;
                    assertThat(adapter.bridgeAvailable()).isTrue();
                    assertThat(adapter.skyWalkingTraceId()).isEqualTo("sw-trace");

                    // 既有端口仍在容器里（装饰而非替换）
                    assertThat(context.getBeansOfType(AgentObservabilityPort.class)).hasSize(2);
                });
    }

    @Test
    @DisplayName("打开但未挂 Agent：桥不谎报可用（Toolkit 在 classpath 上不等于已接入），启动不失败")
    void enabledWithoutAgentDegrades() {
        runner.withPropertyValues("observability.skywalking.enabled=true").run(context -> {
            assertThat(context).hasNotFailed();
            SkyWalkingSpanBridge bridge = context.getBean(SkyWalkingSpanBridge.class);
            // provided 依赖让 Toolkit 类在 classpath 上可见，但未挂 Agent 时必须如实报「不可用」，
            // 否则自检接口会谎报已接入（本地 fat jar 最容易踩）
            assertThat(bridge.available()).isFalse();

            AgentObservabilityPort port = context.getBean(AgentObservabilityPort.class);
            assertThat(port).isInstanceOf(SkyWalkingObservabilityAdapter.class);
            assertThat(((SkyWalkingObservabilityAdapter) port).bridgeAvailable()).isFalse();
        });
    }

    @Test
    @DisplayName("关闭时不装配装饰器：即使容器提供桥也不改变端口装配")
    void disabledKeepsOriginalPort() {
        runner.withUserConfiguration(StubBridgeConfiguration.class).run(context -> {
            assertThat(context.getBean(AgentObservabilityPort.class))
                    .isInstanceOf(RecordingObservabilityPort.class);
        });
    }

    @Test
    @DisplayName("配置绑定：service-name / backend-service / tag-prefix 可读且带兜底")
    void propertiesAreBound() {
        runner.withPropertyValues(
                        "observability.skywalking.enabled=true",
                        "observability.skywalking.service-name=ai-code-platform",
                        "observability.skywalking.backend-service=192.168.132.128:11800",
                        "observability.skywalking.tag-prefix=aicode")
                .run(context -> {
                    assertThat(context).hasNotFailed();
                    SkyWalkingProperties properties = context.getBean(SkyWalkingProperties.class);
                    assertThat(properties.enabled()).isTrue();
                    assertThat(properties.resolvedServiceName()).isEqualTo("ai-code-platform");
                    assertThat(properties.resolvedBackendService()).isEqualTo("192.168.132.128:11800");
                    assertThat(properties.resolvedTagPrefix()).isEqualTo("aicode");
                });
    }

    /** 提供既有观测端口的配置（等价于 Week 17 的 Micrometer 实现）。 */
    @Configuration(proxyBeanMethods = false)
    static class BasePortConfiguration {

        /**
         * 既有端口。
         *
         * @return 端口
         */
        @Bean
        AgentObservabilityPort baseObservabilityPort() {
            return new RecordingObservabilityPort();
        }
    }

    /** 提供桥的配置（等价于真机挂上 Agent 后 Toolkit 可加载的情形）。 */
    @Configuration(proxyBeanMethods = false)
    static class StubBridgeConfiguration {

        /**
         * 假桥。
         *
         * <p>标 {@code @Primary}：{@code SkyWalkingConfiguration} 也会注册一个桥 bean
         * （默认空实现），不指定主候选会报 {@code NoUniqueBeanDefinitionException}。</p>
         *
         * @return 桥
         */
        @Bean
        @Primary
        SkyWalkingSpanBridge stubBridge() {
            return new RecordingSpanBridge();
        }
    }
}
