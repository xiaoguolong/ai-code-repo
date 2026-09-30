package com.aicode.framework.platform.domain.service;

import com.aicode.framework.platform.domain.exception.PlatformAccessDeniedException;
import com.aicode.framework.platform.domain.exception.PlatformNotFoundException;
import com.aicode.framework.platform.domain.model.PlatformRole;
import com.aicode.framework.platform.domain.model.PlatformUser;
import com.aicode.framework.platform.domain.port.PlatformRolePort;
import com.aicode.framework.platform.domain.port.PlatformUserPort;
import org.springframework.stereotype.Service;

import java.util.Map;

/**
 * 平台 RBAC 校验：Agent 调度、Tool 调用、patientId 数据域、执行记录归属。
 */
@Service
public class PlatformPermissionChecker {

    static final String PATIENT_ID = "patientId";

    private final PlatformUserPort platformUserPort;
    private final PlatformRolePort platformRolePort;

    public PlatformPermissionChecker(PlatformUserPort platformUserPort, PlatformRolePort platformRolePort) {
        this.platformUserPort = platformUserPort;
        this.platformRolePort = platformRolePort;
    }

    /** 校验用户是否有权调度 Agent。 */
    public void requireAgentRun(long userId, String agentKey) {
        PlatformRole role = roleOf(userId);
        if (!role.allowsAgent(agentKey)) {
            throw new PlatformAccessDeniedException("agent access denied: " + agentKey);
        }
    }

    /** 校验 Run 输入中的 patientId 数据域（若存在）。 */
    public void requireRunInput(long userId, Map<String, Object> input) {
        String patientId = extractPatientId(input);
        if (patientId != null) {
            requirePatientAccess(userId, patientId);
        }
    }

    /** 校验 Tool 调用与参数中的 patientId。 */
    public void requireToolExecute(long userId, String toolName, Map<String, Object> arguments) {
        PlatformRole role = roleOf(userId);
        if (!role.allowsTool(toolName)) {
            throw new PlatformAccessDeniedException("tool access denied: " + toolName);
        }
        String patientId = extractPatientId(arguments);
        if (patientId != null) {
            requirePatientAccess(userId, patientId);
        }
    }

    /** 校验执行记录归属。admin 可看全部。 */
    public void requireExecutionAccess(long userId, long recordUserId) {
        if (isAdmin(userId)) {
            return;
        }
        if (userId != recordUserId) {
            throw new PlatformAccessDeniedException("execution access denied");
        }
    }

    /** 是否 admin 角色。 */
    public boolean isAdmin(long userId) {
        return roleOf(userId).admin();
    }

    private void requirePatientAccess(long userId, String patientId) {
        PlatformRole role = roleOf(userId);
        if (!role.allowsPatient(patientId)) {
            throw new PlatformAccessDeniedException("data access denied for patient: " + patientId);
        }
    }

    private PlatformRole roleOf(long userId) {
        PlatformUser user = platformUserPort.findById(userId)
                .orElseThrow(() -> new PlatformNotFoundException("user not found: " + userId));
        return platformRolePort.findByKey(user.roleKey())
                .orElseThrow(() -> new PlatformNotFoundException("role not found: " + user.roleKey()));
    }

    static String extractPatientId(Map<String, Object> map) {
        if (map == null) {
            return null;
        }
        Object value = map.get(PATIENT_ID);
        if (value == null || value.toString().isBlank()) {
            return null;
        }
        return value.toString().trim();
    }
}
