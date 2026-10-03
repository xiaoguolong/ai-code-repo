package com.aicode.framework.platform.infrastructure.seed;

import com.aicode.core.domain.port.PasswordHasher;
import com.aicode.framework.platform.domain.model.PlatformRole;
import com.aicode.framework.platform.domain.model.PlatformUser;
import com.aicode.framework.platform.domain.port.PlatformRolePort;
import com.aicode.framework.platform.domain.port.PlatformUserPort;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.boot.ApplicationArguments;
import org.springframework.boot.ApplicationRunner;
import org.springframework.stereotype.Component;

import java.util.Set;
import java.util.stream.Collectors;
import java.util.stream.IntStream;

/**
 * 启动时预置平台角色与用户（admin / operator / viewer）。
 *
 * <p>Week 16：改为按 Port 判空，做到落库与内存两种模式都幂等——已有数据直接跳过，
 * 重启、单测重复调用都不会重复插入或抛主键冲突。密码只存哈希，明文不落库。</p>
 */
@Component
public class PlatformRbacSeedInitializer implements ApplicationRunner {

    private static final Logger log = LoggerFactory.getLogger(PlatformRbacSeedInitializer.class);

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
        if (!platformRolePort.listAll().isEmpty() || !isUserTableEmpty()) {
            return;
        }
        seedRoles();
        seedUsers();
        log.info("[platform] rbac seed applied roles=3 users=3");
    }

    private boolean isUserTableEmpty() {
        // 三个 seed 用户 id 固定为 1/2/3；任一存在即视为已初始化
        return platformUserPort.findById(1L).isEmpty()
                && platformUserPort.findById(2L).isEmpty()
                && platformUserPort.findById(3L).isEmpty();
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
                .collect(Collectors.toUnmodifiableSet());
    }
}
