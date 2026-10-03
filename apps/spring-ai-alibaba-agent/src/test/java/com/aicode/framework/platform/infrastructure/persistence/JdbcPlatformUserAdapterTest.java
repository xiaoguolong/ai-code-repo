package com.aicode.framework.platform.infrastructure.persistence;

import com.aicode.framework.platform.domain.model.PlatformUser;
import com.aicode.framework.platform.domain.port.PlatformUserPort;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.test.context.ActiveProfiles;

import java.util.Optional;

import static org.assertj.core.api.Assertions.assertThat;

/**
 * 平台用户落库适配器（H2 MODE=PostgreSQL + Flyway V1）。
 */
@SpringBootTest(properties = {
        "spring.ai.openai.api-key=test-key",
        "auth.password-salt=test-salt",
        "platform.security.enforce-direct-runs=false",
        "platform.persistence.mode=jdbc"
})
@ActiveProfiles("test")
class JdbcPlatformUserAdapterTest {

    @Autowired
    private PlatformUserPort platformUserPort;

    @Test
    void wiresJdbcAdapterWhenModeIsJdbc() {
        assertThat(platformUserPort).isInstanceOf(JdbcPlatformUserAdapter.class);
    }

    @Test
    void savesAndFindsUserByUsernameAndId() {
        platformUserPort.save(new PlatformUser(9001L, "jdbc-alice", "hashed-value", "viewer"));

        Optional<PlatformUser> byName = platformUserPort.findByUsername("jdbc-alice");
        Optional<PlatformUser> byId = platformUserPort.findById(9001L);

        assertThat(byName).isPresent();
        assertThat(byName.get().passwordHash()).isEqualTo("hashed-value");
        assertThat(byId).isPresent();
        assertThat(byId.get().roleKey()).isEqualTo("viewer");
    }

    @Test
    void overwritesExistingUserOnSecondSave() {
        platformUserPort.save(new PlatformUser(9002L, "jdbc-bob", "hash-1", "viewer"));
        platformUserPort.save(new PlatformUser(9002L, "jdbc-bob", "hash-2", "operator"));

        Optional<PlatformUser> found = platformUserPort.findById(9002L);

        assertThat(found).isPresent();
        assertThat(found.get().passwordHash()).isEqualTo("hash-2");
        assertThat(found.get().roleKey()).isEqualTo("operator");
    }

    @Test
    void returnsEmptyForUnknownUsername() {
        assertThat(platformUserPort.findByUsername("nobody-at-all")).isEmpty();
    }
}
