package com.aicode.framework.platform.controller;

import cn.dev33.satoken.stp.StpUtil;
import com.aicode.framework.dto.ApiResponse;
import com.aicode.framework.platform.application.PlatformRoleUseCase;
import com.aicode.framework.platform.dto.PlatformRoleResponse;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;

import java.util.List;

/**
 * 平台角色查询 API。
 */
@RestController
@RequestMapping("/api/v1/platform/roles")
public class PlatformRoleController {

    private final PlatformRoleUseCase platformRoleUseCase;

    public PlatformRoleController(PlatformRoleUseCase platformRoleUseCase) {
        this.platformRoleUseCase = platformRoleUseCase;
    }

    @GetMapping
    public ApiResponse<List<PlatformRoleResponse>> listRoles() {
        StpUtil.checkLogin();
        List<PlatformRoleResponse> roles = platformRoleUseCase.listRoles().stream()
                .map(PlatformRoleResponse::from)
                .toList();
        return ApiResponse.success(roles);
    }

    @GetMapping("/{roleKey}")
    public ApiResponse<PlatformRoleResponse> getRole(@PathVariable String roleKey) {
        StpUtil.checkLogin();
        return ApiResponse.success(PlatformRoleResponse.from(platformRoleUseCase.getRole(roleKey)));
    }
}
