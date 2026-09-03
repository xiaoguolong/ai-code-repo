package com.aicode.demo.infrastructure.persistence.entity;

import cn.org.atool.fluent.mybatis.annotation.FluentMybatis;
import cn.org.atool.fluent.mybatis.annotation.TableField;
import cn.org.atool.fluent.mybatis.annotation.TableId;
import cn.org.atool.fluent.mybatis.base.RichEntity;

import java.time.Instant;

/**
 * 文档元数据实体。正文与向量存于向量库，本表只记文档标识与名称。
 */
@FluentMybatis(table = "document")
public class DocumentEntity extends RichEntity {

    @TableId("id")
    private Long id;

    @TableField("document_id")
    private String documentId;

    @TableField("name")
    private String name;

    @TableField("created_at")
    private Instant createdAt;

    public DocumentEntity() {
    }

    public DocumentEntity(String documentId, String name, Instant createdAt) {
        this.documentId = documentId;
        this.name = name;
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

    public String getName() {
        return name;
    }

    public void setName(String name) {
        this.name = name;
    }

    public Instant getCreatedAt() {
        return createdAt;
    }

    public void setCreatedAt(Instant createdAt) {
        this.createdAt = createdAt;
    }
}
