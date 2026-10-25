package com.aicode.framework.followup.domain.model;

/**
 * 随访计划生成的一步轨迹（Week 24）。
 *
 * @param stepNo  序号（从 1 开始）
 * @param agent   步骤名
 * @param summary 摘要（不含患者隐私正文）
 */
public record FollowUpStep(int stepNo, String agent, String summary) {
}
