package com.aicode.enterprise.application;

/**
 * 内部检索命中（Week 25）。
 *
 * <p>只返回组装答案所需的字段：{@code content} 是切片正文（已由平台侧 Guardrail 在输出阶段脱敏），
 * {@code documentName} 用于引用展示，避免平台再回查企业库。</p>
 *
 * @param documentId   文档业务 ID
 * @param documentName 文档名
 * @param chunkIndex   切片序号
 * @param content      切片正文
 * @param score        余弦相似度
 */
public record InternalKnowledgeHit(
        String documentId,
        String documentName,
        int chunkIndex,
        String content,
        double score
) {
}
