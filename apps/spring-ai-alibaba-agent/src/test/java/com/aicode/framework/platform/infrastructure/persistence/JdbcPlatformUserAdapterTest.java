package com.aicode.framework.platform.infrastructure.persistence;

import com.aicode.framework.platform.domain.model.PlatformUser;
import com.aicode.framework.platform.domain.port.PlatformUserPort;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.test.context.ActiveProfiles;

import java.time.Instant;
import java.util.Optional;

import static org.assertj.core.api.Assertions.assertThat;

/**
 * 平台用户落库适配器（H2 MODE=PostgreSQL + Flyway V1/V11）。
 */
@SpringBootTest(properties = {
        "spring.ai.openai.api-key=test-key",
        "auth.password-salt=test-salt",
        "platform.security.enforce-direct-runs=false",
        "platform.persistence.mode=jdbc"
})
@ActiveProfiles("test")
class JdbcPlatformUserAdapterTest {

    private static final Instant DISABLED_AT = Instant.parse("2026-10-25T07:30:00Z");

    @Autowired
    private PlatformUserPort platformUserPort;

    @Autowired
    private JdbcTemplate jdbcTemplate;

    @BeforeEach
    void cleanOwnUsers() {
        jdbcTemplate.update("DELETE FROM platform_user WHERE id IN (9001, 9002, 9003, 9004)");
    }

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

    @Test
    void persistsAndClearsDisabledAt() {
        platformUserPort.save(new PlatformUser(9003L, "jdbc-carol", "hash", "operator"));

        assertThat(platformUserPort.updateDisabledAt(9003L, DISABLED_AT)).isTrue();
        PlatformUser disabled = platformUserPort.findById(9003L).orElseThrow();
        assertThat(disabled.disabled()).isTrue();
        assertThat(disabled.disabledAt()).isEqualTo(DISABLED_AT);

        assertThat(platformUserPort.updateDisabledAt(9003L, null)).isTrue();
        assertThat(platformUserPort.findById(9003L).orElseThrow().disabled()).isFalse();
    }

    @Test
    void seedStyleSaveDoesNotOverwriteDisabledAt() {
        platformUserPort.save(new PlatformUser(9004L, "jdbc-dave", "hash", "operator"));
        platformUserPort.updateDisabledAt(9004L, DISABLED_AT);

        // 模拟启动 seed：只带身份字段再 save 一次
        platformUserPort.save(new PlatformUser(9004L, "jdbc-dave", "hash-2", "operator"));

        PlatformUser loaded = platformUserPort.findById(9004L).orElseThrow();
        assertThat(loaded.passwordHash()).isEqualTo("hash-2");
        assertThat(loaded.disabledAt()).isEqualTo(DISABLED_AT);
    }

    @Test
    void listAllReturnsUsersOrderedById() {
        platformUserPort.save(new PlatformUser(9002L, "jdbc-bob", "hash", "operator"));
        platformUserPort.save(new PlatformUser(9001L, "jdbc-alice", "hash", "viewer"));

        assertThat(platformUserPort.listAll())
                .extracting(PlatformUser::id)
                .contains(9001L, 9002L)
                .isSorted();
    }

    @Test
    void updateDisabledAtUnknownUserReturnsFalse() {
        assertThat(platformUserPort.updateDisabledAt(909999L, DISABLED_AT)).isFalse();
    }
}
