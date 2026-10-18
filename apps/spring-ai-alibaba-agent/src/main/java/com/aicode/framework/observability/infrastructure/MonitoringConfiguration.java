package com.aicode.framework.observability.infrastructure;

import com.aicode.framework.observability.domain.MonitoringProbePort;
import org.springframework.boot.autoconfigure.condition.ConditionalOnMissingBean;
import org.springframework.boot.context.properties.EnableConfigurationProperties;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;

/**
 * 监控探测装配（Week 20）。
 *
 * <p>只注册一个 Bean：{@link MonitoringProbePort}。用例（{@code PlatformMonitoringUseCase}）
 * 与具体 HTTP 实现解耦，单测直接传 lambda 即可，不需要启动 HTTP 服务。</p>
 *
 * <p>{@code @ConditionalOnMissingBean} 留给测试/特殊环境替换探针实现
 * （例如离线演示时用「全部 UP」的假探针）。</p>
 */
@Configuration
@EnableConfigurationProperties(MonitoringProperties.class)
public class MonitoringConfiguration {

    /**
     * HTTP 健康探针。
     *
     * @param properties 监控配置
     * @return 探针实现
     */
    @Bean
    @ConditionalOnMissingBean(MonitoringProbePort.class)
    public MonitoringProbePort monitoringProbePort(MonitoringProperties properties) {
        return new HttpMonitoringProbe(properties);
    }
}
