package com.aicode.enterprise.infrastructure.persistence.entity;

import cn.org.atool.fluent.mybatis.annotation.FluentMybatis;
import cn.org.atool.fluent.mybatis.annotation.TableField;
import cn.org.atool.fluent.mybatis.annotation.TableId;
import cn.org.atool.fluent.mybatis.base.RichEntity;

import java.time.Instant;

/**
 * 知识库实体。归属某个用户，多租户隔离基本单位。
 */
@FluentMybatis(table = "knowledge_base")
public class KnowledgeBaseEntity extends RichEntity {

    /** 主键，数据库自增。 */
    @TableId("id")
    private Long id;

    /** 归属用户主键。 */
    @TableField("user_id")
    private Long userId;

    /** 知识库名称。 */
    @TableField("name")
    private String name;

    /** 知识库描述，可空。 */
    @TableField("description")
    private String description;

    /** 创建时间。 */
    @TableField("created_at")
    private Instant createdAt;

    public KnowledgeBaseEntity() {
    }

    public KnowledgeBaseEntity(Long userId, String name, String description, Instant createdAt) {
        this.userId = userId;
        this.name = name;
        this.description = description;
        this.createdAt = createdAt;
    }

    public Long getId() {
        return id;
    }

    public void setId(Long id) {
        this.id = id;
    }

    public Long getUserId() {
        return userId;
    }

    public void setUserId(Long userId) {
        this.userId = userId;
    }

    public String getName() {
        return name;
    }

    public void setName(String name) {
        this.name = name;
    }

    public String getDescription() {
        return description;
    }

    public void setDescription(String description) {
        this.description = description;
    }

    public Instant getCreatedAt() {
        return createdAt;
    }

    public void setCreatedAt(Instant createdAt) {
        this.createdAt = createdAt;
    }
}
