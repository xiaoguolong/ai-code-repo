package com.aicode.framework.platform.domain.model;

import java.util.Set;

/**
 * 平台角色及其 Agent / Tool / 数据域授权。
 *
 * <p>Week 23（B2）：角色按租户复制。{@code tenantKey} 是角色的归属租户；权限集合仍是
 * 每个租户副本各自一份（授权表也带 tenant_key），因此不同租户互不影响。
 * 6 参构造器保留为 default 租户，兼容 Week 13–21 的测试与调用方。</p>
 *
 * @param roleKey           角色键（租户内唯一）
 * @param displayName       展示名
 * @param admin             是否管理员
 * @param allowedAgentKeys  允许调度的 Agent
 * @param allowedToolKeys   允许调用的 Tool
 * @param allowedPatientIds 允许访问的患者数据域
 * @param tenantKey         归属租户
 */
public record PlatformRole(
        String roleKey,
        String displayName,
        boolean admin,
        Set<String> allowedAgentKeys,
        Set<String> allowedToolKeys,
        Set<String> allowedPatientIds,
        String tenantKey
) {

    public PlatformRole {
        tenantKey = (tenantKey == null || tenantKey.isBlank())
                ? PlatformTenant.DEFAULT_TENANT_KEY : tenantKey.trim();
    }

    /**
     * Week 13–21 兼容构造器：归属 default 租户。
     *
     * @param roleKey           角色键
     * @param displayName       展示名
     * @param admin             是否管理员
     * @param allowedAgentKeys  允许的 Agent
     * @param allowedToolKeys   允许的 Tool
     * @param allowedPatientIds 允许的患者
     */
    public PlatformRole(
            String roleKey,
            String displayName,
            boolean admin,
            Set<String> allowedAgentKeys,
            Set<String> allowedToolKeys,
            Set<String> allowedPatientIds
    ) {
        this(roleKey, displayName, admin, allowedAgentKeys, allowedToolKeys, allowedPatientIds,
                PlatformTenant.DEFAULT_TENANT_KEY);
    }

    /** 是否允许访问指定患者数据。admin 放行全部。 */
    public boolean allowsPatient(String patientId) {
        if (admin) {
            return true;
        }
        return patientId != null && allowedPatientIds.contains(patientId);
    }

    /** 是否允许调度指定 Agent。 */
    public boolean allowsAgent(String agentKey) {
        if (admin) {
            return true;
        }
        return agentKey != null && allowedAgentKeys.contains(agentKey);
    }

    /** 是否允许调用指定 Tool。 */
    public boolean allowsTool(String toolKey) {
        if (admin) {
            return true;
        }
        return toolKey != null && allowedToolKeys.contains(toolKey);
    }
}
