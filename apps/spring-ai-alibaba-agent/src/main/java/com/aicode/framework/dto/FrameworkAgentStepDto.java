package com.aicode.framework.dto;

/**
 * Graph Agent 单步轨迹响应体：一次工具调用及其观察结果。
 */
public record FrameworkAgentStepDto(int stepNo, String toolName, String arguments, String observation) {
}
