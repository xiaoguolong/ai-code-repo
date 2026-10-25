package com.aicode.framework.platform.infrastructure.seed;

import com.aicode.core.domain.port.PasswordHasher;
import com.aicode.framework.platform.domain.model.PlatformRole;
import com.aicode.framework.platform.domain.model.PlatformTenant;
import com.aicode.framework.platform.domain.model.PlatformUser;
import com.aicode.framework.platform.domain.port.PlatformRolePort;
import com.aicode.framework.platform.domain.port.PlatformUserPort;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.boot.ApplicationArguments;
import org.springframework.boot.ApplicationRunner;
import org.springframework.stereotype.Component;

import java.util.List;
import java.util.Set;
import java.util.stream.Collectors;
import java.util.stream.IntStream;

/**
 * 启动时预置平台角色与用户（admin / operator / viewer，Week 21 增加第二租户用户）。
 *
 * <p>Week 16：改为按 Port 判空，做到落库与内存两种模式都幂等——已有数据直接跳过，
 * 重启、单测重复调用都不会重复插入或抛主键冲突。密码只存哈希，明文不落库。</p>
 *
 * <p>Week 23（B2）：角色按租户复制。本类改为「逐 (tenant, role) 判空、只补缺」，
 * 因此老库升级后 hospital-b 也能拿到自己的 admin/operator/viewer 副本；
 * 已有的角色/授权不会被覆盖（避免把运维手工撤销的权限加回来）。
 * 用户仍只在用户表为空时 seed（老库不重建用户）。</p>
 */
@Component
public class PlatformRbacSeedInitializer implements ApplicationRunner {

    private static final Logger log = LoggerFactory.getLogger(PlatformRbacSeedInitializer.class);

    /** 当前产品已知租户；更多租户时由 V7 迁移按 platform_tenant 复制。 */
    static final List<String> KNOWN_TENANTS = List.of(
            PlatformTenant.DEFAULT_TENANT_KEY, "hospital-b");

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
        int created = seedRolesIfMissing();
        if (created > 0) {
            log.info("[platform] rbac roles seeded for tenants={} created={}", KNOWN_TENANTS, created);
        }
        if (isUserTableEmpty()) {
            seedUsers();
            log.info("[platform] rbac users seeded users=3");
        }
    }

    private boolean isUserTableEmpty() {
        // 三个 seed 用户 id 固定为 1/2/3；任一存在即视为已初始化
        return platformUserPort.findById(1L).isEmpty()
                && platformUserPort.findById(2L).isEmpty()
                && platformUserPort.findById(3L).isEmpty();
    }

    private int seedRolesIfMissing() {
        int created = 0;
        for (String tenantKey : KNOWN_TENANTS) {
            if (platformRolePort.findByKey(tenantKey, "admin").isEmpty()) {
                platformRolePort.save(new PlatformRole(
                        "admin", "平台管理员", true, Set.of(), Set.of(), Set.of(), tenantKey));
                created++;
            }
            if (platformRolePort.findByKey(tenantKey, "operator").isEmpty()) {
                platformRolePort.save(new PlatformRole(
                        "operator", "运营人员", false,
                        Set.of("patient-risk", "medical-assistant", "patient-analysis"),
                        Set.of("PatientLookupTool", "HealthMetricTool", "ReportLookupTool"),
                        operatorPatientIds(), tenantKey));
                created++;
            }
            if (platformRolePort.findByKey(tenantKey, "viewer").isEmpty()) {
                platformRolePort.save(new PlatformRole(
                        "viewer", "只读访客", false, Set.of(), Set.of(), Set.of(), tenantKey));
                created++;
            }
        }
        return created;
    }

    private void seedUsers() {
        saveUser(1L, "admin", "admin123", "admin", PlatformTenant.DEFAULT_TENANT_KEY);
        saveUser(2L, "operator", "operator123", "operator", PlatformTenant.DEFAULT_TENANT_KEY);
        saveUser(3L, "viewer", "viewer123", "viewer", PlatformTenant.DEFAULT_TENANT_KEY);
    }

    private void saveUser(long id, String username, String rawPassword, String roleKey, String tenantKey) {
        String hash = passwordHasher.hash(rawPassword, username);
        platformUserPort.save(new PlatformUser(id, username, hash, roleKey, tenantKey));
    }

    private Set<String> operatorPatientIds() {
        return IntStream.rangeClosed(1, 10)
                .mapToObj(i -> "P" + String.format("%03d", i))
                .collect(Collectors.toUnmodifiableSet());
    }
}
