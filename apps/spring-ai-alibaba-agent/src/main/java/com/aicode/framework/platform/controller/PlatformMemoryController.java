package com.aicode.framework.platform.controller;

import cn.dev33.satoken.stp.StpUtil;
import com.aicode.framework.dto.ApiResponse;
import com.aicode.framework.platform.application.PlatformMemoryUseCase;
import com.aicode.framework.platform.domain.model.PlatformMemoryHit;
import com.aicode.framework.platform.domain.model.PlatformMemoryScope;
import com.aicode.framework.platform.dto.PlatformMemoryResponse;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RequestParam;
import org.springframework.web.bind.annotation.RestController;

import java.util.List;

/**
 * 长期记忆查询 API（Week 21）。
 *
 * <p>默认只返回当前用户的记忆；`userId` 参数仅管理员可用（越权由用例层拒绝）。
 * 请求里**没有** tenant 参数——租户由服务端从用户记录推导。</p>
 */
@RestController
@RequestMapping("/api/v1/platform/memories")
public class PlatformMemoryController {

    private final PlatformMemoryUseCase platformMemoryUseCase;

    public PlatformMemoryController(PlatformMemoryUseCase platformMemoryUseCase) {
        this.platformMemoryUseCase = platformMemoryUseCase;
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
}
