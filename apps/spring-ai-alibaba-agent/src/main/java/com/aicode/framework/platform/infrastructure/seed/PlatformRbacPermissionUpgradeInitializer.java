package com.aicode.framework.platform.infrastructure.seed;

import com.aicode.framework.platform.domain.model.PlatformRole;
import com.aicode.framework.platform.domain.port.PlatformRolePort;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.boot.ApplicationArguments;
import org.springframework.boot.ApplicationRunner;
import org.springframework.stereotype.Component;

import java.util.LinkedHashSet;
import java.util.Set;

/**
 * 角色授权**增量升级**（Week 21）。
 *
 * <p>真机验收发现的问题：{@link PlatformRbacSeedInitializer} 以「角色表是否为空」为整体开关，
 * 老库已有 operator 角色 → 第 21 周新增的 {@code patient-analysis} / {@code ReportLookupTool}
 * 永远授不出去，表现为「接口 403，但代码和文档都说有权限」。</p>
 *
 * <p>为什么不直接改 seed 为无脑覆盖：覆盖会把运维**手工撤销**的权限又加回来（安全反模式）。
 * 这里只做「并集新增」：本次版本新增的权限如果缺失就补上，其它授权状态原样保留。</p>
 */
@Component
public class PlatformRbacPermissionUpgradeInitializer implements ApplicationRunner {

    private static final Logger log = LoggerFactory.getLogger(PlatformRbacPermissionUpgradeInitializer.class);

    static final String OPERATOR_ROLE_KEY = "operator";

    /** 第 21 周新增给 operator 的授权（只增不减）。 */
    static final Set<String> WEEK_21_AGENTS = Set.of("patient-analysis");
    static final Set<String> WEEK_21_TOOLS = Set.of("ReportLookupTool");

    private final PlatformRolePort rolePort;

    public PlatformRbacPermissionUpgradeInitializer(PlatformRolePort rolePort) {
        this.rolePort = rolePort;
    }

    @Override
    public void run(ApplicationArguments args) {
        rolePort.listAll().stream()
                .filter(role -> OPERATOR_ROLE_KEY.equals(role.roleKey()))
                .forEach(this::upgrade);
    }

    private void upgrade(PlatformRole role) {
        Set<String> existingAgents = role.allowedAgentKeys() == null ? Set.of() : role.allowedAgentKeys();
        Set<String> existingTools = role.allowedToolKeys() == null ? Set.of() : role.allowedToolKeys();
        Set<String> agents = union(existingAgents, WEEK_21_AGENTS);
        Set<String> tools = union(existingTools, WEEK_21_TOOLS);
        if (agents.equals(existingAgents) && tools.equals(existingTools)) {
            return;
        }
        rolePort.save(new PlatformRole(
                role.roleKey(), role.displayName(), role.admin(),
                agents, tools, role.allowedPatientIds(), role.tenantKey()));
        log.info("[platform] rbac permissions upgraded tenantKey={} roleKey={} agents={} tools={}",
                role.tenantKey(), role.roleKey(), agents, tools);
    }

    private Set<String> union(Set<String> existing, Set<String> additions) {
        Set<String> merged = new LinkedHashSet<>(existing == null ? Set.of() : existing);
        merged.addAll(additions);
        return Set.copyOf(merged);
    }
}
