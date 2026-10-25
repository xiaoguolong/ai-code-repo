package com.aicode.enterprise.infrastructure.persistence;

import com.aicode.enterprise.domain.port.SsoUserLinkPort;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.test.context.ActiveProfiles;
import org.springframework.transaction.annotation.Transactional;

import static org.assertj.core.api.Assertions.assertThat;

/** SSO 映射表落库适配器（H2 MODE=PostgreSQL + Flyway V7）。 */
@SpringBootTest(webEnvironment = SpringBootTest.WebEnvironment.NONE)
@ActiveProfiles("test")
@Transactional
class JdbcSsoUserLinkAdapterTest {

    @Autowired
    private SsoUserLinkPort ssoUserLinkPort;

    @Test
    void linksPlatformUserToEnterpriseUserAndReadsBack() {
        ssoUserLinkPort.link(42L, 7L, "default");

        assertThat(ssoUserLinkPort.findEnterpriseUserId(42L)).contains(7L);
    }

    @Test
    void relinkingSamePlatformUserIsIdempotent() {
        ssoUserLinkPort.link(43L, 8L, "default");
        ssoUserLinkPort.link(43L, 8L, "default");

        assertThat(ssoUserLinkPort.findEnterpriseUserId(43L)).contains(8L);
    }

    @Test
    void returnsEmptyForUnknownPlatformUser() {
        assertThat(ssoUserLinkPort.findEnterpriseUserId(999999L)).isEmpty();
    }

    @Test
    void readsFullLinkFromBothDirections() {
        ssoUserLinkPort.link(44L, 9L, "hospital-b");

        assertThat(ssoUserLinkPort.findByPlatformUserId(44L)).get()
                .satisfies(link -> {
                    assertThat(link.enterpriseUserId()).isEqualTo(9L);
                    assertThat(link.tenantKey()).isEqualTo("hospital-b");
                    assertThat(link.linkedAt()).isNotNull();
                });
        assertThat(ssoUserLinkPort.findByEnterpriseUserId(9L).orElseThrow().platformUserId())
                .isEqualTo(44L);
    }

    @Test
    void listsLinksAndUnlinks() {
        ssoUserLinkPort.link(45L, 10L, "default");
        ssoUserLinkPort.link(46L, 11L, "default");

        assertThat(ssoUserLinkPort.listAll(50))
                .extracting(link -> link.platformUserId())
                .contains(45L, 46L);
        assertThat(ssoUserLinkPort.unlink(45L)).isTrue();
        assertThat(ssoUserLinkPort.unlink(45L)).isFalse();
        assertThat(ssoUserLinkPort.findByPlatformUserId(45L)).isEmpty();
    }
}
