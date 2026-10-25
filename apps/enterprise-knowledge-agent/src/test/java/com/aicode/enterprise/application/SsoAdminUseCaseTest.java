package com.aicode.enterprise.application;

import com.aicode.core.domain.exception.InvalidChatRequestException;
import com.aicode.core.domain.exception.NotFoundException;
import com.aicode.core.domain.model.User;
import com.aicode.enterprise.domain.exception.ConflictException;
import com.aicode.enterprise.domain.model.SsoUserLink;
import com.aicode.enterprise.support.InMemorySsoUserLinkAdapter;
import com.aicode.enterprise.support.InMemoryUserPort;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;

import java.time.Instant;
import java.util.List;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

/**
 * SSO 映射运维用例（Week 23，A1）：建链/重建、幂等、冲突、解绑（含删本地用户）。
 */
class SsoAdminUseCaseTest {

    private static final Instant NOW = Instant.parse("2026-10-25T00:00:00Z");

    private InMemoryUserPort userPort;
    private InMemorySsoUserLinkAdapter linkPort;
    private SsoAdminUseCase useCase;
    private User alice;
    private User bob;

    @BeforeEach
    void setUp() {
        userPort = new InMemoryUserPort();
        linkPort = new InMemorySsoUserLinkAdapter();
        useCase = new SsoAdminUseCase(linkPort, userPort);
        alice = userPort.save(new User(null, "alice", "hash", NOW));
        bob = userPort.save(new User(null, "bob", "hash", NOW));
    }

    @Test
    void linksPlatformUserToLocalUser() {
        SsoUserLink link = useCase.link(42L, alice.id(), "default");

        assertThat(link.platformUserId()).isEqualTo(42L);
        assertThat(link.enterpriseUserId()).isEqualTo(alice.id());
        assertThat(link.tenantKey()).isEqualTo("default");
        assertThat(link.linkedAt()).isNotNull();
        assertThat(linkPort.size()).isEqualTo(1);
    }

    @Test
    void relinkingSamePairIsIdempotent() {
        useCase.link(42L, alice.id(), "default");
        useCase.link(42L, alice.id(), "default");

        assertThat(linkPort.size()).isEqualTo(1);
        assertThat(linkPort.findEnterpriseUserId(42L)).contains(alice.id());
    }

    @Test
    void rebindingPlatformUserReplacesOldLocalUser() {
        useCase.link(42L, alice.id(), "default");
        useCase.link(42L, bob.id(), "default");

        assertThat(linkPort.findEnterpriseUserId(42L)).contains(bob.id());
        assertThat(linkPort.size()).isEqualTo(1);
    }

    @Test
    void rejectsLocalUserAlreadyLinkedToAnotherPlatformUser() {
        useCase.link(42L, alice.id(), "default");

        assertThatThrownBy(() -> useCase.link(43L, alice.id(), "default"))
                .isInstanceOf(ConflictException.class);
        assertThat(linkPort.findEnterpriseUserId(42L)).contains(alice.id());
    }

    @Test
    void rejectsUnknownLocalUser() {
        assertThatThrownBy(() -> useCase.link(42L, 9999L, "default"))
                .isInstanceOf(NotFoundException.class);
    }

    @Test
    void rejectsNonPositiveIds() {
        assertThatThrownBy(() -> useCase.link(0L, alice.id(), "default"))
                .isInstanceOf(InvalidChatRequestException.class);
    }

    @Test
    void unlinkOnlyLeavesLocalUser() {
        useCase.link(42L, alice.id(), "default");

        SsoAdminUseCase.SsoUnlinkResult result = useCase.unlink(42L, false);

        assertThat(result.unlinked()).isTrue();
        assertThat(result.localUserDeleted()).isFalse();
        assertThat(linkPort.size()).isZero();
        assertThat(userPort.findById(alice.id())).isPresent();
    }

    @Test
    void unlinkWithDeleteLocalUserRemovesBoth() {
        useCase.link(42L, alice.id(), "default");

        SsoAdminUseCase.SsoUnlinkResult result = useCase.unlink(42L, true);

        assertThat(result.unlinked()).isTrue();
        assertThat(result.localUserDeleted()).isTrue();
        assertThat(linkPort.size()).isZero();
        assertThat(userPort.findById(alice.id())).isEmpty();
    }

    @Test
    void unlinkUnknownPlatformUserIsNotFound() {
        assertThatThrownBy(() -> useCase.unlink(999L, false))
                .isInstanceOf(NotFoundException.class);
    }

    @Test
    void listClampsLimitAndReturnsLinks() {
        useCase.link(42L, alice.id(), "default");
        useCase.link(43L, bob.id(), "hospital-b");

        List<SsoUserLink> all = useCase.list(1000);
        assertThat(all).hasSize(2);
        assertThat(useCase.list(0)).hasSize(2);
        assertThat(useCase.list(1)).hasSize(1);
    }
}
