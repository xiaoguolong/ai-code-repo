package com.aicode.framework.platform.dto;

import com.aicode.framework.platform.domain.model.PlatformRole;

import java.util.List;

/**
 * 平台角色响应。
 */
public record PlatformRoleResponse(
        String roleKey,
        String displayName,
        boolean admin,
        List<String> allowedAgentKeys,
        List<String> allowedToolKeys,
        List<String> allowedPatientIds
) {

    public static PlatformRoleResponse from(PlatformRole role) {
        return new PlatformRoleResponse(
                role.roleKey(),
                role.displayName(),
                role.admin(),
                List.copyOf(role.allowedAgentKeys()),
                List.copyOf(role.allowedToolKeys()),
                List.copyOf(role.allowedPatientIds()));
    }
}
