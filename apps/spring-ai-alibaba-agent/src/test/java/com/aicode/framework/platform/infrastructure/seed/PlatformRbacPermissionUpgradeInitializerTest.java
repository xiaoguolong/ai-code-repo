package com.aicode.framework.platform.infrastructure.seed;

import com.aicode.framework.platform.domain.model.PlatformRole;
import com.aicode.framework.platform.infrastructure.persistence.InMemoryPlatformRoleAdapter;
import org.junit.jupiter.api.Test;
import org.springframework.boot.DefaultApplicationArguments;

import java.util.Set;

import static org.assertj.core.api.Assertions.assertThat;

/**
 * 角色授权增量升级（Week 21）：老库补权限，但**不覆盖**运维的手工调整。
 */
class PlatformRbacPermissionUpgradeInitializerTest {

    @Test
    void addsWeek21PermissionsToExistingOperatorRole() {
        InMemoryPlatformRoleAdapter roles = new InMemoryPlatformRoleAdapter();
        roles.save(new PlatformRole("operator", "运营人员", false,
                Set.of("patient-risk", "medical-assistant"),
                Set.of("PatientLookupTool", "HealthMetricTool"),
                Set.of("P001")));

        new PlatformRbacPermissionUpgradeInitializer(roles).run(new DefaultApplicationArguments());

        PlatformRole operator = roles.findByKey("operator").orElseThrow();
        assertThat(operator.allowedAgentKeys())
                .containsExactlyInAnyOrder(
                        "patient-risk", "medical-assistant", "patient-analysis", "followup");
        assertThat(operator.allowedToolKeys())
                .containsExactlyInAnyOrder("PatientLookupTool", "HealthMetricTool", "ReportLookupTool");
        assertThat(operator.allowedPatientIds()).containsExactly("P001");
    }

    @Test
    void keepsManuallyRevokedPermissionsUntouched() {
        InMemoryPlatformRoleAdapter roles = new InMemoryPlatformRoleAdapter();
        roles.save(new PlatformRole("operator", "运营人员", false,
                Set.of("patient-analysis"), Set.of("ReportLookupTool"), Set.of()));

        new PlatformRbacPermissionUpgradeInitializer(roles).run(new DefaultApplicationArguments());

        PlatformRole operator = roles.findByKey("operator").orElseThrow();
        assertThat(operator.allowedAgentKeys()).containsExactlyInAnyOrder("patient-analysis", "followup");
        assertThat(operator.allowedToolKeys()).containsExactly("ReportLookupTool");
    }

    @Test
    void doesNothingWhenOperatorRoleMissing() {
        InMemoryPlatformRoleAdapter roles = new InMemoryPlatformRoleAdapter();

        new PlatformRbacPermissionUpgradeInitializer(roles).run(new DefaultApplicationArguments());

        assertThat(roles.listAll()).isEmpty();
    }
}
