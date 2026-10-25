package com.aicode.framework.platform.dto;

import com.aicode.framework.platform.domain.model.PlatformRole;

import java.util.List;

/**
 * 平台角色响应。
 *
 * @param tenantKey         归属租户（Week 23）
 * @param roleKey           角色键
 * @param displayName       展示名
 * @param admin             是否管理员
 * @param allowedAgentKeys  允许的 Agent
 * @param allowedToolKeys   允许的 Tool
 * @param allowedPatientIds 允许的患者
 */
public record PlatformRoleResponse(
        String tenantKey,
        String roleKey,
        String displayName,
        boolean admin,
        List<String> allowedAgentKeys,
        List<String> allowedToolKeys,
        List<String> allowedPatientIds
) {

    /**
     * 由领域模型转换。
     *
     * @param role 角色
     * @return 响应体
     */
    public static PlatformRoleResponse from(PlatformRole role) {
        return new PlatformRoleResponse(
                role.tenantKey(),
                role.roleKey(),
                role.displayName(),
                role.admin(),
                List.copyOf(role.allowedAgentKeys()),
                List.copyOf(role.allowedToolKeys()),
                List.copyOf(role.allowedPatientIds()));
    }
}
