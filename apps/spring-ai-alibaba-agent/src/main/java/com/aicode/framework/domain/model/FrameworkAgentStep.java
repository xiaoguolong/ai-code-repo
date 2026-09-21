package com.aicode.framework.domain.model;

/**
 * Graph Agent 单步轨迹：一次工具调用及其观察结果。
 *
 * @param stepNo      步序，从 1 开始
 * @param toolName    工具名
 * @param arguments   模型给出的参数 JSON 原文
 * @param observation 工具返回内容
 */
public record FrameworkAgentStep(int stepNo, String toolName, String arguments, String observation) {
}
