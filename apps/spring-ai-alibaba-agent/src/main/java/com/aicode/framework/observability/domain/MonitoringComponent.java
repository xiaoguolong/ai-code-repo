package com.aicode.framework.observability.domain;

/**
 * 一个监控组件的体检结果（Week 20）。
 *
 * <p>只承载「对外可展示」的信息：端点地址（已脱敏，不含账号密码）、状态、说明与控制台链接。
 * <b>禁止</b>把 API Key、密码、Token 放进这里的任何字段（规范 5.7）。</p>
 *
 * @param key        稳定标识（{@code otel} / {@code prometheus} / {@code grafana} / {@code skywalking} /
 *                   {@code langfuse} / {@code gateway-metrics}），前端与脚本按它取值
 * @param name       展示名
 * @param enabled    是否启用；{@code false} 时状态恒为 {@link MonitoringStatus#DISABLED}
 * @param status     体检状态
 * @param endpoint   端点地址（脱敏后），未配置时为空串
 * @param detail     一句说明（不能含密钥与堆栈）
 * @param consoleUrl 控制台入口（可点击查看明细），没有时为空串
 */
public record MonitoringComponent(
        String key,
        String name,
        boolean enabled,
        MonitoringStatus status,
        String endpoint,
        String detail,
        String consoleUrl
) {

    /** 空值归一为「已关闭」，避免前端收到 null 状态。 */
    public MonitoringComponent {
        key = key == null ? "" : key;
        name = name == null ? "" : name;
        status = status == null ? MonitoringStatus.UNKNOWN : status;
        endpoint = endpoint == null ? "" : endpoint;
        detail = detail == null ? "" : detail;
        consoleUrl = consoleUrl == null ? "" : consoleUrl;
    }

    /**
     * 是否健康：只有 {@code UP} 算健康（{@code DISABLED} 不参与判定，见
     * {@link MonitoringOverviewView#allHealthy()}）。
     *
     * @return 状态为 UP 时返回 true
     */
    public boolean healthy() {
        return status == MonitoringStatus.UP;
    }
}
