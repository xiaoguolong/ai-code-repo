package com.aicode.demo.domain.model;

/**
 * 一次聊天调用的审计结果。失败也必须落日志，否则无法统计故障成本。
 */
public enum AuditStatus {

    /** 模型调用成功并返回内容。 */
    SUCCESS,

    /** 模型调用失败（超时、HTTP 错误、密钥缺失等）。 */
    FAILED
}
