package com.aicode.framework.platform.controller;

import cn.dev33.satoken.stp.StpUtil;
import com.aicode.framework.dto.ApiResponse;
import com.aicode.framework.platform.application.PlatformAuthUseCase;
import com.aicode.framework.platform.application.PlatformLoginCommand;
import com.aicode.framework.platform.application.PlatformLoginOutcome;
import com.aicode.framework.platform.domain.model.PlatformUser;
import com.aicode.framework.platform.dto.PlatformAuthResponse;
import com.aicode.framework.platform.dto.PlatformLoginRequest;
import com.aicode.framework.platform.dto.PlatformMeResponse;
import jakarta.validation.Valid;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;

/**
 * 平台认证 API。
 */
@RestController
@RequestMapping("/api/v1/platform/auth")
public class PlatformAuthController {

    private final PlatformAuthUseCase platformAuthUseCase;

    public PlatformAuthController(PlatformAuthUseCase platformAuthUseCase) {
        this.platformAuthUseCase = platformAuthUseCase;
    }

    @PostMapping("/login")
    public ApiResponse<PlatformAuthResponse> login(@Valid @RequestBody PlatformLoginRequest request) {
        PlatformLoginOutcome outcome = platformAuthUseCase.login(
                new PlatformLoginCommand(request.username(), request.password()));
        return ApiResponse.success(new PlatformAuthResponse(
                outcome.token(), outcome.userId(), outcome.username(), outcome.roleKey()));
    }

    @PostMapping("/logout")
    public ApiResponse<Void> logout() {
        StpUtil.checkLogin();
        platformAuthUseCase.logout();
        return ApiResponse.success(null);
    }

    @GetMapping("/me")
    public ApiResponse<PlatformMeResponse> me() {
        StpUtil.checkLogin();
        PlatformUser user = platformAuthUseCase.getUser(StpUtil.getLoginIdAsLong());
        return ApiResponse.success(PlatformMeResponse.from(user));
    }
}
