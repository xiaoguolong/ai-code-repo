package com.aicode.patient.domain.model;

/**
 * ReAct 循环中的单步轨迹：思考、行动、观察。第 5 周 Action 为模型内部推理，观察值为模型自述结果。
 */
public record AgentStep(int stepNo, String thought, String action, String observation) {
}
