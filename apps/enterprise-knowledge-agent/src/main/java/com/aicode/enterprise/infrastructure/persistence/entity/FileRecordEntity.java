package com.aicode.enterprise.infrastructure.persistence.entity;

import cn.org.atool.fluent.mybatis.annotation.FluentMybatis;
import cn.org.atool.fluent.mybatis.annotation.TableField;
import cn.org.atool.fluent.mybatis.annotation.TableId;
import cn.org.atool.fluent.mybatis.base.RichEntity;

import java.time.Instant;

/**
 * 文件元数据实体。file_id 为对外业务键，storage_key 记录后端定位（本地相对路径 / 对象存储 key）。
 */
@FluentMybatis(table = "file_record")
public class FileRecordEntity extends RichEntity {

    /** 主键，数据库自增。 */
    @TableId("id")
    private Long id;

    /** 文件业务键，对外暴露。 */
    @TableField("file_id")
    private String fileId;

    /** 原始文件名。 */
    @TableField("original_name")
    private String originalName;

    /** 内容类型，可空。 */
    @TableField("content_type")
    private String contentType;

    /** 文件大小（字节）。 */
    @TableField("size_bytes")
    private Long sizeBytes;

    /** 存储类型：local / oss / obs。 */
    @TableField("storage_type")
    private String storageType;

    /** 存储定位：本地相对路径或对象存储 key。 */
    @TableField("storage_key")
    private String storageKey;

    /** 上传者主键。 */
    @TableField("user_id")
    private Long userId;

    /** 创建时间。 */
    @TableField("created_at")
    private Instant createdAt;

    public FileRecordEntity() {
    }

    public FileRecordEntity(
            String fileId,
            String originalName,
            String contentType,
            long sizeBytes,
            String storageType,
            String storageKey,
            Long userId,
            Instant createdAt
    ) {
        this.fileId = fileId;
        this.originalName = originalName;
        this.contentType = contentType;
        this.sizeBytes = sizeBytes;
        this.storageType = storageType;
        this.storageKey = storageKey;
        this.userId = userId;
        this.createdAt = createdAt;
    }

    public Long getId() {
        return id;
    }

    public void setId(Long id) {
        this.id = id;
    }

    public String getFileId() {
        return fileId;
    }

    public void setFileId(String fileId) {
        this.fileId = fileId;
    }

    public String getOriginalName() {
        return originalName;
    }

    public void setOriginalName(String originalName) {
        this.originalName = originalName;
    }

    public String getContentType() {
        return contentType;
    }

    public void setContentType(String contentType) {
        this.contentType = contentType;
    }

    public Long getSizeBytes() {
        return sizeBytes;
    }

    public void setSizeBytes(Long sizeBytes) {
        this.sizeBytes = sizeBytes;
    }

    public String getStorageType() {
        return storageType;
    }

    public void setStorageType(String storageType) {
        this.storageType = storageType;
    }

    public String getStorageKey() {
        return storageKey;
    }

    public void setStorageKey(String storageKey) {
        this.storageKey = storageKey;
    }

    public Long getUserId() {
        return userId;
    }

    public void setUserId(Long userId) {
        this.userId = userId;
    }

    public Instant getCreatedAt() {
        return createdAt;
    }

    public void setCreatedAt(Instant createdAt) {
        this.createdAt = createdAt;
    }
}
