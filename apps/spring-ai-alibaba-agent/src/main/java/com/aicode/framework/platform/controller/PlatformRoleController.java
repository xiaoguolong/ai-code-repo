package com.aicode.framework.platform.controller;

import cn.dev33.satoken.stp.StpUtil;
import com.aicode.framework.dto.ApiResponse;
import com.aicode.framework.platform.application.PlatformRoleUseCase;
import com.aicode.framework.platform.dto.PlatformRoleResponse;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RequestParam;
import org.springframework.web.bind.annotation.RestController;

import java.util.List;

/**
 * 平台角色查询 API（Week 23：可按租户过滤）。
 */
@RestController
@RequestMapping("/api/v1/platform/roles")
public class PlatformRoleController {

    private final PlatformRoleUseCase platformRoleUseCase;

    public PlatformRoleController(PlatformRoleUseCase platformRoleUseCase) {
        this.platformRoleUseCase = platformRoleUseCase;
    }

    /**
     * 列出角色。
     *
     * @param tenantKey 租户键，可空（空则列全部）
     * @return 角色列表
     */
    @GetMapping
    public ApiResponse<List<PlatformRoleResponse>> listRoles(
            @RequestParam(required = false) String tenantKey
    ) {
        StpUtil.checkLogin();
        List<PlatformRoleResponse> roles = platformRoleUseCase.listRoles(tenantKey).stream()
                .map(PlatformRoleResponse::from)
                .toList();
        return ApiResponse.success(roles);
    }

    /**
     * 查询角色。
     *
     * @param roleKey   角色键
     * @param tenantKey 租户键，可空（空则 default）
     * @return 角色
     */
    @GetMapping("/{roleKey}")
    public ApiResponse<PlatformRoleResponse> getRole(
            @PathVariable String roleKey,
            @RequestParam(required = false) String tenantKey
    ) {
        StpUtil.checkLogin();
        return ApiResponse.success(PlatformRoleResponse.from(
                platformRoleUseCase.getRole(roleKey, tenantKey)));
    }
}
