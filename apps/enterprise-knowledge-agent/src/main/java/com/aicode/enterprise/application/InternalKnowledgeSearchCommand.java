package com.aicode.enterprise.application;

/**
 * 内部知识库检索命令（Week 25）。
 *
 * <p>以 {@code enterpriseUserId} 而不是平台用户 ID 执行：知识库归属是 enterprise 本地用户的，
 * 平台侧在**绑定时**记录了该本地用户 ID，检索时直接按它做归属校验，避免每次调用都查 SSO 映射。</p>
 *
 * @param enterpriseUserId 知识库归属的本地用户 ID
 * @param knowledgeBaseId  知识库 ID
 * @param query            检索问题
 * @param topK             返回条数（可空，用默认值）
 */
public record InternalKnowledgeSearchCommand(
        long enterpriseUserId,
        long knowledgeBaseId,
        String query,
        Integer topK
) {
}
