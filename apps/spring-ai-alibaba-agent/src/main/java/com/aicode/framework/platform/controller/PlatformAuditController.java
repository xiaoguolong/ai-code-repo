package com.aicode.framework.platform.controller;

import cn.dev33.satoken.stp.StpUtil;
import com.aicode.framework.dto.ApiResponse;
import com.aicode.framework.platform.application.PlatformAuditUseCase;
import com.aicode.framework.platform.dto.AuditLogResponse;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;

import java.util.List;

/**
 * 平台审计日志查询 API（Week 16）。
 *
 * <p>{@code GET /api/v1/platform/audit-logs}：需登录；admin 返回全部，其他角色只返回本人记录。</p>
 */
@RestController
@RequestMapping("/api/v1/platform/audit-logs")
public class PlatformAuditController {

    private final PlatformAuditUseCase platformAuditUseCase;

    public PlatformAuditController(PlatformAuditUseCase platformAuditUseCase) {
        this.platformAuditUseCase = platformAuditUseCase;
    }

    @GetMapping
    public ApiResponse<List<AuditLogResponse>> listAuditLogs() {
        StpUtil.checkLogin();
        long userId = StpUtil.getLoginIdAsLong();
        List<AuditLogResponse> logs = platformAuditUseCase.listAuditLogs(userId).stream()
                .map(AuditLogResponse::from)
                .toList();
        return ApiResponse.success(logs);
    }
}
