package com.aicode.framework.platform.domain.model;

/**
 * 审计动作。新增动作必须同步更新 {@code docs/api/week-16-api.md} 的审计事件表。
 */
public enum AuditAction {

    /** 登录成功 */
    LOGIN,
    /** 登录失败（用户名不存在或密码错误） */
    LOGIN_FAILED,
    /** 平台调度 Agent 执行（终态） */
    AGENT_RUN,
    /** 读取执行记录（含越权被拒） */
    EXECUTION_READ
}
