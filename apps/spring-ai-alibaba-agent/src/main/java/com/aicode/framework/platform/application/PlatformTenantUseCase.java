package com.aicode.framework.platform.application;

import com.aicode.framework.platform.domain.exception.PlatformAccessDeniedException;
import com.aicode.framework.platform.domain.exception.PlatformNotFoundException;
import com.aicode.framework.platform.domain.model.PlatformTenant;
import com.aicode.framework.platform.domain.port.PlatformTenantPort;
import com.aicode.framework.platform.domain.service.PlatformPermissionChecker;
import org.springframework.stereotype.Service;

import java.util.List;

/**
 * 租户用例（Week 21）：列表仅供管理员，单查供内部（登录/展示）使用。
 */
@Service
public class PlatformTenantUseCase {

    private final PlatformTenantPort tenantPort;
    private final PlatformPermissionChecker permissionChecker;

    public PlatformTenantUseCase(PlatformTenantPort tenantPort, PlatformPermissionChecker permissionChecker) {
        this.tenantPort = tenantPort;
        this.permissionChecker = permissionChecker;
    }

    /**
     * 列出全部租户。
     *
     * @param userId 调用者用户 ID
     * @return 租户列表（按租户键升序）
     * @throws PlatformAccessDeniedException 非管理员
     */
    public List<PlatformTenant> listTenants(long userId) {
        if (!permissionChecker.isAdmin(userId)) {
            throw new PlatformAccessDeniedException("tenant list requires admin");
        }
        return tenantPort.listAll();
    }

    /**
     * 按租户键查询。
     *
     * @param tenantKey 租户键
     * @return 租户
     * @throws PlatformNotFoundException 租户不存在
     */
    public PlatformTenant getTenant(String tenantKey) {
        String key = tenantKey == null ? "" : tenantKey.trim();
        return tenantPort.findByKey(key)
                .orElseThrow(() -> new PlatformNotFoundException("tenant not found: " + key));
    }
}
