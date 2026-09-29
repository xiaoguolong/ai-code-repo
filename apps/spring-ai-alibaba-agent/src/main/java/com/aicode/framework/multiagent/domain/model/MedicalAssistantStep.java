package com.aicode.framework.multiagent.domain.model;

/**
 * 多 Agent 单步轨迹：Supervisor 或 Worker 的一次执行摘要。
 *
 * @param stepNo    步序，从 1 开始
 * @param agentName 节点名（supervisor / data_agent 等）
 * @param summary   本步摘要
 */
public record MedicalAssistantStep(int stepNo, String agentName, String summary) {
}
