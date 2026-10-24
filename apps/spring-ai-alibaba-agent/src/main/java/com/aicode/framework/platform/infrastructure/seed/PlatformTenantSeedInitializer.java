package com.aicode.framework.platform.infrastructure.seed;

import com.aicode.framework.platform.domain.model.PlatformTenant;
import com.aicode.framework.platform.domain.model.TenantStatus;
import com.aicode.framework.platform.domain.port.PlatformTenantPort;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.boot.ApplicationArguments;
import org.springframework.boot.ApplicationRunner;
import org.springframework.stereotype.Component;

import java.time.Instant;

/**
 * 启动时预置租户（Week 21）。
 *
 * <p><b>按 tenantKey 逐个判空，而不是「表里有没有数据」</b>：真库验证时发现，
 * 用整体判空会让「新版本新增的租户」在老库上永远不出现（已有数据 → 整体跳过）。
 * 逐个判空也避免了 {@code save} 的覆盖语义把运维手工停用的租户改回 ACTIVE。</p>
 */
@Component
public class PlatformTenantSeedInitializer implements ApplicationRunner {

    private static final Logger log = LoggerFactory.getLogger(PlatformTenantSeedInitializer.class);

    static final String HOSPITAL_B_TENANT_KEY = "hospital-b";

    private final PlatformTenantPort tenantPort;

    public PlatformTenantSeedInitializer(PlatformTenantPort tenantPort) {
        this.tenantPort = tenantPort;
    }

    @Override
    public void run(ApplicationArguments args) {
        int created = 0;
        created += ensureTenant(PlatformTenant.DEFAULT_TENANT_KEY, "默认租户");
        created += ensureTenant(HOSPITAL_B_TENANT_KEY, "演示医院B");
        if (created > 0) {
            log.info("[platform] tenant seed applied created={}", created);
        }
    }

    private int ensureTenant(String tenantKey, String displayName) {
        if (tenantPort.findByKey(tenantKey).isPresent()) {
            return 0;
        }
        tenantPort.save(new PlatformTenant(tenantKey, displayName, TenantStatus.ACTIVE, Instant.now()));
        return 1;
    }
}
