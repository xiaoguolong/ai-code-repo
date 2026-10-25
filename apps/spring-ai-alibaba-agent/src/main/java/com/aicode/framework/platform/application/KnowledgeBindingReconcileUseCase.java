package com.aicode.framework.platform.application;

import com.aicode.framework.infrastructure.logging.TraceIds;
import com.aicode.framework.knowledge.domain.model.KnowledgeBaseCatalog;
import com.aicode.framework.knowledge.domain.port.KnowledgeSearchPort;
import com.aicode.framework.observability.domain.AgentObservabilityPort;
import com.aicode.framework.platform.domain.exception.PlatformAccessDeniedException;
import com.aicode.framework.platform.domain.model.AuditAction;
import com.aicode.framework.platform.domain.model.AuditLogEntry;
import com.aicode.framework.platform.domain.model.AuditResult;
import com.aicode.framework.platform.domain.model.KnowledgeBinding;
import com.aicode.framework.platform.domain.port.AuditLogPort;
import com.aicode.framework.platform.domain.port.KnowledgeBindingPort;
import com.aicode.framework.platform.domain.service.PlatformPermissionChecker;
import com.aicode.framework.platform.infrastructure.config.PlatformKnowledgeProperties;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.stereotype.Service;

import java.time.Clock;
import java.time.Instant;
import java.util.ArrayList;
import java.util.HashMap;
import java.util.HashSet;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.Set;

/**
 * 知识库绑定对账用例（Week 27）。
 *
 * <p>把平台绑定与企业侧清单按「租户 + 企业归属人」分组比对：改名/改描述 → 同步；
 * 企业侧删除 → 标记 {@code MISSING}（不删行）；企业侧新增 → 只计 {@code unbound}，
 * 是否纳入租户可见范围仍由 admin 显式导入决定。</p>
 *
 * <p><b>失败不改状态</b>：企业清单调用失败时该组只计 {@code failed}，绑定保持对账前的
 * 状态与 {@code lastSyncedAt}——上游一次抖动不能让线上知识库全部变成「已删除」。</p>
 */
@Service
public class KnowledgeBindingReconcileUseCase {

    private static final Logger log = LoggerFactory.getLogger(KnowledgeBindingReconcileUseCase.class);

    private final KnowledgeBindingPort bindingPort;
    private final KnowledgeSearchPort searchPort;
    private final PlatformPermissionChecker permissionChecker;
    private final AuditLogPort auditLogPort;
    private final PlatformKnowledgeProperties properties;
    private final Clock clock;
    private final KnowledgeBindingGroupReconciler groupReconciler;
    private final KnowledgeReconcileReporter reporter;

    public KnowledgeBindingReconcileUseCase(
            KnowledgeBindingPort bindingPort,
            KnowledgeSearchPort searchPort,
            PlatformPermissionChecker permissionChecker,
            AuditLogPort auditLogPort,
            AgentObservabilityPort observability,
            PlatformKnowledgeProperties properties,
            Clock clock
    ) {
        this.bindingPort = bindingPort;
        this.searchPort = searchPort;
        this.permissionChecker = permissionChecker;
        this.auditLogPort = auditLogPort;
        this.properties = properties == null ? new PlatformKnowledgeProperties() : properties;
        this.clock = clock == null ? Clock.systemUTC() : clock;
        this.groupReconciler = new KnowledgeBindingGroupReconciler(bindingPort);
        this.reporter = new KnowledgeReconcileReporter(bindingPort, observability);
    }

    /**
     * 手动对账（仅 admin，只对调用者所在租户）。
     *
     * @param callerUserId 管理员
     * @return 对账结果
     * @throws PlatformAccessDeniedException 非 admin
     */
    public KnowledgeReconcileResult reconcileTenant(long callerUserId) {
        if (!permissionChecker.isAdmin(callerUserId)) {
            throw new PlatformAccessDeniedException("admin role is required for knowledge binding reconcile");
        }
        String tenantKey = permissionChecker.tenantKeyOf(callerUserId);
        Instant now = clock.instant();
        KnowledgeReconcileResult result = reconcile(bindingPort.listByTenant(tenantKey), now, tenantKey);
        audit(callerUserId, result);
        log.info("[knowledge] manual reconcile tenantKey={} by={} groups={} checked={} updated={} missing={} failed={}",
                tenantKey, callerUserId, result.groups(), result.checked(),
                result.updated(), result.missing(), result.failed());
        return result;
    }

    /**
     * 定时对账（全部租户）。
     *
     * <p>不写审计：每日一条系统事件没有追溯价值，日志 + 指标已能回答「对账是否在跑、结果如何」。</p>
     *
     * @return 对账结果；对账关闭时返回空结果
     */
    public KnowledgeReconcileResult reconcileAll() {
        Instant now = clock.instant();
        if (!properties.isEnabled()) {
            log.debug("[knowledge] reconcile skipped: knowledge capability disabled");
            return KnowledgeReconcileResult.empty("", now);
        }
        KnowledgeReconcileResult result = reconcile(bindingPort.listAll(), now, "");
        log.info("[knowledge] scheduled reconcile groups={} checked={} updated={} missing={} unchanged={} failed={} unbound={}",
                result.groups(), result.checked(), result.updated(), result.missing(),
                result.unchanged(), result.failed(), result.unbound());
        return result;
    }

    private KnowledgeReconcileResult reconcile(
            List<KnowledgeBinding> all,
            Instant now,
            String tenantKey
    ) {
        if (all == null || all.isEmpty()) {
            reporter.reportMissingGauge();
            return KnowledgeReconcileResult.empty(tenantKey, now);
        }
        int limit = properties.resolvedReconcileBatchLimit();
        List<KnowledgeBinding> targets = all.size() > limit
                ? new ArrayList<>(all.subList(0, limit)) : all;
        if (all.size() > limit) {
            log.warn("[knowledge] reconcile truncated total={} batchLimit={}", all.size(), limit);
        }
        Map<String, Set<Long>> boundIdsByTenant = boundIdsByTenant(all);
        Map<GroupKey, List<KnowledgeBinding>> groups = groupByOwner(targets);

        int updated = 0;
        int missing = 0;
        int unchanged = 0;
        int failed = 0;
        int unbound = 0;
        for (Map.Entry<GroupKey, List<KnowledgeBinding>> entry : groups.entrySet()) {
            GroupKey key = entry.getKey();
            List<KnowledgeBinding> group = entry.getValue();
            KnowledgeBaseCatalog catalog;
            try {
                catalog = searchPort.listBasesByEnterpriseUser(key.enterpriseUserId());
            } catch (RuntimeException ex) {
                failed += group.size();
                log.warn("[knowledge] reconcile group failed tenantKey={} enterpriseUserId={} bindings={} error={}",
                        key.tenantKey(), key.enterpriseUserId(), group.size(), ex.getMessage());
                continue;
            }
            KnowledgeBindingGroupReconciler.GroupOutcome outcome = groupReconciler.apply(
                    group, catalog, boundIdsByTenant.getOrDefault(key.tenantKey(), Set.of()), now);
            updated += outcome.updated();
            missing += outcome.missing();
            unchanged += outcome.unchanged();
            unbound += outcome.unbound();
        }
        reporter.reportCounters(updated, missing, unchanged, failed);
        reporter.reportMissingGauge();
        return new KnowledgeReconcileResult(
                tenantKey, now, groups.size(),
                updated + missing + unchanged + failed,
                updated, missing, unchanged, failed, unbound);
    }

    private Map<GroupKey, List<KnowledgeBinding>> groupByOwner(List<KnowledgeBinding> bindings) {
        Map<GroupKey, List<KnowledgeBinding>> groups = new LinkedHashMap<>();
        for (KnowledgeBinding binding : bindings) {
            GroupKey key = new GroupKey(binding.tenantKey(), binding.enterpriseUserId());
            groups.computeIfAbsent(key, ignored -> new ArrayList<>()).add(binding);
        }
        return groups;
    }

    private Map<String, Set<Long>> boundIdsByTenant(List<KnowledgeBinding> bindings) {
        Map<String, Set<Long>> result = new HashMap<>();
        for (KnowledgeBinding binding : bindings) {
            result.computeIfAbsent(binding.tenantKey(), ignored -> new HashSet<>())
                    .add(binding.knowledgeBaseId());
        }
        return result;
    }

    private void audit(long userId, KnowledgeReconcileResult result) {
        try {
            auditLogPort.record(AuditLogEntry.of(
                    userId, AuditAction.KNOWLEDGE_RECONCILE, "knowledge-binding:reconcile",
                    AuditResult.SUCCESS, TraceIds.current(),
                    "groups=" + result.groups() + " checked=" + result.checked()
                            + " updated=" + result.updated() + " missing=" + result.missing()
                            + " failed=" + result.failed() + " unbound=" + result.unbound()));
        } catch (RuntimeException ex) {
            log.warn("[knowledge] reconcile audit failed userId={} error={}", userId, ex.getMessage());
        }
    }

    /**
     * 对账分组键：同一租户 + 同一企业归属人只调用一次企业清单。
     *
     * @param tenantKey        租户
     * @param enterpriseUserId 企业本地用户
     */
    private record GroupKey(String tenantKey, long enterpriseUserId) {
    }
}
