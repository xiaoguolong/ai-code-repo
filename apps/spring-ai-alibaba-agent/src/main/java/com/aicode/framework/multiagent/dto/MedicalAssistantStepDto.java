package com.aicode.framework.multiagent.dto;

/**
 * 多 Agent 单步轨迹 DTO。
 */
public record MedicalAssistantStepDto(int stepNo, String agentName, String summary) {
}
