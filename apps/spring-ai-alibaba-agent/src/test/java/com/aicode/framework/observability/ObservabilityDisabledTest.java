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
        private InMemoryOtelTracer.Bundle spanStore;

        @Autowired
        private MockMvc mockMvc;

        @Test
        void wiresMicrometerAdapter() {
            assertThat(observabilityPort).isInstanceOf(MicrometerObservabilityAdapter.class);
            assertThat(observabilityPort.isEnabled()).isTrue();
        }

        @Test
        void httpRequestProducesServerSpanAndTraceparentHeader() throws Exception {
            mockMvc.perform(get("/api/v1/platform/observability/trace-context")
                            .header("X-Trace-Id", "enabled-probe"))
                    .andExpect(header().exists("traceparent"));

            assertThat(spanStore.finishedSpans()).anySatisfy(span ->
                    assertThat(span.getName()).isEqualTo("http.server"));
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
