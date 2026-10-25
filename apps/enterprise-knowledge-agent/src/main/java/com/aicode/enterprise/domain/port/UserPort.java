package com.aicode.enterprise.domain.port;

import com.aicode.core.domain.model.User;

import java.util.Optional;

/**
 * 出站端口：用户持久化。实现类负责数据库协议，领域不依赖 SQL。
 */
public interface UserPort {

    /**
     * 创建用户并返回带主键的用户。
     *
     * @param user 用户，username 已校验非空
     * @return 持久化后的用户（含 id）
     */
    User create(User user);

    /**
     * 按用户名查询。
     *
     * @param username 用户名
     * @return 命中用户，不存在为空
     */
    Optional<User> findByUsername(String username);

    /**
     * 按主键查询。
     *
     * @param id 用户主键
     * @return 命中用户，不存在为空
     */
    Optional<User> findById(long id);

    /**
     * 按主键删除用户（Week 23 运维解绑用）。
     *
     * <p>只做物理删除；若用户仍被文档/会话等外键引用，实现会抛异常，调用方应转成 409，
     * 不要静默级联删业务数据。</p>
     *
     * @param id 用户主键
     * @return true = 删除了一条
     */
    boolean deleteById(long id);
}
