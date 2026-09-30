package com.aicode.framework.platform.application;

import com.aicode.framework.platform.domain.exception.PlatformNotFoundException;
import com.aicode.framework.platform.domain.model.PlatformRole;
import com.aicode.framework.platform.domain.port.PlatformRolePort;
import org.springframework.stereotype.Service;

import java.util.List;

/**
 * 平台角色查询用例。
 */
@Service
public class PlatformRoleUseCase {

    private final PlatformRolePort platformRolePort;

    public PlatformRoleUseCase(PlatformRolePort platformRolePort) {
        this.platformRolePort = platformRolePort;
    }

    /** 列出全部角色。 */
    public List<PlatformRole> listRoles() {
        return platformRolePort.listAll();
    }

    /** 查询角色。 */
    public PlatformRole getRole(String roleKey) {
        return platformRolePort.findByKey(roleKey)
                .orElseThrow(() -> new PlatformNotFoundException("role not found: " + roleKey));
    }
}
