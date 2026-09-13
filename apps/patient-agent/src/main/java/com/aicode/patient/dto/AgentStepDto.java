package com.aicode.patient.dto;

/**
 * Agent 单步轨迹响应体。
 */
public record AgentStepDto(int stepNo, String thought, String action, String observation) {
}
