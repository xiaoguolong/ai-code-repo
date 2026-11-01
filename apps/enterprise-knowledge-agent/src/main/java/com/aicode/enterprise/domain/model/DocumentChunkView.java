package com.aicode.enterprise.domain.model;

/**
 * 文档切片视图（Week 32 增补）：用于知识库页面查看分块内容。
 *
 * @param chunkIndex 切片序号（从 0 开始）
 * @param content    切片正文
 */
public record DocumentChunkView(int chunkIndex, String content) {
}
