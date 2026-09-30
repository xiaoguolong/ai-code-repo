package com.aicode.framework.platform.infrastructure.security;

import com.aicode.core.domain.exception.ToolExecutionException;
import com.aicode.core.domain.model.ToolCall;
import com.aicode.core.domain.model.ToolDefinition;
import com.aicode.core.domain.model.ToolResult;
import com.aicode.core.domain.port.ToolPort;
import com.aicode.framework.platform.domain.exception.PlatformAccessDeniedException;
import com.aicode.framework.platform.domain.model.PlatformRole;
import com.aicode.framework.platform.domain.model.PlatformUser;
import com.aicode.framework.platform.domain.service.PlatformPermissionChecker;
import com.aicode.framework.platform.infrastructure.config.PlatformSecurityProperties;
import com.aicode.framework.platform.infrastructure.persistence.InMemoryPlatformRoleAdapter;
import com.aicode.framework.platform.infrastructure.persistence.InMemoryPlatformUserAdapter;
import com.fasterxml.jackson.databind.ObjectMapper;
import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;

import java.util.List;
import java.util.Map;
import java.util.Set;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

/** AuthorizingToolPort 装饰器测试。 */
class AuthorizingToolPortTest {

    private InMemoryPlatformUserAdapter users;
    private InMemoryPlatformRoleAdapter roles;
    private PlatformPermissionChecker checker;
    private ToolPort delegate;
    private AuthorizingToolPort port;

    @BeforeEach
    void setUp() {
        users = new InMemoryPlatformUserAdapter();
        roles = new InMemoryPlatformRoleAdapter();
        checker = new PlatformPermissionChecker(users, roles);
        delegate = new ToolPort() {
            @Override
            public List<ToolDefinition> definitions() {
                return List.of();
            }

            @Override
            public ToolResult execute(ToolCall call) {
                return new ToolResult("ok");
            }
        };
        port = new AuthorizingToolPort(
                delegate, checker, new PlatformSecurityProperties(false), new ObjectMapper());

        roles.save(new PlatformRole("viewer", "Viewer", false, Set.of(), Set.of(), Set.of()));
        users.save(new PlatformUser(3L, "viewer", "hash", "viewer"));
    }

    @AfterEach
    void tearDown() {
        PlatformSecurityContext.clear();
    }

    @Test
    void allowsDirectRunWhenSoftGateOffEvenIfViewerWouldDenyTool() {
        // 模拟 incidental 登录态：软 gate 关闭时不应校验 Tool（Week 8–11 直连兼容）
        assertThat(port.execute(new ToolCall("id1", "PatientLookupTool", "{\"patientId\":\"P001\"}")))
                .extracting(ToolResult::output)
                .isEqualTo("ok");
    }

    @Test
    void enforcesToolPermissionOnPlatformRunContext() {
        PlatformSecurityContext.beginPlatformRun(3L);
        assertThatThrownBy(() -> port.execute(new ToolCall("id1", "PatientLookupTool", "{}")))
                .isInstanceOf(PlatformAccessDeniedException.class);
    }

    @Test
    void deniesToolWhenCheckerWouldBlockInPlatformContext() {
        roles.save(new PlatformRole("operator", "Operator", false,
                Set.of(), Set.of("PatientLookupTool"), Set.of("P001")));
        users.save(new PlatformUser(2L, "operator", "hash", "operator"));
        PlatformSecurityContext.beginPlatformRun(2L);
        assertThatThrownBy(() -> port.execute(new ToolCall("id1", "HealthMetricTool", "{}")))
                .isInstanceOf(PlatformAccessDeniedException.class);
    }
}
