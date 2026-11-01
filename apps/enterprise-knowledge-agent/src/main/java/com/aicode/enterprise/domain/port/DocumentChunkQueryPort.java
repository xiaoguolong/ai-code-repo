package com.aicode.enterprise.domain.port;

import com.aicode.enterprise.domain.model.DocumentChunkView;

import java.util.List;

/**
 * 切片查询端口（Week 32 增补）：按文档读取分块正文，供知识库页面展示。
 */
public interface DocumentChunkQueryPort {

    /**
     * @param documentId 文档业务 ID
     * @return 按 chunk_index 升序的切片；不存在返回空列表
     */
    List<DocumentChunkView> listByDocument(String documentId);
}
