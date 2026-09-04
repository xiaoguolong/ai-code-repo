package com.aicode.enterprise.infrastructure.persistence.entity;

import cn.org.atool.fluent.mybatis.annotation.FluentMybatis;
import cn.org.atool.fluent.mybatis.annotation.TableField;
import cn.org.atool.fluent.mybatis.annotation.TableId;
import cn.org.atool.fluent.mybatis.base.RichEntity;

import java.time.Instant;

/**
 * 用户实体。password_hash 为加盐哈希，禁止存明文。
 */
@FluentMybatis(table = "app_user")
public class AppUserEntity extends RichEntity {

    /** 主键，数据库自增。 */
    @TableId("id")
    private Long id;

    /** 用户名，唯一。 */
    @TableField("username")
    private String username;

    /** 密码哈希（加盐），禁止存明文。 */
    @TableField("password_hash")
    private String passwordHash;

    /** 创建时间。 */
    @TableField("created_at")
    private Instant createdAt;

    public AppUserEntity() {
    }

    public AppUserEntity(String username, String passwordHash, Instant createdAt) {
        this.username = username;
        this.passwordHash = passwordHash;
        this.createdAt = createdAt;
    }

    public Long getId() {
        return id;
    }

    public void setId(Long id) {
        this.id = id;
    }

    public String getUsername() {
        return username;
    }

    public void setUsername(String username) {
        this.username = username;
    }

    public String getPasswordHash() {
        return passwordHash;
    }

    public void setPasswordHash(String passwordHash) {
        this.passwordHash = passwordHash;
    }

    public Instant getCreatedAt() {
        return createdAt;
    }

    public void setCreatedAt(Instant createdAt) {
        this.createdAt = createdAt;
    }
}
