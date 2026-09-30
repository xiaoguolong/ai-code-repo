package com.aicode.framework.platform.domain.model;

import java.util.Set;

/**
 * 平台角色及其 Agent / Tool / 数据域授权。
 */
public record PlatformRole(
        String roleKey,
        String displayName,
        boolean admin,
        Set<String> allowedAgentKeys,
        Set<String> allowedToolKeys,
        Set<String> allowedPatientIds
) {

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
