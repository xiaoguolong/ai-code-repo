package com.aicode.framework.platform.infrastructure.seed;

import com.aicode.core.domain.port.PasswordHasher;
import com.aicode.framework.platform.domain.model.PlatformRole;
import com.aicode.framework.platform.domain.model.PlatformUser;
import com.aicode.framework.platform.domain.port.PlatformRolePort;
import com.aicode.framework.platform.domain.port.PlatformUserPort;
import com.aicode.framework.platform.infrastructure.persistence.InMemoryPlatformRoleAdapter;
import com.aicode.framework.platform.infrastructure.persistence.InMemoryPlatformUserAdapter;
import org.springframework.boot.ApplicationArguments;
import org.springframework.boot.ApplicationRunner;
import org.springframework.stereotype.Component;

import java.util.Set;
import java.util.stream.IntStream;

/**
 * 启动时预置平台角色与用户（admin / operator / viewer）。
 */
@Component
public class PlatformRbacSeedInitializer implements ApplicationRunner {

    private final PlatformRolePort platformRolePort;
    private final PlatformUserPort platformUserPort;
    private final PasswordHasher passwordHasher;

    public PlatformRbacSeedInitializer(
            PlatformRolePort platformRolePort,
            PlatformUserPort platformUserPort,
            PasswordHasher passwordHasher
    ) {
        this.platformRolePort = platformRolePort;
        this.platformUserPort = platformUserPort;
        this.passwordHasher = passwordHasher;
    }

    @Override
    public void run(ApplicationArguments args) {
        if (platformRolePort instanceof InMemoryPlatformRoleAdapter adapter && !adapter.isEmpty()) {
            return;
        }
        seedRoles();
        seedUsers();
    }

    private void seedRoles() {
        platformRolePort.save(new PlatformRole(
                "admin", "平台管理员", true, Set.of(), Set.of(), Set.of()));
        platformRolePort.save(new PlatformRole(
                "operator", "运营人员",
                false,
                Set.of("patient-risk", "medical-assistant"),
                Set.of("PatientLookupTool", "HealthMetricTool"),
                operatorPatientIds()));
        platformRolePort.save(new PlatformRole(
                "viewer", "只读访客", false, Set.of(), Set.of(), Set.of()));
    }

    private void seedUsers() {
        saveUser(1L, "admin", "admin123", "admin");
        saveUser(2L, "operator", "operator123", "operator");
        saveUser(3L, "viewer", "viewer123", "viewer");
    }

    private void saveUser(long id, String username, String rawPassword, String roleKey) {
        String hash = passwordHasher.hash(rawPassword, username);
        platformUserPort.save(new PlatformUser(id, username, hash, roleKey));
    }

    private Set<String> operatorPatientIds() {
        return IntStream.rangeClosed(1, 10)
                .mapToObj(i -> "P" + String.format("%03d", i))
                .collect(java.util.stream.Collectors.toUnmodifiableSet());
    }
}
