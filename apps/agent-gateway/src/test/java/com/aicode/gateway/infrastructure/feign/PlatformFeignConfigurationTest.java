package com.aicode.gateway.infrastructure.feign;

import io.micrometer.core.instrument.simple.SimpleMeterRegistry;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.springframework.boot.autoconfigure.http.HttpMessageConverters;
import org.springframework.boot.test.context.runner.ApplicationContextRunner;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;
import org.springframework.context.annotation.Import;

import static org.assertj.core.api.Assertions.assertThat;

/**
 * Feign 配置装配测试（Week 19）。
 *
 * <p><b>本测试的价值在于守住一个只在真机暴露的坑</b>：网关是 WebFlux 应用，
 * Spring Boot 3.4 的 {@code HttpMessageConvertersAutoConfiguration} 只对 Servlet 应用生效，
 * 于是 {@code SpringEncoder} 依赖的 {@code HttpMessageConverters} 在容器里不存在 ——
 * 表现是启动正常、只有带请求体的调用失败（{@code EncodeException} + {@code status=-1}，
 * 日志看起来像「上游 502」）。单测里 {@code StubFeignClient} 走内存实现、不经过编码器，
 * 所以必须在这里直接断言该 bean 存在，否则这类缺陷只能靠真机验收发现。</p>
 */
class PlatformFeignConfigurationTest {

    @Test
    @DisplayName("必须提供 HttpMessageConverters（否则 Feign 编码请求体时会 NoSuchBeanDefinition）")
    void providesHttpMessageConverters() {
        new ApplicationContextRunner()
                .withUserConfiguration(TestFeignConfig.class)
                .run(context -> {
                    assertThat(context).hasNotFailed();
                    assertThat(context).hasSingleBean(HttpMessageConverters.class);
                });
    }

    @Test
    @DisplayName("请求拦截器与日志级别已注册（BASIC：不记请求头与正文）")
    void registersInterceptorAndLogLevel() {
        new ApplicationContextRunner()
                .withUserConfiguration(TestFeignConfig.class)
                .run(context -> {
                    assertThat(context).hasNotFailed();
                    assertThat(context).hasBean("platformFeignRequestInterceptor");
                    assertThat(context.getBean("platformFeignLoggerLevel"))
                            .isEqualTo(feign.Logger.Level.BASIC);
                });
    }

    @Test
    @DisplayName("Micrometer 能力随 MeterRegistry 一起注册（指标口径与 Week 17 一致）")
    void registersMicrometerCapability() {
        new ApplicationContextRunner()
                .withUserConfiguration(TestFeignConfig.class)
                .run(context -> {
                    assertThat(context).hasNotFailed();
                    assertThat(context).hasBean("platformFeignMicrometerCapability");
                });
    }

    /**
     * 测试装配：显式 import 被测配置，并提供它依赖的 MeterRegistry。
     *
     * <p>用 {@code @Import} 而不是把 {@code ApplicationContextRunner} 直接指向被测类：
     * 后者在单测里会被当成普通 {@code @Configuration} 处理，容易得到与真实
     * 「Feign 客户端子上下文」不一致的结果（实测：直接指向时条件与依赖解析表现异常）。</p>
     */
    @Configuration(proxyBeanMethods = false)
    @Import(PlatformFeignConfiguration.class)
    static class TestFeignConfig {

        /**
         * 指标注册表（等价于生产里的 Prometheus registry）。
         *
         * @return 简单注册表
         */
        @Bean
        SimpleMeterRegistry simpleMeterRegistry() {
            return new SimpleMeterRegistry();
        }
    }
}
