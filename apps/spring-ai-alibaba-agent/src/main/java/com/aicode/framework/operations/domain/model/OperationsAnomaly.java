package com.aicode.framework.operations.domain.model;

/**
 * 运营异常条目（Week 24）。
 *
 * <p>每个条目都带「实测值 + 阈值」，让日报上的结论可复核：读者不需要相信模型，
 * 只需要按 code 回到 {@code execution_record} 复算。</p>
 *
 * @param code      规则码（稳定契约）
 * @param level     级别（影响排序与措辞）
 * @param message   中文说明，含实测值
 * @param value     触发时的实测值（比率/条数/倍数）
 * @param threshold 规则阈值（无阈值的规则填 0）
 */
public record OperationsAnomaly(
        OperationsAnomalyCode code,
        OperationsAnomalyLevel level,
        String message,
        double value,
        double threshold
) {

    public OperationsAnomaly {
        message = message == null ? "" : message;
    }
}
