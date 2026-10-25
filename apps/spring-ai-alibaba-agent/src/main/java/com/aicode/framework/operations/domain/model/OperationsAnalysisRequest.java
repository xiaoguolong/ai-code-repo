package com.aicode.framework.operations.domain.model;

import com.aicode.framework.platform.domain.model.ExecutionRecord;

import java.time.Instant;
import java.util.List;

/**
 * 一次运营分析的输入（Week 24）。
 *
 * <p>由用例层组装：窗口边界、当前/上一窗口的执行记录、待审数量与截断标记。
 * Agent 因此不接触数据库/权限，只做「统计 → 异常 → 日报」。</p>
 *
 * @param windowStart      窗口起点（含）
 * @param windowEnd        窗口终点（不含）
 * @param windowDays       窗口天数
 * @param currentRecords   本窗口执行记录
 * @param previousRecords  上一等长窗口执行记录（只用于 token 突增对比）
 * @param pendingApprovals 当前可见范围内的待审审批数
 * @param truncated        是否因读取上限被截断
 */
public record OperationsAnalysisRequest(
        Instant windowStart,
        Instant windowEnd,
        int windowDays,
        List<ExecutionRecord> currentRecords,
        List<ExecutionRecord> previousRecords,
        long pendingApprovals,
        boolean truncated
) {

    public OperationsAnalysisRequest {
        currentRecords = currentRecords == null ? List.of() : List.copyOf(currentRecords);
        previousRecords = previousRecords == null ? List.of() : List.copyOf(previousRecords);
    }
}
