package com.aicode.framework.followup.domain;

import com.aicode.framework.followup.domain.model.FollowUpPlan;
import com.aicode.framework.followup.domain.model.FollowUpPlanItem;
import com.aicode.framework.followup.domain.model.FollowUpPriority;
import org.junit.jupiter.api.Test;

import java.time.Instant;
import java.time.temporal.ChronoUnit;
import java.util.List;

import static org.assertj.core.api.Assertions.assertThat;

/**
 * 随访计划规则（Week 24）：三档风险的任务条数、间隔与优先级。
 */
class FollowUpPlannerTest {

    private static final Instant BASE = Instant.parse("2026-10-25T09:00:00Z");

    private final FollowUpPlanner planner = new FollowUpPlanner();

    @Test
    void highRiskGetsThreeTasksWithHighPriority() {
        FollowUpPlan plan = planner.plan("HIGH", BASE);

        assertThat(plan.riskLevel()).isEqualTo("HIGH");
        assertThat(plan.items()).hasSize(3);
        assertThat(plan.items()).extracting(FollowUpPlanItem::title)
                .containsExactly("复测血压与症状", "复查 HbA1c/空腹血糖", "门诊复诊");
        assertThat(plan.items()).extracting(FollowUpPlanItem::dueAt).containsExactly(
                BASE.plus(3, ChronoUnit.DAYS),
                BASE.plus(14, ChronoUnit.DAYS),
                BASE.plus(30, ChronoUnit.DAYS));
        assertThat(plan.items()).allSatisfy(item ->
                assertThat(item.priority()).isEqualTo(FollowUpPriority.HIGH));
    }

    @Test
    void mediumRiskGetsTwoTasks() {
        FollowUpPlan plan = planner.plan("MEDIUM", BASE);

        assertThat(plan.riskLevel()).isEqualTo("MEDIUM");
        assertThat(plan.items()).extracting(FollowUpPlanItem::dueAt).containsExactly(
                BASE.plus(7, ChronoUnit.DAYS), BASE.plus(30, ChronoUnit.DAYS));
        assertThat(plan.items()).allSatisfy(item ->
                assertThat(item.priority()).isEqualTo(FollowUpPriority.MEDIUM));
    }

    @Test
    void lowRiskGetsOneTask() {
        FollowUpPlan plan = planner.plan("LOW", BASE);

        assertThat(plan.size()).isEqualTo(1);
        assertThat(plan.items().get(0).priority()).isEqualTo(FollowUpPriority.LOW);
        assertThat(plan.items().get(0).dueAt()).isEqualTo(BASE.plus(30, ChronoUnit.DAYS));
    }

    @Test
    void unknownOrBlankRiskFallsBackToLow() {
        assertThat(planner.plan(null, BASE).riskLevel()).isEqualTo("LOW");
        assertThat(planner.plan("  ", BASE).riskLevel()).isEqualTo("LOW");
        assertThat(planner.plan("nope", BASE).riskLevel()).isEqualTo("LOW");
    }

    @Test
    void riskLevelIsCaseInsensitive() {
        assertThat(planner.plan("high", BASE).size()).isEqualTo(3);
    }

    @Test
    void tasksAreOrderedByDueDate() {
        List<FollowUpPlanItem> items = planner.plan("HIGH", BASE).items();

        assertThat(items).isSortedAccordingTo(
                (left, right) -> left.dueAt().compareTo(right.dueAt()));
    }
}
