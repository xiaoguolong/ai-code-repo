package com.aicode.framework.followup.domain;

import com.aicode.framework.followup.domain.model.FollowUpPlan;
import com.aicode.framework.followup.domain.model.FollowUpPlanItem;
import com.aicode.framework.followup.domain.model.FollowUpPriority;

import java.time.Instant;
import java.time.temporal.ChronoUnit;
import java.util.List;
import java.util.Locale;

/**
 * 随访计划规则（Week 24）。
 *
 * <p><b>临床时间点由规则决定，模型不参与</b>：模型只把本计划写成患者/医生能读懂的说明文本。
 * 这样「几号复测」不会因为模型幻觉漂移，也便于单测与后续替换成真实指南。</p>
 *
 * <p>下表是**演示规则**，不是临床指南：</p>
 * <table border="1">
 *   <caption>风险 → 计划</caption>
 *   <tr><th>风险</th><th>任务</th></tr>
 *   <tr><td>HIGH</td><td>+3 天复测血压、+14 天复查 HbA1c、+30 天门诊复诊（全 HIGH）</td></tr>
 *   <tr><td>MEDIUM</td><td>+7 天复测血压、+30 天门诊复诊（MEDIUM）</td></tr>
 *   <tr><td>LOW</td><td>+30 天常规复查（LOW）</td></tr>
 * </table>
 */
public class FollowUpPlanner {

    /**
     * 生成随访计划。
     *
     * @param riskLevel 风险等级（大小写不敏感，未知值按 LOW 处理）
     * @param base      计划基准时间（通常是执行时间）
     * @return 按到期时间升序的计划
     */
    public FollowUpPlan plan(String riskLevel, Instant base) {
        String normalized = riskLevel == null ? "LOW" : riskLevel.trim().toUpperCase(Locale.ROOT);
        Instant from = base == null ? Instant.now() : base;
        return switch (normalized) {
            case "HIGH" -> new FollowUpPlan("HIGH", List.of(
                    item("复测血压与症状", "3 天后复测血压并记录症状变化", FollowUpPriority.HIGH,
                            from.plus(3, ChronoUnit.DAYS)),
                    item("复查 HbA1c/空腹血糖", "14 天后复查糖化血红蛋白与空腹血糖", FollowUpPriority.HIGH,
                            from.plus(14, ChronoUnit.DAYS)),
                    item("门诊复诊", "30 天后门诊复诊，评估用药与随访计划", FollowUpPriority.HIGH,
                            from.plus(30, ChronoUnit.DAYS))));
            case "MEDIUM" -> new FollowUpPlan("MEDIUM", List.of(
                    item("复测血压", "7 天后复测血压", FollowUpPriority.MEDIUM,
                            from.plus(7, ChronoUnit.DAYS)),
                    item("门诊复诊", "30 天后门诊复诊", FollowUpPriority.MEDIUM,
                            from.plus(30, ChronoUnit.DAYS))));
            default -> new FollowUpPlan("LOW", List.of(
                    item("常规复查", "30 天后常规复查", FollowUpPriority.LOW,
                            from.plus(30, ChronoUnit.DAYS))));
        };
    }

    private FollowUpPlanItem item(
            String title, String detail, FollowUpPriority priority, Instant dueAt) {
        return new FollowUpPlanItem(title, detail, priority, dueAt);
    }
}
