package com.aicode.framework.platform.domain.port;

import com.aicode.framework.platform.domain.model.PlatformRole;
import com.aicode.framework.platform.domain.model.PlatformTenant;

import java.util.List;
import java.util.Optional;

/**
 * 平台角色持久化端口（Week 23 起按租户隔离）。
 */
public interface PlatformRolePort {

    /**
     * 按角色键查 default 租户（Week 13–21 兼容入口）。
     *
     * @param roleKey 角色键
     * @return 角色，不存在为空
     */
    default Optional<PlatformRole> findByKey(String roleKey) {
        return findByKey(PlatformTenant.DEFAULT_TENANT_KEY, roleKey);
    }

    /**
     * 按租户 + 角色键查询。
     *
     * @param tenantKey 租户键
     * @param roleKey   角色键
     * @return 角色，不存在为空
     */
    Optional<PlatformRole> findByKey(String tenantKey, String roleKey);

    /**
     * 列出全部租户的角色（管理员总览）。
     *
     * @return 角色列表
     */
    List<PlatformRole> listAll();

    /**
     * 列出某租户的角色。
     *
     * @param tenantKey 租户键
     * @return 角色列表
     */
    default List<PlatformRole> listByTenant(String tenantKey) {
        if (tenantKey == null || tenantKey.isBlank()) {
            return List.of();
        }
        return listAll().stream()
                .filter(role -> tenantKey.trim().equals(role.tenantKey()))
                .toList();
    }

    /**
     * 保存（覆盖）角色；按 {@code (tenantKey, roleKey)} 定位。
     *
     * @param role 角色
     */
    void save(PlatformRole role);
}
