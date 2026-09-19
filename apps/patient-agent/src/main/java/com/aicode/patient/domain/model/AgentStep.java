package com.aicode.patient.domain.model;

/**
 * 一次工具调用轨迹。第 6 周原生 Function Calling：Agent 每一步都是「调用工具 → 得到观察结果」。
 */
public record AgentStep(int stepNo, String toolName, String arguments, String observation) {
}
