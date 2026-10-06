package com.aicode.gateway.infrastructure.feign;

import feign.Logger;
import feign.RequestInterceptor;
import feign.micrometer.MicrometerCapability;
import io.micrometer.core.instrument.MeterRegistry;
import org.springframework.boot.autoconfigure.condition.ConditionalOnBean;
import org.springframework.boot.autoconfigure.condition.ConditionalOnMissingBean;
import org.springframework.boot.autoconfigure.http.HttpMessageConverters;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;

/**
 * Feign 客户端配置（Week 19）。
 *
 * <p>四件事：</p>
 * <ol>
 *   <li>{@link RequestInterceptor}：补默认头（{@code Accept} / {@code Content-Type}）并确保每个出站请求
 *       都带 {@code X-Trace-Id}（缺失时补空串占位，便于上游日志统一）——<b>不打印 Authorization</b>；</li>
 *   <li>{@link Logger.Level#BASIC}：只记方法、URL、状态、耗时。禁用 FULL/HEADERS：
 *       那会把 Authorization 与 Prompt 正文写进日志（规范 5.5 禁令）；</li>
 *   <li>{@link MicrometerCapability}（容器有 {@link MeterRegistry} 时）：把 Feign 调用纳入
 *       {@code feign.Client} 指标，与 Week 17 的指标口径一致；</li>
 *   <li>{@link HttpMessageConverters} 兜底 bean：<b>响应式应用必须显式提供</b>，见下方说明。</li>
 * </ol>
 *
 * <p><b>为什么必须提供 {@code HttpMessageConverters}（实测踩坑，排查成本很高）</b>：
 * Spring Cloud OpenFeign 默认用 {@code SpringEncoder} 编码请求体，它的构造器要求注入
 * {@code org.springframework.boot.autoconfigure.http.HttpMessageConverters}。
 * 该 bean 原本由 {@code HttpMessageConvertersAutoConfiguration} 提供，而这个自动配置在
 * Spring Boot 3.4 里是 <b>Servlet Web 应用专属</b>（条件含 {@code ServletWebServerApplicationContext}）。
 * 网关是 WebFlux 应用（且刻意排除了 {@code spring-boot-starter-web}），因此容器里没有这个 bean ——
 * 表现是<b>启动完全正常、只有第一次带请求体的调用失败</b>，且 Feign 把它包成
 * {@code EncodeException} + {@code status=-1}，日志里只看到「上游不可用 502」，
 * 极易误判为网络或被调服务的问题（本次即如此）。</p>
 */
@Configuration
public class PlatformFeignConfiguration {

    /** 出站请求默认头与链路头补齐。 */
    @Bean
    public RequestInterceptor platformFeignRequestInterceptor() {
        return template -> {
            template.header("Accept", "application/json");
            if (template.headers().getOrDefault("Content-Type", java.util.List.of()).isEmpty()) {
                template.header("Content-Type", "application/json");
            }
            if (template.headers().getOrDefault("X-Trace-Id", java.util.List.of()).isEmpty()) {
                template.header("X-Trace-Id", "");
            }
        };
    }

    /**
     * Feign 日志级别（只记请求行与状态，不含头与正文）。
     *
     * @return BASIC 级别
     */
    @Bean
    public Logger.Level platformFeignLoggerLevel() {
        return Logger.Level.BASIC;
    }

    /**
     * Feign 指标桥：把 Feign 调用纳入 {@code feign.Client} 指标（与 Week 17 口径一致）。
     *
     * <p>不写成 {@code @ConditionalOnBean}：Feign 的客户端配置类参与的是「Feign 子上下文」，
     * 条件注解在那里判断容易失效（实测：容器里明明有 {@code MeterRegistry}，条件仍判定未命中，
     * 能力悄悄不注册）。改为直接要求注入 {@link MeterRegistry} —— 网关作为 Actuator 应用必然有它，
     * 缺了就应该启动失败而不是静默少一份指标。</p>
     *
     * @param meterRegistry 指标注册表
     * @return Micrometer 能力
     */
    @Bean
    public MicrometerCapability platformFeignMicrometerCapability(MeterRegistry meterRegistry) {
        return new MicrometerCapability(meterRegistry);
    }

    /**
     * Feign 编码器需要的消息转换器兜底（响应式应用必须显式提供）。
     *
     * <p>{@code HttpMessageConvertersAutoConfiguration} 在 Spring Boot 3.4 里只对 Servlet 应用生效，
     * 而 {@code SpringEncoder} 又强依赖该 bean。这里直接 {@code new} 一个默认实例：
     * 它按 classpath 装配 Jackson 转换器，因此 JSON 请求体（登录、Run 入参）能正常编码。
     * 用 {@code @ConditionalOnMissingBean} 保证 Servlet 应用（若将来复用本配置）不被覆盖。</p>
     *
     * @return 默认转换器集合
     */
    @Bean
    @ConditionalOnMissingBean(HttpMessageConverters.class)
    public HttpMessageConverters feignHttpMessageConverters() {
        return new HttpMessageConverters();
    }
}
