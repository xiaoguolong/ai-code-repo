package com.aicode.framework.multiagent.domain.model;

/**
 * Supervisor 条件边路由目标。
 */
public enum SupervisorRoute {

    /** 数据 Agent：查询患者与指标 */
    DATA("data_agent"),
    /** 分析 Agent：风险研判 */
    ANALYSIS("analysis_agent"),
    /** 报告 Agent：生成报告 */
    REPORT("report_agent"),
    /** 随访 Agent：生成随访计划 */
    FOLLOWUP("followup_agent"),
    /** 结束 */
    FINISH("finish");

    private final String graphRoute;

    SupervisorRoute(String graphRoute) {
        this.graphRoute = graphRoute;
    }

    /**
     * @return StateGraph 条件边映射键
     */
    public String graphRoute() {
        return graphRoute;
    }
}
