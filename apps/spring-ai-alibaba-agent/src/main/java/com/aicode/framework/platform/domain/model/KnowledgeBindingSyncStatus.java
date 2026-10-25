package com.aicode.framework.platform.domain.model;

/**
 * 知识库绑定与企业侧知识库的同步状态（Week 27）。
 *
 * <p>与 {@code enabled} 正交：{@code enabled} 是「本租户是否允许检索」，
 * 本枚举是「企业侧这个库是否还存在」。</p>
 *
 * <ul>
 *   <li>{@link #UNKNOWN}：导入后尚未对账（V9 旧行默认值）。**不阻断检索**——
 *       「还没对账」不等于「不存在」；</li>
 *   <li>{@link #ACTIVE}：最近一次成功对账在企业侧存在；</li>
 *   <li>{@link #MISSING}：最近一次成功对账企业侧已无此库。检索直接 404，
 *       但**不自动删除绑定**（历史、审计与人工决策都还在）。</li>
 * </ul>
 *
 * <p>对账失败时状态保持不变：上游一次抖动不能把线上知识库全部判死。</p>
 */
public enum KnowledgeBindingSyncStatus {

    /** 尚未对账（或 V9 迁移后的默认值）；可检索。 */
    UNKNOWN,

    /** 最近一次对账确认企业侧存在。 */
    ACTIVE,

    /** 最近一次对账确认企业侧已删除；不可检索。 */
    MISSING
}
