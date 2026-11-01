package com.aicode.core.infrastructure.llm;

import com.aicode.core.domain.exception.StreamCancelledException;
import com.aicode.core.domain.model.ChatMessage;
import com.aicode.core.domain.model.ChatOptions;
import com.aicode.core.domain.model.ChatResult;
import com.aicode.core.domain.model.ChatStreamListener;
import com.aicode.core.domain.model.MessageRole;
import com.aicode.core.infrastructure.config.LlmProperties;
import com.sun.net.httpserver.HttpServer;
import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.Test;
import org.springframework.http.client.SimpleClientHttpRequestFactory;
import org.springframework.web.client.RestClient;

import java.io.OutputStream;
import java.net.InetSocketAddress;
import java.nio.charset.StandardCharsets;
import java.util.ArrayList;
import java.util.List;
import java.util.concurrent.atomic.AtomicBoolean;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

/**
 * 原生 SSE 适配器的"真流式"证明（Week 31）。
 *
 * <p>起一个本地 SSE 服务：第一块立即写、第二块 300ms 后写。若适配器逐块回调，
 * 首块应在 300ms 前到达；若像 Spring AI 那样攒到结束，首块会在 300ms 之后。</p>
 */
class OpenAiCompatibleChatStreamTest {

    private HttpServer server;

    @AfterEach
    void stopServer() {
        if (server != null) {
            server.stop(0);
        }
    }

    @Test
    void deliversChunksBeforeStreamEnds() throws Exception {
        server = HttpServer.create(new InetSocketAddress("127.0.0.1", 0), 0);
        server.createContext("/chat/completions", exchange -> {
            exchange.getResponseHeaders().add("Content-Type", "text/event-stream");
            exchange.sendResponseHeaders(200, 0);
            try (OutputStream out = exchange.getResponseBody()) {
                write(out, "data: {\"choices\":[{\"delta\":{\"content\":\"你\"}}]}\n\n");
                sleep(800);
                write(out, "data: {\"choices\":[{\"delta\":{\"content\":\"好\"}}]}\n\n");
                write(out, "data: {\"choices\":[],\"usage\":{\"prompt_tokens\":1,"
                        + "\"completion_tokens\":2,\"total_tokens\":3}}\n\n");
                write(out, "data: [DONE]\n\n");
            }
        });
        server.start();

        OpenAiCompatibleChatModelAdapter adapter = adapter(server);
        List<Long> deltaTimes = new ArrayList<>();
        long start = System.nanoTime();
        ChatResult result = adapter.chatStream(
                List.of(new ChatMessage(MessageRole.USER, "你好")),
                new ChatOptions("deepseek-chat", 0.7, 64),
                text -> deltaTimes.add((System.nanoTime() - start) / 1_000_000L));

        assertThat(deltaTimes).hasSize(2);
        // 相对断言：首块必须在第二块之前很久到达（缓冲实现会两块同时到）。
        assertThat(deltaTimes.get(0)).isLessThan(600L);
        assertThat(deltaTimes.get(1) - deltaTimes.get(0)).isGreaterThanOrEqualTo(400L);
        assertThat(result.content()).isEqualTo("你好");
        assertThat(result.usage().totalTokens()).isEqualTo(3);
    }

    @Test
    void stopsReadingWhenListenerCancels() throws Exception {
        server = HttpServer.create(new InetSocketAddress("127.0.0.1", 0), 0);
        server.createContext("/chat/completions", exchange -> {
            exchange.getResponseHeaders().add("Content-Type", "text/event-stream");
            exchange.sendResponseHeaders(200, 0);
            try (OutputStream out = exchange.getResponseBody()) {
                write(out, "data: {\"choices\":[{\"delta\":{\"content\":\"a\"}}]}\n\n");
                sleep(200);
                write(out, "data: {\"choices\":[{\"delta\":{\"content\":\"b\"}}]}\n\n");
                write(out, "data: [DONE]\n\n");
            }
        });
        server.start();

        OpenAiCompatibleChatModelAdapter adapter = adapter(server);
        AtomicBoolean cancelled = new AtomicBoolean();
        ChatStreamListener listener = new ChatStreamListener() {
            @Override
            public void onDelta(String delta) {
                cancelled.set(true);
            }

            @Override
            public boolean isCancelled() {
                return cancelled.get();
            }
        };

        assertThatThrownBy(() -> adapter.chatStream(
                List.of(new ChatMessage(MessageRole.USER, "你好")),
                new ChatOptions("deepseek-chat", 0.7, 64),
                listener))
                .isInstanceOf(StreamCancelledException.class);
    }

    private OpenAiCompatibleChatModelAdapter adapter(HttpServer server) {
        LlmProperties properties = new LlmProperties(
                "http://127.0.0.1:" + server.getAddress().getPort(),
                "test-key", "deepseek-chat", 128, 0.7, 30, "", 7890);
        RestClient restClient = RestClient.builder()
                .baseUrl(properties.baseUrl())
                .requestFactory(new SimpleClientHttpRequestFactory())
                .build();
        return new OpenAiCompatibleChatModelAdapter(
                restClient, new OpenAiChatResponseMapper(), properties);
    }

    private static void write(OutputStream out, String frame) throws java.io.IOException {
        out.write(frame.getBytes(StandardCharsets.UTF_8));
        out.flush();
    }

    private static void sleep(long millis) {
        try {
            Thread.sleep(millis);
        } catch (InterruptedException ex) {
            Thread.currentThread().interrupt();
        }
    }
}
