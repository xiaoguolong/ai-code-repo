package com.aicode.demo.domain.port;

import com.aicode.demo.domain.model.DocumentMetadata;

import java.util.List;

/**
 * 出站端口：文档元数据持久化。正文与向量由 {@link VectorStorePort} 管理，两者不耦合。
 */
public interface DocumentPort {

    /**
     * 创建一条文档元数据。documentId 由用例层生成，需先于切片入库。
     *
     * @param metadata 文档元数据
     */
    void create(DocumentMetadata metadata);

    /**
     * 列出全部文档元数据。无文档时返回空列表。
     */
    List<DocumentMetadata> list();
}
