package com.aicode.framework.observability.infrastructure;

import com.aicode.framework.observability.LangfuseTestFixtures;
import com.aicode.framework.observability.StubHttpServer;
import com.fasterxml.jackson.databind.ObjectMapper;
import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;

import java.io.IOException;

import static org.assertj.core.api.Assertions.assertThat;

/**
 * Langfuse Prompt 拉取客户端测试（Week 18）。
 *
 * <p>用本地真实 HTTP 桩验证请求形态（Basic 认证、label 查询参数）与「任何异常都不抛」——
 * Prompt 服务不可用不能变成业务 5xx。同时覆盖 text 与 chat 两种 Prompt 类型的文本提取。</p>
 */
class LangfusePromptClientTest {

    private StubHttpServer server;
    private LangfusePromptClient client;

    @BeforeEach
    void setUp() throws IOException {
        server = StubHttpServer.start();
        client = new LangfusePromptClient(LangfuseTestFixtures.enabledAt(server.baseUrl()), new ObjectMapper());
    }

    @AfterEach
    void tearDown() {
        server.close();
    }

    @Test
    void fetchesTextPromptWithBasicAuthAndLabel() {
        server.respond(200, "application/json",
                "{\"type\":\"text\",\"prompt\":\"你是医疗报告助手\",\"version\":3,\"labels\":[\"production\"]}");

        var fetch = client.fetch("medical-report");

        assertThat(fetch.payload()).isPresent();
        assertThat(fetch.payload().get().text()).isEqualTo("你是医疗报告助手");
        assertThat(fetch.payload().get().version()).isEqualTo("3");
        assertThat(fetch.outcome()).isEqualTo(LangfusePromptClient.OUTCOME_HIT);

        StubHttpServer.RecordedRequest request = server.lastRequest();
        assertThat(request.method()).isEqualTo("GET");
        assertThat(request.path()).isEqualTo("/api/public/v2/prompts/medical-report?label=production");
        assertThat(request.header("authorization")).startsWith("Basic ");
        // 真实网络踩过的坑：明文 HTTP 下 JDK HttpClient 默认先尝试 h2c 升级，跨机路径会失败
        // （HTTP/1.1 header parser received no bytes），表现为 Prompt 永远回退本地模板。
        // 因此必须不带 Upgrade 头、直接用 HTTP/1.1 发。
        assertThat(request.header("upgrade")).isNull();
    }

    @Test
    void extractsSystemMessageFromChatPrompt() {
        server.respond(200, "application/json",
                "{\"type\":\"chat\",\"prompt\":[{\"role\":\"system\",\"content\":\"你是报告助手\"},"
                        + "{\"role\":\"user\",\"content\":\"{{question}}\"}],\"version\":5}");

        var fetch = client.fetch("medical-report");

        assertThat(fetch.payload()).isPresent();
        assertThat(fetch.payload().get().text()).isEqualTo("你是报告助手");
        assertThat(fetch.payload().get().version()).isEqualTo("5");
    }

    @Test
    void reportsMissOnNotFound() {
        server.respond(404, "application/json", "{\"message\":\"prompt not found\"}");

        var fetch = client.fetch("not-exists");

        assertThat(fetch.payload()).isEmpty();
        assertThat(fetch.outcome()).isEqualTo(LangfusePromptClient.OUTCOME_MISS);
    }

    @Test
    void reportsFallbackOnServerErrorAndMalformedBody() {
        server.respond(500, "application/json", "boom");
        assertThat(client.fetch("medical-report").outcome()).isEqualTo(LangfusePromptClient.OUTCOME_FALLBACK);

        server.respond(200, "application/json", "not-json");
        assertThat(client.fetch("medical-report").outcome()).isEqualTo(LangfusePromptClient.OUTCOME_FALLBACK);
    }

    @Test
    void returnsEmptyWithoutHttpCallWhenNameBlank() {
        assertThat(client.fetch("  ").payload()).isEmpty();
        assertThat(server.requests()).isEmpty();
    }

    @Test
    void reportsFallbackWhenServerUnreachable() throws IOException {
        StubHttpServer dead = StubHttpServer.start();
        String baseUrl = dead.baseUrl();
        dead.close();

        LangfusePromptClient unreachable = new LangfusePromptClient(
                LangfuseTestFixtures.enabledAt(baseUrl), new ObjectMapper());

        var fetch = unreachable.fetch("medical-report");

        assertThat(fetch.payload()).isEmpty();
        assertThat(fetch.outcome()).isEqualTo(LangfusePromptClient.OUTCOME_FALLBACK);
    }
}
