package com.aicode.framework.platform.domain.model;

/**
 * 审计结果。
 */
public enum AuditResult {

    /** 操作成功 */
    SUCCESS,
    /** 操作失败（业务失败或系统异常） */
    FAILURE,
    /** 鉴权拒绝（RBAC / 数据域） */
    DENIED
}
