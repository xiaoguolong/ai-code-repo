package com.aicode.framework.observability.infrastructure;

import com.aicode.framework.observability.domain.MonitoringProbePort;
import com.aicode.framework.observability.domain.MonitoringProbeResult;

import java.io.IOException;
import java.net.URI;
import java.net.http.HttpClient;
import java.net.http.HttpRequest;
import java.net.http.HttpResponse;
import java.net.http.HttpTimeoutException;
import java.time.Duration;
import java.util.Map;
import java.util.concurrent.ConcurrentHashMap;
import java.util.function.LongSupplier;

/**
 * 基于 JDK {@link HttpClient} 的监控探测适配器（Week 20）。
 *
 * <p>三条设计约束（与 {@link MonitoringProbePort} 的契约一一对应）：</p>
 * <ul>
 *   <li><b>不抛异常</b>：连接失败、超时、非法 URL 全部转成 {@code DOWN} 结果；
 *       自检接口的价值恰恰在后端挂掉时，不能因为探测失败而 500；</li>
 *   <li><b>有超时上界</b>：连接超时与请求超时都取 {@code monitoring.probe.timeout-ms}；</li>
 *   <li><b>按 URL 缓存</b>：TTL 内复用结果，避免每次自检都串行打三个后端。
 *       缓存 key 只含 URL，不含时间/用户维度（探测结果与调用者无关）。</li>
 * </ul>
 *
 * <p>失败摘要只写异常短名（如 {@code ConnectException}），<b>不写完整堆栈</b>，
 * 避免把内部网络细节通过自检接口带出去。</p>
 */
public class HttpMonitoringProbe implements MonitoringProbePort {

    private final HttpClient httpClient;
    private final long timeoutMs;
    private final long cacheTtlSeconds;
    private final LongSupplier clock;
    private final Map<String, CachedResult> cache = new ConcurrentHashMap<>();

    /**
     * 按配置构造。
     *
     * @param properties 监控配置（读取 {@code probe.*}）
     */
    public HttpMonitoringProbe(MonitoringProperties properties) {
        this(properties.resolvedProbeTimeoutMs(), properties.resolvedProbeCacheTtlSeconds(),
                System::currentTimeMillis);
    }

    /**
     * 可注入时钟的构造器（单测验证缓存过期，不真的 sleep）。
     *
     * @param timeoutMs       单次探测超时（毫秒）
     * @param cacheTtlSeconds 结果缓存 TTL（秒），{@code <=0} 表示不缓存
     * @param clock           当前毫秒时钟
     */
    public HttpMonitoringProbe(long timeoutMs, long cacheTtlSeconds, LongSupplier clock) {
        this.timeoutMs = timeoutMs <= 0 ? MonitoringProperties.DEFAULT_TIMEOUT_MS : timeoutMs;
        this.cacheTtlSeconds = Math.max(cacheTtlSeconds, 0L);
        this.clock = clock == null ? System::currentTimeMillis : clock;
        this.httpClient = HttpClient.newBuilder()
                .connectTimeout(Duration.ofMillis(this.timeoutMs))
                // 不跟随重定向：3xx 本身就是「进程在监听」的有效证据，
                // 跟随反而会因缺 Location 抛 IOException，把可达误判为不可达。
                .followRedirects(HttpClient.Redirect.NEVER)
                .build();
    }

    @Override
    public MonitoringProbeResult probe(String url) {
        if (url == null || url.isBlank()) {
            return MonitoringProbeResult.down("未配置地址");
        }
        String key = url.trim();
        long now = clock.getAsLong();
        CachedResult cached = cache.get(key);
        if (cached != null && now < cached.expiresAt()) {
            return cached.result();
        }
        MonitoringProbeResult result = doProbe(key);
        if (cacheTtlSeconds > 0) {
            cache.put(key, new CachedResult(now + cacheTtlSeconds * 1000L, result));
        }
        return result;
    }

    /** 真正发一次 GET；任何异常都转成 DOWN，不向外抛。 */
    private MonitoringProbeResult doProbe(String url) {
        try {
            HttpRequest request = HttpRequest.newBuilder(URI.create(url))
                    .timeout(Duration.ofMillis(timeoutMs))
                    .header("Accept", "application/json, text/plain, */*")
                    .GET()
                    .build();
            HttpResponse<Void> response = httpClient.send(request, HttpResponse.BodyHandlers.discarding());
            int status = response.statusCode();
            if (status >= 200 && status < 400) {
                return MonitoringProbeResult.up(status);
            }
            return new MonitoringProbeResult(false, status, "HTTP " + status);
        } catch (IllegalArgumentException ex) {
            return MonitoringProbeResult.down("非法 URL");
        } catch (HttpTimeoutException ex) {
            return MonitoringProbeResult.down("timeout(" + timeoutMs + "ms)");
        } catch (InterruptedException ex) {
            Thread.currentThread().interrupt();
            return MonitoringProbeResult.down("interrupted");
        } catch (IOException ex) {
            return MonitoringProbeResult.down(ex.getClass().getSimpleName());
        } catch (RuntimeException ex) {
            return MonitoringProbeResult.down(ex.getClass().getSimpleName());
        }
    }

    /**
     * 一次缓存条目。
     *
     * @param expiresAt 过期时间戳（毫秒）
     * @param result    结果
     */
    private record CachedResult(long expiresAt, MonitoringProbeResult result) {
    }
}
