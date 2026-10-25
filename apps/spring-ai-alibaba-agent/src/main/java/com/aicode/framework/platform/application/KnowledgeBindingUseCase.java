package com.aicode.framework.platform.application;

import com.aicode.framework.knowledge.domain.model.KnowledgeBaseCatalog;
import com.aicode.framework.knowledge.domain.model.KnowledgeBaseCatalogEntry;
import com.aicode.framework.knowledge.domain.port.KnowledgeSearchPort;
import com.aicode.framework.platform.domain.exception.PlatformAccessDeniedException;
import com.aicode.framework.platform.domain.exception.PlatformNotFoundException;
import com.aicode.framework.platform.domain.model.KnowledgeBinding;
import com.aicode.framework.platform.domain.model.KnowledgeBindingSyncStatus;
import com.aicode.framework.platform.domain.model.PlatformUser;
import com.aicode.framework.platform.domain.port.KnowledgeBindingPort;
import com.aicode.framework.platform.domain.port.PlatformUserPort;
import com.aicode.framework.platform.domain.service.PlatformPermissionChecker;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.stereotype.Service;

import java.time.Clock;
import java.time.Instant;
import java.util.List;
import java.util.UUID;

/**
 * 知识库绑定用例（Week 25）。
 *
 * <p>「导入」= 以管理员自己的 SSO 映射身份，把他在 enterprise 名下的知识库登记给本租户。
 * 这样不需要每个平台用户都有企业账号，也不会把别的租户的知识库带进来。</p>
 *
 * <p>权限：导入/删除仅 admin；列表所有登录用户可看（同租户）。</p>
 */
@Service
public class KnowledgeBindingUseCase {

    private static final Logger log = LoggerFactory.getLogger(KnowledgeBindingUseCase.class);

    private final KnowledgeBindingPort bindingPort;
    private final KnowledgeSearchPort searchPort;
    private final PlatformUserPort userPort;
    private final PlatformPermissionChecker permissionChecker;
    private final Clock clock;

    public KnowledgeBindingUseCase(
            KnowledgeBindingPort bindingPort,
            KnowledgeSearchPort searchPort,
            PlatformUserPort userPort,
            PlatformPermissionChecker permissionChecker,
            Clock clock
    ) {
        this.bindingPort = bindingPort;
        this.searchPort = searchPort;
        this.userPort = userPort;
        this.permissionChecker = permissionChecker;
        this.clock = clock == null ? Clock.systemUTC() : clock;
    }

    /**
     * 从 enterprise 导入当前管理员名下的知识库（幂等 upsert）。
     *
     * @param callerUserId 管理员
     * @return 导入结果
     * @throws PlatformAccessDeniedException 非 admin
     */
    public KnowledgeImportResult importFromEnterprise(long callerUserId) {
        requireAdmin(callerUserId);
        PlatformUser admin = requireUser(callerUserId);
        KnowledgeBaseCatalog catalog = searchPort.listBases(callerUserId);
        Instant now = clock.instant();
        int imported = 0;
        int updated = 0;
        for (KnowledgeBaseCatalogEntry entry : catalog.bases()) {
            KnowledgeBinding existing = bindingPort
                    .findByTenantAndKnowledgeBase(admin.tenantKey(), entry.knowledgeBaseId())
                    .orElse(null);
            if (existing == null) {
                bindingPort.save(new KnowledgeBinding(
                        UUID.randomUUID().toString(), admin.tenantKey(), catalog.enterpriseUserId(),
                        entry.knowledgeBaseId(), entry.name(), entry.description(), true,
                        callerUserId, now, now, KnowledgeBindingSyncStatus.ACTIVE, now));
                imported++;
            } else {
                // 导入本身就是一次「刚从企业侧读到」的成功对账：状态置 ACTIVE 并刷新 lastSyncedAt。
                bindingPort.save(existing.synced(entry.name(), entry.description(), now));
                updated++;
            }
        }
        List<KnowledgeBinding> bindings = bindingPort.listByTenant(admin.tenantKey());
        log.info("[knowledge] imported bindings tenantKey={} adminUserId={} enterpriseUserId={} imported={} updated={}",
                admin.tenantKey(), callerUserId, catalog.enterpriseUserId(), imported, updated);
        return new KnowledgeImportResult(
                admin.tenantKey(), catalog.enterpriseUserId(), imported, updated, bindings);
    }

    /**
     * 列出当前租户的绑定。
     *
     * @param callerUserId 调用者
     * @return 绑定列表
     */
    public List<KnowledgeBinding> list(long callerUserId) {
        requireUser(callerUserId);
        return bindingPort.listByTenant(permissionChecker.tenantKeyOf(callerUserId));
    }

    /**
     * 删除绑定（仅 admin，租户内）。
     *
     * @param callerUserId 管理员
     * @param bindingId    绑定 ID
     * @throws PlatformNotFoundException 不存在或不属于本租户
     */
    public void delete(long callerUserId, String bindingId) {
        requireAdmin(callerUserId);
        String tenantKey = permissionChecker.tenantKeyOf(callerUserId);
        KnowledgeBinding binding = bindingPort.findById(requireText(bindingId, "bindingId"))
                .filter(existing -> existing.tenantKey().equals(tenantKey))
                .orElseThrow(() -> new PlatformNotFoundException("knowledge binding not found: " + bindingId));
        if (!bindingPort.delete(tenantKey, binding.bindingId())) {
            throw new PlatformNotFoundException("knowledge binding not found: " + bindingId);
        }
        log.info("[knowledge] binding deleted tenantKey={} bindingId={} by={}",
                tenantKey, binding.bindingId(), callerUserId);
    }

    private void requireAdmin(long userId) {
        if (!permissionChecker.isAdmin(userId)) {
            throw new PlatformAccessDeniedException("admin role is required for knowledge bindings");
        }
    }

    private PlatformUser requireUser(long userId) {
        return userPort.findById(userId)
                .orElseThrow(() -> new PlatformNotFoundException("user not found: " + userId));
    }

    private String requireText(String value, String field) {
        String normalized = value == null ? "" : value.trim();
        if (normalized.isEmpty()) {
            throw new PlatformNotFoundException(field + " is required");
        }
        return normalized;
    }
}
