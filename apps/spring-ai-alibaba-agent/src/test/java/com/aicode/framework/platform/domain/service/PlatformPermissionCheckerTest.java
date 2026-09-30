package com.aicode.framework.platform.domain.service;

import com.aicode.framework.platform.domain.exception.PlatformAccessDeniedException;
import com.aicode.framework.platform.domain.model.PlatformRole;
import com.aicode.framework.platform.domain.model.PlatformUser;
import com.aicode.framework.platform.infrastructure.persistence.InMemoryPlatformRoleAdapter;
import com.aicode.framework.platform.infrastructure.persistence.InMemoryPlatformUserAdapter;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;

import java.util.Map;
import java.util.Set;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatCode;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

/** 平台 RBAC 校验测试。 */
class PlatformPermissionCheckerTest {

    private InMemoryPlatformUserAdapter users;
    private InMemoryPlatformRoleAdapter roles;
    private PlatformPermissionChecker checker;

    @BeforeEach
    void setUp() {
        users = new InMemoryPlatformUserAdapter();
        roles = new InMemoryPlatformRoleAdapter();
        checker = new PlatformPermissionChecker(users, roles);

        roles.save(new PlatformRole("admin", "Admin", true, Set.of(), Set.of(), Set.of()));
        roles.save(new PlatformRole("operator", "Operator", false,
                Set.of("patient-risk"), Set.of("PatientLookupTool"), Set.of("P001")));
        roles.save(new PlatformRole("viewer", "Viewer", false, Set.of(), Set.of(), Set.of()));

        users.save(new PlatformUser(1L, "admin", "hash", "admin"));
        users.save(new PlatformUser(2L, "operator", "hash", "operator"));
        users.save(new PlatformUser(3L, "viewer", "hash", "viewer"));
    }

    @Test
    void adminAllowsAnyAgentAndPatient() {
        assertThatCode(() -> checker.requireAgentRun(1L, "framework-react")).doesNotThrowAnyException();
        assertThatCode(() -> checker.requireRunInput(1L, Map.of("patientId", "P999"))).doesNotThrowAnyException();
        assertThat(checker.isAdmin(1L)).isTrue();
    }

    @Test
    void operatorAllowsGrantedAgentAndPatient() {
        assertThatCode(() -> checker.requireAgentRun(2L, "patient-risk")).doesNotThrowAnyException();
        assertThatCode(() -> checker.requireToolExecute(2L, "PatientLookupTool", Map.of("patientId", "P001")))
                .doesNotThrowAnyException();
    }

    @Test
    void operatorDeniesUnknownAgent() {
        assertThatThrownBy(() -> checker.requireAgentRun(2L, "framework-react"))
                .isInstanceOf(PlatformAccessDeniedException.class);
    }

    @Test
    void operatorDeniesOutOfScopePatient() {
        assertThatThrownBy(() -> checker.requireRunInput(2L, Map.of("patientId", "P999")))
                .isInstanceOf(PlatformAccessDeniedException.class);
    }

    @Test
    void viewerDeniesAgentRun() {
        assertThatThrownBy(() -> checker.requireAgentRun(3L, "patient-risk"))
                .isInstanceOf(PlatformAccessDeniedException.class);
    }

    @Test
    void requireExecutionAccessAllowsOwnerOrAdmin() {
        assertThatCode(() -> checker.requireExecutionAccess(2L, 2L)).doesNotThrowAnyException();
        assertThatCode(() -> checker.requireExecutionAccess(1L, 99L)).doesNotThrowAnyException();
        assertThatThrownBy(() -> checker.requireExecutionAccess(2L, 99L))
                .isInstanceOf(PlatformAccessDeniedException.class);
    }
}
