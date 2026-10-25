package com.aicode.framework.platform.application;

import com.aicode.core.domain.exception.InvalidChatRequestException;
import com.aicode.framework.platform.domain.exception.PlatformAccessDeniedException;
import com.aicode.framework.platform.domain.exception.PlatformNotFoundException;
import com.aicode.framework.platform.domain.model.AuditAction;
import com.aicode.framework.platform.domain.model.PlatformRole;
import com.aicode.framework.platform.domain.model.PlatformUser;
import com.aicode.framework.platform.domain.port.PlatformSessionPort;
import com.aicode.framework.platform.domain.service.PlatformPermissionChecker;
import com.aicode.framework.platform.infrastructure.persistence.InMemoryAuditLogAdapter;
import com.aicode.framework.platform.infrastructure.persistence.InMemoryPlatformRoleAdapter;
import com.aicode.framework.platform.infrastructure.persistence.InMemoryPlatformUserAdapter;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;

import java.time.Clock;
import java.time.Instant;
import java.time.ZoneOffset;
import java.util.Set;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatCode;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.mockito.ArgumentMatchers.anyLong;
import static org.mockito.Mockito.doThrow;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.verify;

/**
 * 平台用户管理用例（Week 28，TD-06）：停用/恢复、权限、幂等、踢会话。
 */
class PlatformUserAdminUseCaseTest {

    private static final Instant NOW = Instant.parse("2026-10-25T07:30:00Z");
    private static final long ADMIN_ID = 1L;
    private static final long OPERATOR_ID = 2L;
    private static final long VIEWER_ID = 3L;

    private InMemoryPlatformUserAdapter users;
    private InMemoryAuditLogAdapter audit;
    private PlatformSessionPort sessionPort;
    private PlatformUserAdminUseCase useCase;

    @BeforeEach
    void setUp() {
        users = new InMemoryPlatformUserAdapter();
        audit = new InMemoryAuditLogAdapter();
        sessionPort = mock(PlatformSessionPort.class);
        InMemoryPlatformRoleAdapter roles = new InMemoryPlatformRoleAdapter();
        roles.save(new PlatformRole("admin", "管理员", true, Set.of(), Set.of(), Set.of()));
        roles.save(new PlatformRole("operator", "运营", false, Set.of(), Set.of(), Set.of()));
        roles.save(new PlatformRole("viewer", "只读", false, Set.of(), Set.of(), Set.of()));
        users.save(new PlatformUser(ADMIN_ID, "admin", "hash", "admin"));
        users.save(new PlatformUser(OPERATOR_ID, "operator", "hash", "operator"));
        users.save(new PlatformUser(VIEWER_ID, "viewer", "hash", "viewer"));
        useCase = new PlatformUserAdminUseCase(
                users, new PlatformPermissionChecker(users, roles), sessionPort, audit,
                Clock.fixed(NOW, ZoneOffset.UTC));
    }

    @Test
    void listIsAdminOnly() {
        assertThat(useCase.list(ADMIN_ID)).extracting(PlatformUserView::username)
                .contains("admin", "operator", "viewer");
        assertThatThrownBy(() -> useCase.list(OPERATOR_ID))
                .isInstanceOf(PlatformAccessDeniedException.class);
    }

    @Test
    void disableWritesTimestampRevokesSessionsAndAudits() {
        PlatformUserView view = useCase.disable(ADMIN_ID, OPERATOR_ID);

        assertThat(view.disabled()).isTrue();
        assertThat(view.disabledAt()).isEqualTo(NOW);
        assertThat(users.findById(OPERATOR_ID).orElseThrow().disabled()).isTrue();
        verify(sessionPort).revokeAll(OPERATOR_ID);
        assertThat(audit.listAll()).singleElement().satisfies(entry -> {
            assertThat(entry.action()).isEqualTo(AuditAction.USER_DISABLE);
            assertThat(entry.resource()).isEqualTo("user:" + OPERATOR_ID);
            assertThat(entry.detail()).contains("username=operator");
        });
    }

    @Test
    void disableIsIdempotent() {
        useCase.disable(ADMIN_ID, OPERATOR_ID);
        useCase.disable(ADMIN_ID, OPERATOR_ID);

        assertThat(audit.listAll()).hasSize(1);
        verify(sessionPort).revokeAll(OPERATOR_ID);
    }

    @Test
    void cannotDisableYourself() {
        assertThatThrownBy(() -> useCase.disable(ADMIN_ID, ADMIN_ID))
                .isInstanceOf(InvalidChatRequestException.class);
        assertThat(users.findById(ADMIN_ID).orElseThrow().disabled()).isFalse();
    }

    @Test
    void enableClearsDisabledAndAudits() {
        useCase.disable(ADMIN_ID, OPERATOR_ID);

        PlatformUserView view = useCase.enable(ADMIN_ID, OPERATOR_ID);

        assertThat(view.disabled()).isFalse();
        assertThat(view.disabledAt()).isNull();
        assertThat(users.findById(OPERATOR_ID).orElseThrow().disabled()).isFalse();
        assertThat(audit.listAll())
                .extracting(entry -> entry.action())
                .containsExactlyInAnyOrder(AuditAction.USER_DISABLE, AuditAction.USER_ENABLE);
    }

    @Test
    void enableIsIdempotent() {
        useCase.enable(ADMIN_ID, OPERATOR_ID);

        assertThat(audit.listAll()).isEmpty();
    }

    @Test
    void sessionRevokeFailureDoesNotRollbackDisable() {
        doThrow(new IllegalStateException("sa-token down")).when(sessionPort).revokeAll(anyLong());

        assertThatCode(() -> useCase.disable(ADMIN_ID, VIEWER_ID)).doesNotThrowAnyException();
        assertThat(users.findById(VIEWER_ID).orElseThrow().disabled()).isTrue();
    }

    @Test
    void unknownAndNonAdminAreRejected() {
        assertThatThrownBy(() -> useCase.disable(ADMIN_ID, 999L))
                .isInstanceOf(PlatformNotFoundException.class);
        assertThatThrownBy(() -> useCase.disable(OPERATOR_ID, VIEWER_ID))
                .isInstanceOf(PlatformAccessDeniedException.class);
        verify(sessionPort, never()).revokeAll(anyLong());
    }
}
