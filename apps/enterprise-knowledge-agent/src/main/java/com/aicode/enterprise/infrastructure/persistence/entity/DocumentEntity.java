package com.aicode.enterprise.infrastructure.persistence.entity;

import cn.org.atool.fluent.mybatis.annotation.FluentMybatis;
import cn.org.atool.fluent.mybatis.annotation.TableField;
import cn.org.atool.fluent.mybatis.annotation.TableId;
import cn.org.atool.fluent.mybatis.base.RichEntity;

import java.time.Instant;

/**
 * 文档元数据实体。正文与向量存于向量库，本表只记文档标识、归属知识库与名称。
 */
@FluentMybatis(table = "document")
public class DocumentEntity extends RichEntity {

    /** 主键，数据库自增。 */
    @TableId("id")
    private Long id;

    /** 文档业务键，对外暴露。 */
    @TableField("document_id")
    private String documentId;

    /** 归属知识库主键。 */
    @TableField("knowledge_base_id")
    private Long knowledgeBaseId;

    /** 文档名称。 */
    @TableField("name")
    private String name;

    /** 切片数量。 */
    @TableField("chunk_count")
    private Integer chunkCount;

    /** 创建时间。 */
    @TableField("created_at")
    private Instant createdAt;

    public DocumentEntity() {
    }

    public DocumentEntity(String documentId, Long knowledgeBaseId, String name, int chunkCount, Instant createdAt) {
        this.documentId = documentId;
        this.knowledgeBaseId = knowledgeBaseId;
        this.name = name;
        this.chunkCount = chunkCount;
        this.createdAt = createdAt;
    }

    public Long getId() {
        return id;
    }

    public void setId(Long id) {
        this.id = id;
    }

    public String getDocumentId() {
        return documentId;
    }

    public void setDocumentId(String documentId) {
        this.documentId = documentId;
    }

    public Long getKnowledgeBaseId() {
        return knowledgeBaseId;
    }

    public void setKnowledgeBaseId(Long knowledgeBaseId) {
        this.knowledgeBaseId = knowledgeBaseId;
    }

    public String getName() {
        return name;
    }

    public void setName(String name) {
        this.name = name;
    }

    public Integer getChunkCount() {
        return chunkCount;
    }

    public void setChunkCount(Integer chunkCount) {
        this.chunkCount = chunkCount;
    }

    public Instant getCreatedAt() {
        return createdAt;
    }

    public void setCreatedAt(Instant createdAt) {
        this.createdAt = createdAt;
    }
}
