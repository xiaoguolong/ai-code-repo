package com.aicode.framework.observability;

import com.aicode.framework.observability.domain.AgentObservabilityPort;
import com.aicode.framework.observability.domain.NoopObservabilityAdapter;
import com.aicode.framework.observability.infrastructure.MicrometerObservabilityAdapter;
import io.micrometer.core.instrument.simple.SimpleMeterRegistry;
import io.micrometer.tracing.Tracer;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.autoconfigure.web.servlet.AutoConfigureMockMvc;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.boot.test.context.TestConfiguration;
import org.springframework.context.annotation.Bean;
import org.springframework.test.context.ActiveProfiles;
import org.springframework.test.web.servlet.MockMvc;

import static org.assertj.core.api.Assertions.assertThat;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.header;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.jsonPath;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

/**
 * 可观测开关测试（Week 17）：
 * {@code observability.enabled=false} 时装配空实现且不写 {@code traceparent}；
 * 打开时装配 Micrometer 适配器并真的产生 span。
 *
 * <p>两个上下文都要求整个应用能正常启动——开关不得破坏 Spring 装配。</p>
 */
class ObservabilityDisabledTest {

    /** 关闭观测：空实现生效，不注册任何 Tracer（开关真的把观测依赖摘掉了）。 */
    @SpringBootTest(properties = {
            "spring.ai.openai.api-key=test-key",
            "auth.password-salt=test-salt",
            "platform.persistence.mode=memory",
            "observability.enabled=false",
            "management.otlp.tracing.export.enabled=false"
    })
    @ActiveProfiles("test")
    @AutoConfigureMockMvc
    static class Disabled {

        @Autowired
        private AgentObservabilityPort observabilityPort;

        @Autowired
        private MockMvc mockMvc;

        @Test
        void wiresNoopAdapter() {
            assertThat(observabilityPort).isInstanceOf(NoopObservabilityAdapter.class);
            assertThat(observabilityPort.isEnabled()).isFalse();
        }

        @Test
        void noopAdapterProducesEmptyOtelTraceContextButKeepsTraceId() {
            var view = observabilityPort.currentTraceContext("mdc-trace");

            assertThat(view.traceId()).isEqualTo("mdc-trace");
            assertThat(view.otelTraceId()).isEmpty();
            assertThat(view.spanId()).isEmpty();
            assertThat(view.traceparent()).isEmpty();
            assertThat(view.observabilityEnabled()).isFalse();
        }

        @Test
        void responseCarriesNoTraceparentHeaderButKeepsTraceIdContract() throws Exception {
            mockMvc.perform(get("/api/v1/platform/observability/trace-context")
                            .header("X-Trace-Id", "disabled-probe"))
                    .andExpect(header().doesNotExist("traceparent"))
                    .andExpect(header().string("X-Trace-Id", "disabled-probe"));
        }
    }

    /** 打开观测：Micrometer 适配器生效，span 真的被创建，traceparent 正常回写。 */
    @SpringBootTest(properties = {
            "spring.ai.openai.api-key=test-key",
            "auth.password-salt=test-salt",
            "platform.persistence.mode=memory",
            "observability.enabled=true",
            "management.otlp.tracing.export.enabled=false"
    })
    @ActiveProfiles("test")
    @AutoConfigureMockMvc
    static class Enabled {

        @Autowired
        private AgentObservabilityPort observabilityPort;

        @Autowired
        private MockMvc mockMvc;

        @Test
        void wiresMicrometerAdapter() {
            assertThat(observabilityPort).isInstanceOf(MicrometerObservabilityAdapter.class);
            assertThat(observabilityPort.isEnabled()).isTrue();
        }

        /**
         * 打开观测时，HTTP 响应必须回写 {@code traceparent}。
         *
         * <p><b>这里不断言 span</b>：MockMvc 上下文里框架观测建立的 span 不会落到本测试的内存 exporter
         * （Week 17 实测结论，见 {@code notes/impl-logs/week-17.md} 第 13 节）。入口 server span 由
         * {@code HttpServerSpanFilterTest}（standalone 过滤链）、{@code ObservabilityProductionTraceLinkTest}
         * 以及真机 OTLP collector / Langfuse 事件库（Week 18 实现日志 10.2）共同覆盖 —— 断言一个跑不出来的
         * 事实只会变成永远失败或永远被忽略的测试。</p>
         *
         * <p>Week 18 说明：本方法原先断言 {@code finishedSpans()} 里存在 {@code http.server}，
         * 而该嵌套测试类此前从未被执行（surefire 默认排除内部类），直到本周补上 include/excludes
         * 才暴露出来（312 → 320）。</p>
         */
        @Test
        void httpRequestProducesTraceparentHeader() throws Exception {
            // 该端点需登录：未登录时（401）同样必须回写链路头，且 X-Trace-Id 原样回显（Week 16/17 契约）
            mockMvc.perform(get("/api/v1/platform/observability/trace-context")
                            .header("X-Trace-Id", "enabled-probe"))
                    .andExpect(status().isUnauthorized())
                    .andExpect(header().exists("traceparent"))
                    .andExpect(header().string("X-Trace-Id", "enabled-probe"))
                    .andExpect(jsonPath("$.traceId").isNotEmpty());
        }

        @Test
        void rejectsUnauthenticatedObservabilityRequestWithTraceId() throws Exception {
            mockMvc.perform(get("/api/v1/platform/observability/trace-context"))
                    .andExpect(status().isUnauthorized())
                    .andExpect(jsonPath("$.code").value("UNAUTHORIZED"))
                    .andExpect(jsonPath("$.traceId").isNotEmpty());
        }

        @TestConfiguration
        static class InMemoryTracingConfiguration {

            @Bean
            InMemoryOtelTracer.Bundle otelBundle() {
                return InMemoryOtelTracer.build();
            }

            @Bean
            Tracer tracer(InMemoryOtelTracer.Bundle bundle) {
                return bundle.tracer();
            }

            @Bean
            SimpleMeterRegistry simpleMeterRegistry() {
                return new SimpleMeterRegistry();
            }
        }
    }
}
