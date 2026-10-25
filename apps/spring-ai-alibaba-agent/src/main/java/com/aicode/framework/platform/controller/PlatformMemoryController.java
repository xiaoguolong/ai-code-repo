package com.aicode.framework.platform.controller;

import cn.dev33.satoken.stp.StpUtil;
import com.aicode.framework.dto.ApiResponse;
import com.aicode.framework.platform.application.MemoryPurgeResult;
import com.aicode.framework.platform.application.MemoryRebuildResult;
import com.aicode.framework.platform.application.PlatformMemoryMaintenanceUseCase;
import com.aicode.framework.platform.application.PlatformMemoryUseCase;
import com.aicode.framework.platform.domain.model.PlatformMemoryHit;
import com.aicode.framework.platform.domain.model.PlatformMemoryScope;
import com.aicode.framework.platform.dto.PlatformMemoryDeleteResponse;
import com.aicode.framework.platform.dto.PlatformMemoryPurgeResponse;
import com.aicode.framework.platform.dto.PlatformMemoryRebuildResponse;
import com.aicode.framework.platform.dto.PlatformMemoryResponse;
import org.springframework.web.bind.annotation.DeleteMapping;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RequestParam;
import org.springframework.web.bind.annotation.RestController;

import java.util.List;

/**
 * 长期记忆 API（Week 21 查询；Week 23 增加删除 / 过期清理 / 向量重建）。
 *
 * <p>默认只返回当前用户的记忆；`userId` 参数仅管理员可用（越权由用例层拒绝）。
 * 请求里**没有** tenant 参数——租户由服务端从用户记录推导。</p>
 */
@RestController
@RequestMapping("/api/v1/platform/memories")
public class PlatformMemoryController {

    private final PlatformMemoryUseCase platformMemoryUseCase;
    private final PlatformMemoryMaintenanceUseCase maintenanceUseCase;

    public PlatformMemoryController(
            PlatformMemoryUseCase platformMemoryUseCase,
            PlatformMemoryMaintenanceUseCase maintenanceUseCase
    ) {
        this.platformMemoryUseCase = platformMemoryUseCase;
        this.maintenanceUseCase = maintenanceUseCase;
    }

    /**
     * 查询记忆。
     *
     * @param query     语义检索文本，可空（空则按时间倒序）
     * @param patientId 按患者会话过滤，可空
     * @param userId    目标用户，仅管理员可传
     * @param limit     条数上限，默认 5（上限 20）
     * @return 记忆列表（scope 回显，不含 embedding）
     */
    @GetMapping
    public ApiResponse<PlatformMemoryResponse> list(
            @RequestParam(required = false) String query,
            @RequestParam(required = false) String patientId,
            @RequestParam(required = false) Long userId,
            @RequestParam(defaultValue = "5") int limit
    ) {
        StpUtil.checkLogin();
        long callerUserId = StpUtil.getLoginIdAsLong();
        PlatformMemoryScope scope = platformMemoryUseCase.resolveScope(callerUserId, userId);
        List<PlatformMemoryHit> hits = platformMemoryUseCase.list(scope, patientId, query, limit);
        return ApiResponse.success(PlatformMemoryResponse.from(scope.tenantKey(), scope.userId(), hits));
    }

    /**
     * 按范围删除一条记忆。
     *
     * @param memoryId 记忆 ID
     * @param userId   目标用户，仅管理员可传
     * @return 删除结果；不存在或不属于该范围 404
     */
    @DeleteMapping("/{memoryId}")
    public ApiResponse<PlatformMemoryDeleteResponse> delete(
            @PathVariable String memoryId,
            @RequestParam(required = false) Long userId
    ) {
        StpUtil.checkLogin();
        long callerUserId = StpUtil.getLoginIdAsLong();
        maintenanceUseCase.delete(callerUserId, userId, memoryId);
        return ApiResponse.success(new PlatformMemoryDeleteResponse(memoryId, 1));
    }

    /**
     * 清理过期记忆（仅管理员）。
     *
     * @param limit 单次最多删除条数，默认 100、上限 500
     * @return 实际删除条数与生效上限
     */
    @PostMapping("/purge-expired")
    public ApiResponse<PlatformMemoryPurgeResponse> purgeExpired(
            @RequestParam(defaultValue = "100") int limit
    ) {
        StpUtil.checkLogin();
        long callerUserId = StpUtil.getLoginIdAsLong();
        MemoryPurgeResult result =
                maintenanceUseCase.purgeExpired(callerUserId, limit);
        return ApiResponse.success(new PlatformMemoryPurgeResponse(result.deleted(), result.limit()));
    }

    /**
     * 重建缺失/旧模型向量（仅管理员）。
     *
     * @param limit 单批条数，默认 100、上限 500
     * @return 扫描/重建/失败计数与当前模型标识
     */
    @PostMapping("/rebuild-embeddings")
    public ApiResponse<PlatformMemoryRebuildResponse> rebuildEmbeddings(
            @RequestParam(defaultValue = "100") int limit
    ) {
        StpUtil.checkLogin();
        long callerUserId = StpUtil.getLoginIdAsLong();
        MemoryRebuildResult result =
                maintenanceUseCase.rebuildEmbeddings(callerUserId, limit);
        return ApiResponse.success(new PlatformMemoryRebuildResponse(
                result.scanned(), result.rebuilt(), result.failed(), result.currentModel()));
    }
}
