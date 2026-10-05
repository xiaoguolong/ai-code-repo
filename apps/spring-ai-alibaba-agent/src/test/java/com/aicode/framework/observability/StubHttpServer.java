package com.aicode.framework.observability;

import com.sun.net.httpserver.HttpExchange;
import com.sun.net.httpserver.HttpServer;

import java.io.IOException;
import java.net.InetSocketAddress;
import java.nio.charset.StandardCharsets;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Locale;
import java.util.Map;
import java.util.concurrent.CopyOnWriteArrayList;
import java.util.concurrent.atomic.AtomicInteger;
import java.util.concurrent.atomic.AtomicReference;

/**
 * 本地 HTTP 桩（Week 18）：用 JDK 自带 {@code com.sun.net.httpserver} 起一个临时服务，
 * 记录收到的请求（方法 / 路径 / 头 / 正文）并按预设响应返回。
 *
 * <p>为什么不用 Mockito 或 WireMock：本周要验证的是<b>真实 HTTP 行为</b>——
 * OTLP 导出是否打对了路径与认证头、Prompt 拉取是否带了 label 与 Basic 认证。
 * 只有让真实客户端发一次真实请求才能证明，且零新增测试依赖。</p>
 */
public final class StubHttpServer implements AutoCloseable {

    private final HttpServer server;
    private final List<RecordedRequest> requests = new CopyOnWriteArrayList<>();
    private final AtomicInteger status = new AtomicInteger(200);
    private final AtomicReference<String> contentType = new AtomicReference<>("application/json");
    private final AtomicReference<String> responseBody = new AtomicReference<>("");

    private StubHttpServer(HttpServer server) {
        this.server = server;
    }

    /** 在随机端口启动一个桩服务。 */
    public static StubHttpServer start() throws IOException {
        HttpServer server = HttpServer.create(new InetSocketAddress("127.0.0.1", 0), 0);
        StubHttpServer stub = new StubHttpServer(server);
        server.createContext("/", stub::handle);
        server.start();
        return stub;
    }

    /** 根地址，形如 {@code http://127.0.0.1:34567}（无结尾斜杠）。 */
    public String baseUrl() {
        return "http://127.0.0.1:" + server.getAddress().getPort();
    }

    /** 收到的全部请求（按时间顺序）。 */
    public List<RecordedRequest> requests() {
        return List.copyOf(requests);
    }

    /** 最近一次请求；没有请求时抛断言错误，避免测试静默通过。 */
    public RecordedRequest lastRequest() {
        if (requests.isEmpty()) {
            throw new AssertionError("no request reached the stub server");
        }
        return requests.get(requests.size() - 1);
    }

    /** 预设响应。 */
    public void respond(int status, String contentType, String body) {
        this.status.set(status);
        this.contentType.set(contentType);
        this.responseBody.set(body == null ? "" : body);
    }

    @Override
    public void close() {
        server.stop(0);
    }

    private void handle(HttpExchange exchange) throws IOException {
        byte[] body = exchange.getRequestBody().readAllBytes();
        Map<String, String> headers = new LinkedHashMap<>();
        exchange.getRequestHeaders().forEach((key, values) ->
                headers.put(key.toLowerCase(Locale.ROOT), String.join(",", values)));
        requests.add(new RecordedRequest(exchange.getRequestMethod(), exchange.getRequestURI().toString(),
                headers, body));

        byte[] out = responseBody.get().getBytes(StandardCharsets.UTF_8);
        exchange.getResponseHeaders().add("Content-Type", contentType.get());
        if (out.length == 0) {
            exchange.sendResponseHeaders(status.get(), -1);
        } else {
            exchange.sendResponseHeaders(status.get(), out.length);
            exchange.getResponseBody().write(out);
        }
        exchange.close();
    }

    /**
     * 一次已记录的请求。
     *
     * @param method  HTTP 方法
     * @param path    请求路径（含查询串）
     * @param headers 请求头，键统一小写
     * @param body    请求正文
     */
    public record RecordedRequest(String method, String path, Map<String, String> headers, byte[] body) {

        /** 正文按 UTF-8 文本读取。 */
        public String bodyText() {
            return new String(body, StandardCharsets.UTF_8);
        }

        /** 取请求头（大小写不敏感）。 */
        public String header(String name) {
            return headers.get(name.toLowerCase(Locale.ROOT));
        }
    }
}
