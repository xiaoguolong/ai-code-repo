package com.aicode.framework.platform.domain.port;

import com.aicode.framework.platform.domain.model.PlatformUser;

import java.time.Instant;
import java.util.List;
import java.util.Optional;

/**
 * 平台用户持久化端口（Week 28：停用/恢复）。
 */
public interface PlatformUserPort {

    Optional<PlatformUser> findByUsername(String username);

    Optional<PlatformUser> findById(long userId);

    /**
     * 保存用户（seed / 测试）。
     *
     * <p><b>不覆盖</b> {@code disabled_at}：启动 seed 不能用一份没有停用字段的用户把停用用户复活；
     * 停用/恢复只走 {@link #updateDisabledAt}。</p>
     *
     * @param user 用户
     */
    void save(PlatformUser user);

    /**
     * 列出全部用户（仅 admin 用户管理使用）。
     *
     * @return 按 userId 升序的用户列表，无数据返回空列表
     */
    List<PlatformUser> listAll();

    /**
     * 只更新停用时间（不动用户名/密码/角色/租户）。
     *
     * @param userId     用户 ID
     * @param disabledAt 停用时间；null = 恢复
     * @return true = 更新了一条；false = 用户不存在
     */
    boolean updateDisabledAt(long userId, Instant disabledAt);
}
