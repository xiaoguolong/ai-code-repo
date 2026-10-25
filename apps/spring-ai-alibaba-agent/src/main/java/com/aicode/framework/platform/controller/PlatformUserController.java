package com.aicode.framework.platform.controller;

import cn.dev33.satoken.stp.StpUtil;
import com.aicode.framework.dto.ApiResponse;
import com.aicode.framework.platform.application.PlatformUserAdminUseCase;
import com.aicode.framework.platform.dto.PlatformUserListResponse;
import com.aicode.framework.platform.dto.PlatformUserResponse;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;

/**
 * 平台用户管理 API（Week 28，TD-06）。
 *
 * <p>仅 admin：列表 / 停用 / 恢复。停用不删行，历史执行与审计保留；
 * 停用会踢出该用户全部平台会话，恢复后需重新登录。</p>
 */
@RestController
@RequestMapping("/api/v1/platform/users")
public class PlatformUserController {

    private final PlatformUserAdminUseCase userAdminUseCase;

    public PlatformUserController(PlatformUserAdminUseCase userAdminUseCase) {
        this.userAdminUseCase = userAdminUseCase;
    }

    /**
     * 列出平台用户（仅 admin）。
     *
     * @return 用户列表（含停用状态）
     */
    @GetMapping
    public ApiResponse<PlatformUserListResponse> list() {
        StpUtil.checkLogin();
        long callerUserId = StpUtil.getLoginIdAsLong();
        return ApiResponse.success(PlatformUserListResponse.from(userAdminUseCase.list(callerUserId)));
    }

    /**
     * 停用用户（仅 admin）。
     *
     * @param userId 目标用户 ID
     * @return 停用后的用户
     */
    @PostMapping("/{userId}/disable")
    public ApiResponse<PlatformUserResponse> disable(@PathVariable long userId) {
        StpUtil.checkLogin();
        long callerUserId = StpUtil.getLoginIdAsLong();
        return ApiResponse.success(
                PlatformUserResponse.from(userAdminUseCase.disable(callerUserId, userId)));
    }

    /**
     * 恢复用户（仅 admin）。
     *
     * @param userId 目标用户 ID
     * @return 恢复后的用户
     */
    @PostMapping("/{userId}/enable")
    public ApiResponse<PlatformUserResponse> enable(@PathVariable long userId) {
        StpUtil.checkLogin();
        long callerUserId = StpUtil.getLoginIdAsLong();
        return ApiResponse.success(
                PlatformUserResponse.from(userAdminUseCase.enable(callerUserId, userId)));
    }
}
