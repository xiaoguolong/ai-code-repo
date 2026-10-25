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
    EXECUTION_READ,
    /** 高风险患者分析生成人工审批任务（Week 23） */
    APPROVAL_SUBMIT,
    /** 人工批准/拒绝审批任务（Week 23） */
    APPROVAL_DECIDE,
    /** 删除长期记忆（Week 23） */
    MEMORY_DELETE,
    /** 清理过期长期记忆（Week 23，仅手工触发） */
    MEMORY_PURGE,
    /** 重建长期记忆向量（Week 23） */
    MEMORY_REBUILD,
    /** 生成随访任务（Week 24，Agent 运行或审批通过） */
    FOLLOWUP_TASK_CREATE,
    /** 完成随访任务（Week 24） */
    FOLLOWUP_TASK_COMPLETE,
    /** 取消随访任务（Week 24） */
    FOLLOWUP_TASK_CANCEL,
    /** 手动对账知识库绑定（Week 27，仅 admin） */
    KNOWLEDGE_RECONCILE,
    /** 审批待办超时提醒（Week 27，系统调度） */
    APPROVAL_REMIND
}
