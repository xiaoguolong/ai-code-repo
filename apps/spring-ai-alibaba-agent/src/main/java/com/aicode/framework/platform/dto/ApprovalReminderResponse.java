package com.aicode.framework.platform.dto;

import com.aicode.framework.platform.application.ApprovalReminderView;

import java.time.Instant;
import java.util.List;

/**
 * 审批待办超时提醒响应（Week 27）。
 *
 * <p>只返回决策所需的最小标识，不返回报告正文：报告仍按 {@code executionId}
 * 走既有执行记录接口取。</p>
 *
 * @param thresholdHours 生效的超时阈值（小时）
 * @param total          超时待办条数
 * @param items          超时待办列表（最久的在前）
 */
public record ApprovalReminderResponse(
        int thresholdHours,
        int total,
        List<Item> items
) {

    /**
     * 由用例视图转换。
     *
     * @param view 视图
     * @return 响应体
     */
    public static ApprovalReminderResponse from(ApprovalReminderView view) {
        List<Item> items = view.items().stream().map(Item::from).toList();
        return new ApprovalReminderResponse(view.thresholdHours(), items.size(), items);
    }

    /**
     * 一条超时待办。
     *
     * @param approvalId   审批任务 ID
     * @param executionId  关联执行记录
     * @param agentKey     Agent 标识
     * @param patientId    患者编号
     * @param riskLevel    风险等级
     * @param createdAt    待办创建时间
     * @param overdueHours 已超时小时数
     */
    public record Item(
            String approvalId,
            String executionId,
            String agentKey,
            String patientId,
            String riskLevel,
            Instant createdAt,
            long overdueHours
    ) {

        /**
         * 由用例条目转换。
         *
         * @param item 用例条目
         * @return 响应条目
         */
        public static Item from(ApprovalReminderView.Item item) {
            return new Item(
                    item.approval().approvalId(), item.approval().executionId(),
                    item.approval().agentKey(), item.approval().patientId(),
                    item.approval().riskLevel(), item.approval().createdAt(),
                    item.overdueHours());
        }
    }
}
