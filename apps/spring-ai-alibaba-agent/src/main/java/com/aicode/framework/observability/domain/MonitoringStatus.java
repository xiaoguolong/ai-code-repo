package com.aicode.framework.observability.domain;

/**
 * 监控组件状态（Week 20）。
 *
 * <p>四个值刻意区分「没开」与「坏了」：把 {@code DISABLED} 报成 {@code DOWN} 会制造假故障，
 * 反过来把 {@code DOWN} 报成 {@code DISABLED} 会让真故障被静默吞掉。</p>
 */
public enum MonitoringStatus {

    /** 已启用且探测可达。 */
    UP,

    /** 已启用但探测不可达（连接失败 / 超时 / 非 2xx-3xx）。 */
    DOWN,

    /** 未启用（配置关闭或地址未填写）：不参与健康判定，也不发起探测。 */
    DISABLED,

    /** 已启用但缺少判定依据（例如没有探针可用）。 */
    UNKNOWN
}
