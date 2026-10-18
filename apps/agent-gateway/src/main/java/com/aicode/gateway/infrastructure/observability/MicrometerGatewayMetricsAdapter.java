package com.aicode.gateway.infrastructure.observability;

import com.aicode.gateway.domain.model.GatewayMetricNames;
import com.aicode.gateway.domain.port.GatewayMetricsPort;
import io.micrometer.core.instrument.MeterRegistry;
import org.springframework.stereotype.Component;

import java.time.Duration;

/**
 * 网关指标端口的 Micrometer 实现（Week 20）。
 *
 * <p>唯一允许出现 {@code io.micrometer.*} 的网关类。指标经 Actuator 的
 * {@code /actuator/prometheus} 暴露，由 Prometheus 抓取后在 Grafana 网关面板展示。</p>
 *
 * <p>三条实现约定：</p>
 * <ul>
 *   <li><b>不抛异常</b>：登记失败只吞掉，业务请求不受影响（与 Week 17/19 可观测降级同口径）；</li>
 *   <li><b>标签归一</b>：空 outcome / 空错误码一律记 {@code UNKNOWN}，避免出现空标签时间序列；</li>
 *   <li><b>负耗时归零</b>：{@code System.nanoTime()} 差值理论上不为负，但外部传入时不能写崩 Timer。</li>
 * </ul>
 */
@Component
public class MicrometerGatewayMetricsAdapter implements GatewayMetricsPort {

    private final MeterRegistry meterRegistry;

    /**
     * @param meterRegistry 指标注册表（Actuator 自动装配，Prometheus 出口在 {@code /actuator/prometheus}）
     */
    public MicrometerGatewayMetricsAdapter(MeterRegistry meterRegistry) {
        this.meterRegistry = meterRegistry;
    }

    @Override
    public void sessionLogin(String outcome) {
        count(GatewayMetricNames.LOGIN, GatewayMetricNames.TAG_OUTCOME, normalize(outcome));
    }

    @Override
    public void catalogLookup(boolean cacheHit) {
        count(GatewayMetricNames.CATALOG, GatewayMetricNames.TAG_CACHE_HIT, Boolean.toString(cacheHit));
    }

    @Override
    public void agentRun(String outcome, long durationMs) {
        String normalized = normalize(outcome);
        count(GatewayMetricNames.RUN, GatewayMetricNames.TAG_OUTCOME, normalized);
        try {
            meterRegistry.timer(GatewayMetricNames.RUN_DURATION, GatewayMetricNames.TAG_OUTCOME, normalized)
                    .record(Duration.ofMillis(Math.max(durationMs, 0L)));
        } catch (RuntimeException ex) {
            // 忽略：指标登记失败不得影响业务
        }
    }

    @Override
    public void upstreamFailure(String errorCode) {
        count(GatewayMetricNames.UPSTREAM_ERROR, GatewayMetricNames.TAG_ERROR_CODE, normalize(errorCode));
    }

    /** 计数器自增；异常吞掉。 */
    private void count(String name, String tagKey, String tagValue) {
        try {
            meterRegistry.counter(name, tagKey, tagValue).increment();
        } catch (RuntimeException ex) {
            // 忽略：指标登记失败不得影响业务
        }
    }

    /** 空值归一为 {@code UNKNOWN}，防止空标签。 */
    static String normalize(String value) {
        return value == null || value.isBlank() ? GatewayMetricNames.UNKNOWN : value.trim();
    }
}
