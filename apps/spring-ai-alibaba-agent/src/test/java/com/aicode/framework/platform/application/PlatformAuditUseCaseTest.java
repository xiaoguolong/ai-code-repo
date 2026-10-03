package com.aicode.framework.platform.application;

import com.aicode.framework.platform.domain.model.AuditAction;
import com.aicode.framework.platform.domain.model.AuditLogEntry;
import com.aicode.framework.platform.domain.model.AuditResult;
import com.aicode.framework.platform.domain.model.PlatformRole;
import com.aicode.framework.platform.domain.model.PlatformUser;
import com.aicode.framework.platform.domain.service.PlatformPermissionChecker;
import com.aicode.framework.platform.infrastructure.persistence.InMemoryAuditLogAdapter;
import com.aicode.framework.platform.infrastructure.persistence.InMemoryPlatformRoleAdapter;
import com.aicode.framework.platform.infrastructure.persistence.InMemoryPlatformUserAdapter;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;

import java.time.Instant;
import java.util.Set;

import static org.assertj.core.api.Assertions.assertThat;

/**
 * 审计查询用例：admin 可见全部，其他角色只看自己（最小权限）。
 */
class PlatformAuditUseCaseTest {

    private InMemoryAuditLogAdapter auditLog;
    private PlatformAuditUseCase useCase;

    @BeforeEach
    void setUp() {
        auditLog = new InMemoryAuditLogAdapter();
        InMemoryPlatformUserAdapter users = new InMemoryPlatformUserAdapter();
        InMemoryPlatformRoleAdapter roles = new InMemoryPlatformRoleAdapter();
        users.save(new PlatformUser(1L, "admin", "hash", "admin"));
        users.save(new PlatformUser(2L, "operator", "hash", "operator"));
        roles.save(new PlatformRole("admin", "Admin", true, Set.of(), Set.of(), Set.of()));
        roles.save(new PlatformRole("operator", "Operator", false, Set.of(), Set.of(), Set.of()));
        useCase = new PlatformAuditUseCase(auditLog, new PlatformPermissionChecker(users, roles));

        auditLog.record(entry(1L, AuditAction.LOGIN, Instant.parse("2026-10-01T01:00:00Z")));
        auditLog.record(entry(2L, AuditAction.AGENT_RUN, Instant.parse("2026-10-01T02:00:00Z")));
    }

    @Test
    void adminSeesAllAuditEntriesNewestFirst() {
        assertThat(useCase.listAuditLogs(1L))
                .extracting(AuditLogEntry::action)
                .containsExactly(AuditAction.AGENT_RUN, AuditAction.LOGIN);
    }

    @Test
    void nonAdminSeesOnlyOwnAuditEntries() {
        assertThat(useCase.listAuditLogs(2L))
                .extracting(AuditLogEntry::userId)
                .containsExactly(2L);
    }

    @Test
    void returnsEmptyListForUserWithoutAuditHistory() {
        assertThat(useCase.listAuditLogs(1L)).isNotEmpty();
        InMemoryPlatformUserAdapter freshUsers = new InMemoryPlatformUserAdapter();
        InMemoryPlatformRoleAdapter freshRoles = new InMemoryPlatformRoleAdapter();
        freshUsers.save(new PlatformUser(9L, "viewer", "hash", "viewer"));
        freshRoles.save(new PlatformRole("viewer", "Viewer", false, Set.of(), Set.of(), Set.of()));
        PlatformAuditUseCase fresh = new PlatformAuditUseCase(
                auditLog, new PlatformPermissionChecker(freshUsers, freshRoles));

        assertThat(fresh.listAuditLogs(9L)).isEmpty();
    }

    private AuditLogEntry entry(long userId, AuditAction action, Instant createdAt) {
        return new AuditLogEntry(0L, userId, action, "resource", AuditResult.SUCCESS, "t", "", createdAt);
    }
}
