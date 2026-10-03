package com.aicode.framework.platform.infrastructure.persistence;

import com.aicode.framework.platform.domain.model.PlatformRole;
import com.aicode.framework.platform.domain.port.PlatformRolePort;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.test.context.ActiveProfiles;

import java.util.Optional;
import java.util.Set;

import static org.assertj.core.api.Assertions.assertThat;

/**
 * 平台角色落库适配器（H2 MODE=PostgreSQL + Flyway V1）。
 * 重点验证 Agent / Tool / patientId 三类授权集合的读写与覆盖写。
 */
@SpringBootTest(properties = {
        "spring.ai.openai.api-key=test-key",
        "auth.password-salt=test-salt",
        "platform.security.enforce-direct-runs=false",
        "platform.persistence.mode=jdbc"
})
@ActiveProfiles("test")
class JdbcPlatformRoleAdapterTest {

    @Autowired
    private PlatformRolePort platformRolePort;

    @Test
    void wiresJdbcAdapterWhenModeIsJdbc() {
        assertThat(platformRolePort).isInstanceOf(JdbcPlatformRoleAdapter.class);
    }

    @Test
    void savesRoleWithThreeAuthorizationSets() {
        platformRolePort.save(new PlatformRole(
                "jdbc-nurse", "落库护士", false,
                Set.of("patient-risk", "medical-assistant"),
                Set.of("PatientLookupTool"),
                Set.of("P001", "P002")));

        Optional<PlatformRole> found = platformRolePort.findByKey("jdbc-nurse");

        assertThat(found).isPresent();
        PlatformRole role = found.get();
        assertThat(role.displayName()).isEqualTo("落库护士");
        assertThat(role.admin()).isFalse();
        assertThat(role.allowedAgentKeys()).containsExactlyInAnyOrder("patient-risk", "medical-assistant");
        assertThat(role.allowedToolKeys()).containsExactly("PatientLookupTool");
        assertThat(role.allowedPatientIds()).containsExactlyInAnyOrder("P001", "P002");
    }

    @Test
    void overwriteReplacesAuthorizationSets() {
        platformRolePort.save(new PlatformRole(
                "jdbc-editor", "落库编辑", false,
                Set.of("patient-risk"), Set.of("PatientLookupTool"), Set.of("P001")));
        platformRolePort.save(new PlatformRole(
                "jdbc-editor", "落库编辑", false,
                Set.of("medical-assistant"), Set.of("HealthMetricTool"), Set.of("P009")));

        PlatformRole role = platformRolePort.findByKey("jdbc-editor").orElseThrow();

        assertThat(role.allowedAgentKeys()).containsExactly("medical-assistant");
        assertThat(role.allowedToolKeys()).containsExactly("HealthMetricTool");
        assertThat(role.allowedPatientIds()).containsExactly("P009");
    }

    @Test
    void adminRoleWildcardWithEmptySetsRoundTrips() {
        platformRolePort.save(new PlatformRole("jdbc-admin", "落库管理员", true, Set.of(), Set.of(), Set.of()));

        PlatformRole role = platformRolePort.findByKey("jdbc-admin").orElseThrow();

        assertThat(role.admin()).isTrue();
        assertThat(role.allowedAgentKeys()).isEmpty();
        assertThat(role.allowsAgent("any-agent")).isTrue();
        assertThat(role.allowsPatient("P999")).isTrue();
    }

    @Test
    void listAllReturnsRolesSortedByKey() {
        // 只断言写入的 roleKey 存在且有序，不依赖 seed 数据是否已执行
        platformRolePort.save(new PlatformRole("jdbc-zzz", "末尾角色", false, Set.of(), Set.of(), Set.of()));
        platformRolePort.save(new PlatformRole("jdbc-aaa", "开头角色", false, Set.of(), Set.of(), Set.of()));

        assertThat(platformRolePort.listAll())
                .extracting(PlatformRole::roleKey)
                .contains("jdbc-aaa", "jdbc-zzz")
                .isSorted();
    }
}
