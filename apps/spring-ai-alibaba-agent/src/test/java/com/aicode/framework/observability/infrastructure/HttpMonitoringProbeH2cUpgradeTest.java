package com.aicode.framework.observability.infrastructure;

import com.aicode.framework.observability.domain.MonitoringProbeResult;
import org.junit.jupiter.api.Test;

import java.io.IOException;
import java.io.InputStream;
import java.io.OutputStream;
import java.net.InetAddress;
import java.net.ServerSocket;
import java.net.Socket;
import java.nio.charset.StandardCharsets;
import java.util.concurrent.atomic.AtomicReference;

import static org.assertj.core.api.Assertions.assertThat;

/**
 * 监控探针必须走 HTTP/1.1（Week 22）。
 *
 * <p>真实环境（云端 Langfuse / Next.js）会拒绝 JDK HttpClient 默认发起的 <b>h2c 升级</b>：
 * 服务端看到 {@code Upgrade: h2c} 直接断开，客户端得到
 * {@code IOException: HTTP/1.1 header parser received no bytes}，
 * 于是「后端明明是健康的」却被自检报成 DOWN。</p>
 *
 * <p>第 18 周在 Langfuse 导出器上踩过同一个坑（当时修的是导出器），
 * 第 22 周应用真正上云、监控自检第一次在真实 Langfuse 上跑，探针又踩了一次。
 * 本用例用一个「看到 h2c 就断开」的假服务端把这个行为钉死。</p>
 */
class HttpMonitoringProbeH2cUpgradeTest {

    @Test
    void probeSucceedsAgainstServerThatRejectsH2cUpgrade() throws Exception {
        AtomicReference<String> requestHead = new AtomicReference<>("");
        try (ServerSocket server = new ServerSocket(0, 1, InetAddress.getLoopbackAddress())) {
            Thread serverThread = new Thread(() -> handleOneRequest(server, requestHead),
                    "h2c-rejecting-server");
            serverThread.setDaemon(true);
            serverThread.start();

            HttpMonitoringProbe probe = new HttpMonitoringProbe(2000, 0, System::currentTimeMillis);
            MonitoringProbeResult result = probe.probe(
                    "http://127.0.0.1:" + server.getLocalPort() + "/api/public/health");
            serverThread.join(3000);

            assertThat(result.reachable())
                    .as("探针必须强制 HTTP/1.1；请求头=%s", requestHead.get())
                    .isTrue();
            assertThat(result.statusCode()).isEqualTo(200);
            assertThat(requestHead.get().toLowerCase()).doesNotContain("upgrade: h2c");
        }
    }

    /**
     * 模拟「拒绝 h2c 升级」的服务端：读到 Upgrade: h2c 就直接断开（不给任何响应），
     * 否则返回 200。
     */
    private void handleOneRequest(ServerSocket server, AtomicReference<String> requestHead) {
        try (Socket socket = server.accept()) {
            socket.setSoTimeout(3000);
            String head = readHead(socket.getInputStream());
            requestHead.set(head);
            if (head.toLowerCase().contains("upgrade: h2c")) {
                return;
            }
            OutputStream out = socket.getOutputStream();
            byte[] body = "{\"status\":\"ok\"}".getBytes(StandardCharsets.UTF_8);
            out.write(("HTTP/1.1 200 OK\r\nContent-Type: application/json\r\nContent-Length: "
                    + body.length + "\r\nConnection: close\r\n\r\n").getBytes(StandardCharsets.UTF_8));
            out.write(body);
            out.flush();
        } catch (IOException ignored) {
            // 断开即模拟成功
        }
    }

    private String readHead(InputStream in) throws IOException {
        StringBuilder builder = new StringBuilder();
        int current;
        while ((current = in.read()) != -1) {
            builder.append((char) current);
            if (builder.toString().endsWith("\r\n\r\n")) {
                break;
            }
        }
        return builder.toString();
    }
}
