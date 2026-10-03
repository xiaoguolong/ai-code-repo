package com.aicode.framework.platform.infrastructure.seed;

import com.aicode.framework.platform.domain.model.PlatformRole;
import com.aicode.framework.platform.domain.model.PlatformUser;
import com.aicode.framework.platform.domain.port.AgentRegistryPort;
import com.aicode.framework.platform.domain.port.PlatformRolePort;
import com.aicode.framework.platform.domain.port.PlatformUserPort;
import com.aicode.framework.platform.domain.port.ToolCatalogPort;
import com.aicode.framework.platform.domain.port.WorkflowRegistryPort;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.ApplicationArguments;
import org.springframework.boot.DefaultApplicationArguments;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.test.context.ActiveProfiles;

import static org.assertj.core.api.Assertions.assertThat;

/**
 * 启动预置数据幂等性（H2 MODE=PostgreSQL + Flyway V1）。
 * 落库后 seed 必须可重复执行：第二次启动不得重复插入，也不得抛主键冲突。
 */
@SpringBootTest(properties = {
        "spring.ai.openai.api-key=test-key",
        "auth.password-salt=test-salt",
        "platform.security.enforce-direct-runs=false",
        "platform.persistence.mode=jdbc"
})
@ActiveProfiles("test")
class PlatformSeedIdempotencyTest {

    @Autowired
    private PlatformRbacSeedInitializer rbacSeedInitializer;

    @Autowired
    private PlatformSeedDataInitializer metadataSeedInitializer;

    @Autowired
    private PlatformUserPort platformUserPort;

    @Autowired
    private PlatformRolePort platformRolePort;

    @Autowired
    private AgentRegistryPort agentRegistryPort;

    @Autowired
    private ToolCatalogPort toolCatalogPort;

    @Autowired
    private WorkflowRegistryPort workflowRegistryPort;

    @Autowired
    private JdbcTemplate jdbcTemplate;

    @Test
    void rbacSeedRunsTwiceWithoutDuplicatingUsersAndRoles() {
        ApplicationArguments args = new DefaultApplicationArguments();

        rbacSeedInitializer.run(args);
        int usersAfterFirstRun = countUsers();
        int rolesAfterFirstRun = platformRolePort.listAll().size();
        rbacSeedInitializer.run(args);

        assertThat(countUsers()).isEqualTo(usersAfterFirstRun);
        assertThat(platformRolePort.listAll()).hasSize(rolesAfterFirstRun);
        assertThat(platformUserPort.findById(1L)).isPresent();
        assertThat(platformUserPort.findByUsername("admin")).isPresent();
    }

    @Test
    void rbacSeedStoresHashedPasswordNotPlaintext() {
        PlatformUser admin = platformUserPort.findByUsername("admin").orElseThrow();

        assertThat(admin.passwordHash()).isNotEqualTo("admin123");
        assertThat(admin.passwordHash()).doesNotContain("admin123");
        assertThat(admin.roleKey()).isEqualTo("admin");
    }

    @Test
    void seededRolesKeepAgentToolAndPatientAuthorizations() {
        PlatformRole operator = platformRolePort.findByKey("operator").orElseThrow();
        PlatformRole viewer = platformRolePort.findByKey("viewer").orElseThrow();

        assertThat(operator.allowedAgentKeys()).containsExactlyInAnyOrder("patient-risk", "medical-assistant");
        assertThat(operator.allowedToolKeys()).containsExactlyInAnyOrder("PatientLookupTool", "HealthMetricTool");
        assertThat(operator.allowedPatientIds()).hasSize(10).contains("P001", "P010");
        assertThat(operator.admin()).isFalse();
        assertThat(viewer.allowedAgentKeys()).isEmpty();
    }

    @Test
    void metadataSeedRunsTwiceWithoutDuplicatingRegistries() {
        ApplicationArguments args = new DefaultApplicationArguments();

        metadataSeedInitializer.run(args);
        int agents = agentRegistryPort.listAll().size();
        int tools = toolCatalogPort.listAll().size();
        int workflows = workflowRegistryPort.listAll().size();
        metadataSeedInitializer.run(args);

        assertThat(agentRegistryPort.listAll()).hasSize(agents).hasSize(3);
        assertThat(toolCatalogPort.listAll()).hasSize(tools).hasSize(2);
        assertThat(workflowRegistryPort.listAll()).hasSize(workflows).hasSize(2);
    }

    private int countUsers() {
        Integer count = jdbcTemplate.queryForObject("SELECT COUNT(*) FROM platform_user", Integer.class);
        return count == null ? 0 : count;
    }
}
