package com.aicode.framework.observability.infrastructure;

import com.aicode.core.domain.model.PromptDescriptor;
import com.aicode.core.domain.model.PromptTemplate;
import com.aicode.core.domain.port.PromptTemplatePort;
import com.aicode.framework.observability.LangfuseTestFixtures;
import com.aicode.framework.observability.StubHttpServer;
import com.aicode.framework.observability.domain.AgentObservabilityPort;
import com.aicode.framework.observability.domain.PromptReference;
import com.fasterxml.jackson.databind.ObjectMapper;
import io.micrometer.core.instrument.simple.SimpleMeterRegistry;
import io.micrometer.tracing.test.simple.SimpleTracer;
import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;

import java.io.IOException;
import java.util.List;
import java.util.Map;

import static org.assertj.core.api.Assertions.assertThat;

/**
 * Langfuse Prompt 适配器测试（Week 18）。
 *
 * <p>三条契约：① 命中时用 Langfuse 的 Prompt 文本与版本；② 客户端缓存生效（TTL 内零网络请求）；
 * ③ 404 / 5xx / 网络不可达一律<b>回退 classpath 模板且不抛异常</b>——Prompt 服务不能成为业务单点。</p>
 */
class LangfusePromptAdapterTest {

    private StubHttpServer server;
    private SimpleMeterRegistry registry;
    private LocalPrompts local;
    private LangfusePromptTracker tracker;

    @BeforeEach
    void setUp() throws IOException {
        server = StubHttpServer.start();
        registry = new SimpleMeterRegistry();
        local = new LocalPrompts();
        tracker = new LangfusePromptTracker();
    }

    @AfterEach
    void tearDown() {
        server.close();
        tracker.clear();
    }

    @Test
    void usesLangfusePromptAndCachesWithinTtl() {
        server.respond(200, "application/json",
                "{\"type\":\"text\",\"prompt\":\"你是医疗报告助手\",\"version\":3}");
        LangfusePromptAdapter adapter = adapter(60);

        PromptTemplate first = adapter.load("medical-report");
        PromptTemplate second = adapter.load("medical-report");

        assertThat(first.content()).isEqualTo("你是医疗报告助手");
        assertThat(first.version()).isEqualTo("3");
        assertThat(second.content()).isEqualTo("你是医疗报告助手");
        assertThat(server.requests()).hasSize(1);
        assertThat(local.renderCalls).isZero();
        assertThat(tracker.match("你是医疗报告助手")).isEqualTo(new PromptReference("medical-report", "3"));
        assertThat(registry.find("langfuse.prompt.fetch.count").tag("outcome", "hit").counter().count())
                .isEqualTo(1.0);
    }

    @Test
    void rendersVariablesWithLocalTemplateEngine() {
        server.respond(200, "application/json",
                "{\"type\":\"text\",\"prompt\":\"你好 {{name}}，请生成报告\",\"version\":4}");
        LangfusePromptAdapter adapter = adapter(60);

        PromptTemplate rendered = adapter.render("medical-report", Map.of("name", "张三"));

        assertThat(rendered.content()).isEqualTo("你好 张三，请生成报告");
        assertThat(rendered.version()).isEqualTo("4");
        assertThat(tracker.match("你好 张三，请生成报告")).isNotNull();
    }

    @Test
    void fallsBackToLocalTemplateOnNotFound() {
        server.respond(404, "application/json", "{\"message\":\"not found\"}");
        LangfusePromptAdapter adapter = adapter(60);

        PromptTemplate template = adapter.load("medical-report");

        assertThat(template.content()).isEqualTo("local:medical-report");
        assertThat(local.renderCalls).isEqualTo(1);
        assertThat(registry.find("langfuse.prompt.fetch.count").tag("outcome", "miss").counter().count())
                .isEqualTo(1.0);
        assertThat(tracker.match("local:medical-report")).isNull();
    }

    @Test
    void fallsBackToLocalTemplateOnServerErrorAndNetworkFailure() throws IOException {
        server.respond(500, "application/json", "boom");
        LangfusePromptAdapter adapter = adapter(0);

        assertThat(adapter.load("medical-report").content()).isEqualTo("local:medical-report");
        assertThat(registry.find("langfuse.prompt.fetch.count").tag("outcome", "fallback").counter().count())
                .isEqualTo(1.0);

        StubHttpServer dead = StubHttpServer.start();
        String deadUrl = dead.baseUrl();
        dead.close();
        LangfusePromptAdapter unreachable = new LangfusePromptAdapter(local, client(deadUrl), tracker,
                LangfuseTestFixtures.promptEnabledAt(deadUrl, 0), observability());

        assertThat(unreachable.load("medical-followup").content()).isEqualTo("local:medical-followup");
    }

    @Test
    void refetchesAfterTtlExpires() {
        server.respond(200, "application/json",
                "{\"type\":\"text\",\"prompt\":\"你是医疗报告助手\",\"version\":3}");
        LangfusePromptAdapter adapter = adapter(0);

        adapter.load("medical-report");
        adapter.load("medical-report");

        assertThat(server.requests()).hasSize(2);
    }

    @Test
    void delegatesListToLocalTemplates() {
        LangfusePromptAdapter adapter = adapter(60);

        assertThat(adapter.list()).extracting(PromptDescriptor::name).containsExactly("medical-report");
    }

    private LangfusePromptAdapter adapter(int cacheTtlSeconds) {
        return new LangfusePromptAdapter(local, client(server.baseUrl()), tracker,
                LangfuseTestFixtures.promptEnabledAt(server.baseUrl(), cacheTtlSeconds), observability());
    }

    private LangfusePromptClient client(String host) {
        return new LangfusePromptClient(LangfuseTestFixtures.enabledAt(host), new ObjectMapper());
    }

    private AgentObservabilityPort observability() {
        return new MicrometerObservabilityAdapter(new SimpleTracer(), registry);
    }

    /** 本地模板假实现：记录渲染次数，内容可断言回退确实发生。 */
    private static final class LocalPrompts implements PromptTemplatePort {

        private int renderCalls;

        @Override
        public PromptTemplate load(String name) {
            return render(name, Map.of());
        }

        @Override
        public PromptTemplate render(String name, Map<String, Object> variables) {
            renderCalls++;
            return new PromptTemplate("v1", "local:" + name);
        }

        @Override
        public List<PromptDescriptor> list() {
            return List.of(new PromptDescriptor("medical-report", "v1"));
        }
    }
}
