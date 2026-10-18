package com.aicode.framework.observability.domain;

/**
 * 监控后端健康探测端口（Week 20）。
 *
 * <p>实现必须满足三条约定（否则自检接口会变成故障放大器）：</p>
 * <ol>
 *   <li><b>不抛异常</b>：连接失败 / 超时 / 非法 URL 一律返回
 *       {@link MonitoringProbeResult#down(String)}；</li>
 *   <li><b>有超时上界</b>：单次探测不得超过配置的 {@code monitoring.probe.timeout-ms}；</li>
 *   <li><b>可缓存</b>：同一 URL 在 TTL 内复用结果，避免每次自检都打一轮后端。</li>
 * </ol>
 *
 * <p>领域层只依赖本接口，{@code java.net.http.HttpClient} 等实现细节不出现在
 * 用例代码里（规范 3.3）。</p>
 */
public interface MonitoringProbePort {

    /**
     * 探测一个 HTTP 健康端点。
     *
     * @param url 完整 URL；空白返回不可达结果
     * @return 探测结果，永不为 null
     */
    MonitoringProbeResult probe(String url);
}
