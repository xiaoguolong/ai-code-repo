package com.aicode.framework.platform.application;

import com.aicode.framework.platform.domain.exception.PlatformNotFoundException;
import com.aicode.framework.platform.domain.model.PlatformRole;
import com.aicode.framework.platform.domain.model.PlatformTenant;
import com.aicode.framework.platform.domain.port.PlatformRolePort;
import org.springframework.stereotype.Service;

import java.util.List;

/**
 * 平台角色查询用例（Week 23 起支持按租户查询）。
 */
@Service
public class PlatformRoleUseCase {

    private final PlatformRolePort platformRolePort;

    public PlatformRoleUseCase(PlatformRolePort platformRolePort) {
        this.platformRolePort = platformRolePort;
    }

    /** 列出全部角色（管理员总览）。 */
    public List<PlatformRole> listRoles() {
        return listRoles(null);
    }

    /**
     * 列出角色；tenantKey 为空时列全部。
     *
     * @param tenantKey 租户键，可空
     * @return 角色列表
     */
    public List<PlatformRole> listRoles(String tenantKey) {
        if (tenantKey == null || tenantKey.isBlank()) {
            return platformRolePort.listAll();
        }
        return platformRolePort.listByTenant(tenantKey.trim());
    }

    /** 查询 default 租户的角色（兼容入口）。 */
    public PlatformRole getRole(String roleKey) {
        return getRole(roleKey, PlatformTenant.DEFAULT_TENANT_KEY);
    }

    /**
     * 查询角色。
     *
     * @param roleKey   角色键
     * @param tenantKey 租户键，空则 default
     * @return 角色
     * @throws PlatformNotFoundException 不存在
     */
    public PlatformRole getRole(String roleKey, String tenantKey) {
        String normalizedTenant = (tenantKey == null || tenantKey.isBlank())
                ? PlatformTenant.DEFAULT_TENANT_KEY : tenantKey.trim();
        return platformRolePort.findByKey(normalizedTenant, roleKey)
                .orElseThrow(() -> new PlatformNotFoundException(
                        "role not found: " + normalizedTenant + "/" + roleKey));
    }
}
