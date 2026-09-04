package com.aicode.enterprise.domain.port;

import com.aicode.enterprise.domain.model.DocumentMetadata;

import java.util.List;

/**
 * 出站端口：文档元数据持久化。正文与向量由 {@link VectorStorePort} 管理，两者不耦合。
 */
public interface DocumentPort {

    /**
     * 创建一条文档元数据。documentId 由用例层生成，需先于切片入库。
     */
    void create(DocumentMetadata metadata);

    /**
     * 列出某知识库下的全部文档元数据。
     */
    List<DocumentMetadata> listByKnowledgeBase(long knowledgeBaseId);

    /**
     * 按业务键删除文档元数据。
     */
    void deleteByDocumentId(String documentId);
}
