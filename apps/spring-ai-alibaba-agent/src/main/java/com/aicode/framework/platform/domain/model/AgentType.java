package com.aicode.framework.platform.domain.model;

/**
 * 平台注册的 Agent 运行时类型。
 */
public enum AgentType {

    /** Week 8 ReAct Graph Agent */
    FRAMEWORK_REACT,
    /** Week 9-10 患者风险 Workflow */
    PATIENT_RISK_WORKFLOW,
    /** Week 11 Supervisor 多 Agent */
    MEDICAL_ASSISTANT,
    /** Week 21 Agent 1：患者分析（查患者/报告/指标 + 规则风险 + 报告 + 记忆） */
    PATIENT_ANALYSIS,
    /** Week 24 Agent 2：运营分析（窗口统计 + 规则异常 + 日报） */
    OPERATIONS_ANALYSIS,
    /** Week 24 Agent 4：随访（规则计划 + 任务落库 + 站内提醒） */
    FOLLOW_UP,
    /** Week 25 Agent 3：知识库问答（检索 enterprise 知识库 + 平台生成带引用的答案） */
    KNOWLEDGE_QA
}
