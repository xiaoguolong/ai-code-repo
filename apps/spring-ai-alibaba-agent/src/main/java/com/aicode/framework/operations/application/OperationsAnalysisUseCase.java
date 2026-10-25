package com.aicode.framework.operations.application;

import com.aicode.core.domain.exception.InvalidChatRequestException;
import com.aicode.framework.operations.domain.model.OperationsAnalysisRequest;
import com.aicode.framework.operations.domain.model.OperationsDailyReport;
import com.aicode.framework.operations.infrastructure.OperationsAnalysisAgent;
import com.aicode.framework.platform.domain.model.ApprovalStatus;
import com.aicode.framework.platform.domain.model.ExecutionRecord;
import com.aicode.framework.platform.domain.port.ExecutionRecordPort;
import com.aicode.framework.platform.domain.port.PlatformApprovalPort;
import com.aicode.framework.platform.domain.service.PlatformPermissionChecker;
import com.aicode.framework.platform.infrastructure.config.PlatformOperationsProperties;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.stereotype.Service;

import java.time.Clock;
import java.time.Instant;
import java.time.temporal.ChronoUnit;
import java.util.List;

/**
 * 运营分析用例（Week 24，Agent 2）：解析窗口 → 取数 → 交给 Agent 统计与生成日报。
 *
 * <p>可见性（写死并单测）：</p>
 * <ul>
 *   <li>admin 统计**整个租户**；非 admin 只统计**自己的**执行记录；</li>
 *   <li>租户键只从用户记录推导（{@link PlatformPermissionChecker#tenantKeyOf}），不接受客户端传入；</li>
 *   <li>窗口只接受天数（1–30），不接受客户端传任意时间点，避免查全表与跨租户猜时间。</li>
 * </ul>
 *
 * <p>取数上限 {@code platform.operations.max-records}（默认 5000）：超出即截断并在日报里标注，
 * 这是演示库的 YAGNI 取舍，数据量上来换 SQL 聚合。</p>
 */
@Service
public class OperationsAnalysisUseCase {

    private static final Logger log = LoggerFactory.getLogger(OperationsAnalysisUseCase.class);

    private final ExecutionRecordPort executionRecordPort;
    private final PlatformApprovalPort approvalPort;
    private final PlatformPermissionChecker permissionChecker;
    private final OperationsAnalysisAgent agent;
    private final PlatformOperationsProperties properties;
    private final Clock clock;

    public OperationsAnalysisUseCase(
            ExecutionRecordPort executionRecordPort,
            PlatformApprovalPort approvalPort,
            PlatformPermissionChecker permissionChecker,
            OperationsAnalysisAgent agent,
            PlatformOperationsProperties properties,
            Clock clock
    ) {
        this.executionRecordPort = executionRecordPort;
        this.approvalPort = approvalPort;
        this.permissionChecker = permissionChecker;
        this.agent = agent;
        this.properties = properties == null ? new PlatformOperationsProperties() : properties;
        this.clock = clock == null ? Clock.systemUTC() : clock;
    }

    /**
     * 生成运营日报。
     *
     * @param userId     调用者（来自登录态）
     * @param windowDays 统计窗口天数；null 用默认值，越界 400
     * @return 日报
     * @throws InvalidChatRequestException 窗口天数越界
     */
    public OperationsDailyReport run(long userId, Integer windowDays) {
        int days = resolveWindowDays(windowDays);
        Instant end = Instant.now(clock).truncatedTo(ChronoUnit.HOURS);
        Instant start = end.minus(days, ChronoUnit.DAYS);
        Instant previousStart = start.minus(days, ChronoUnit.DAYS);

        boolean admin = permissionChecker.isAdmin(userId);
        String tenantKey = permissionChecker.tenantKeyOf(userId);
        int limit = properties.resolvedMaxRecords();

        List<ExecutionRecord> current = admin
                ? executionRecordPort.listByTenantBetween(tenantKey, start, end, limit)
                : executionRecordPort.listByUserBetween(userId, start, end, limit);
        List<ExecutionRecord> previous = admin
                ? executionRecordPort.listByTenantBetween(tenantKey, previousStart, start, limit)
                : executionRecordPort.listByUserBetween(userId, previousStart, start, limit);
        boolean truncated = current.size() >= limit;
        long pendingApprovals = admin
                ? approvalPort.countAll(ApprovalStatus.PENDING)
                : approvalPort.countByUser(userId, ApprovalStatus.PENDING);

        log.info("[operations-analysis] window userId={} tenantKey={} admin={} windowDays={} "
                        + "current={} previous={} pendingApprovals={} truncated={}",
                userId, tenantKey, admin, days, current.size(), previous.size(), pendingApprovals, truncated);

        return agent.run(
                new OperationsAnalysisRequest(start, end, days, current, previous, pendingApprovals, truncated),
                properties.thresholds());
    }

    private int resolveWindowDays(Integer windowDays) {
        int max = properties.resolvedMaxWindowDays();
        if (windowDays == null) {
            return properties.resolvedDefaultWindowDays();
        }
        if (windowDays < 1 || windowDays > max) {
            throw new InvalidChatRequestException("windowDays must be between 1 and " + max);
        }
        return windowDays;
    }
}
