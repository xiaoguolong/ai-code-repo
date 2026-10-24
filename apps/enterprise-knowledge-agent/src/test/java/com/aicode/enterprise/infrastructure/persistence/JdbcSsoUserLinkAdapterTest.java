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
}
