package com.aicode.framework.platform.controller;

import cn.dev33.satoken.stp.StpUtil;
import com.aicode.framework.dto.ApiResponse;
import com.aicode.framework.platform.application.PlatformTenantUseCase;
import com.aicode.framework.platform.domain.model.PlatformTenant;
import com.aicode.framework.platform.dto.PlatformTenantResponse;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;

import java.util.List;

/**
 * 租户 API（Week 21）。
 *
 * <p>只有管理员可看：租户列表会暴露「平台上有几家机构」，属内部拓扑信息。</p>
 */
@RestController
@RequestMapping("/api/v1/platform/tenants")
public class PlatformTenantController {

    private final PlatformTenantUseCase platformTenantUseCase;

    public PlatformTenantController(PlatformTenantUseCase platformTenantUseCase) {
        this.platformTenantUseCase = platformTenantUseCase;
    }

    /**
     * 列出全部租户。
     *
     * @return 租户列表；未登录 401，非管理员 403
     */
    @GetMapping
    public ApiResponse<List<PlatformTenantResponse>> listTenants() {
        StpUtil.checkLogin();
        long userId = StpUtil.getLoginIdAsLong();
        List<PlatformTenant> tenants = platformTenantUseCase.listTenants(userId);
        return ApiResponse.success(tenants.stream().map(PlatformTenantResponse::from).toList());
    }
}
