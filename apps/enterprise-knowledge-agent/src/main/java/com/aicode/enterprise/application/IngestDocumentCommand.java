package com.aicode.enterprise.application;

/**
 * 文档入库用例入参。
 *
 * @param knowledgeBaseId 归属知识库
 * @param userId          归属用户
 * @param name            文档名称
 * @param content         文档正文（纯文本）
 */
public record IngestDocumentCommand(Long knowledgeBaseId, Long userId, String name, String content) {
}
