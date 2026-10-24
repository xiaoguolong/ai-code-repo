package com.aicode.framework.platform.controller;

import cn.dev33.satoken.stp.StpUtil;
import com.aicode.framework.dto.ApiResponse;
import com.aicode.framework.platform.application.PlatformAuthenticationFacade;
import com.aicode.framework.platform.application.PlatformLoginCommand;
import com.aicode.framework.platform.application.PlatformLoginOutcome;
import com.aicode.framework.platform.application.PlatformMeView;
import com.aicode.framework.platform.application.PlatformAuthUseCase;
import com.aicode.framework.platform.domain.model.PlatformUser;
import com.aicode.framework.platform.dto.PlatformAuthResponse;
import com.aicode.framework.platform.dto.PlatformLoginRequest;
import com.aicode.framework.platform.dto.PlatformMeResponse;
import com.aicode.framework.platform.dto.PlatformVerifyResponse;
import com.aicode.framework.platform.infrastructure.security.ClientIpResolver;
import jakarta.servlet.http.HttpServletRequest;
import jakarta.validation.Valid;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;

/**
 * 平台认证 API。
 *
 * <p>Week 22：登录与身份校验都经 {@link PlatformAuthenticationFacade}（先限流后认证）；
 * 控制器只负责解析客户端 IP，不参与限流判断。</p>
 */
@RestController
@RequestMapping("/api/v1/platform/auth")
public class PlatformAuthController {

    private final PlatformAuthenticationFacade authenticationFacade;
    private final PlatformAuthUseCase platformAuthUseCase;
    private final boolean trustForwardedFor;

    public PlatformAuthController(
            PlatformAuthenticationFacade authenticationFacade,
            PlatformAuthUseCase platformAuthUseCase,
            @Value("${platform.security.rate-limit.trust-forwarded-for:false}") boolean trustForwardedFor
    ) {
        this.authenticationFacade = authenticationFacade;
        this.platformAuthUseCase = platformAuthUseCase;
        this.trustForwardedFor = trustForwardedFor;
    }

    /**
     * 登录（建会话）。
     *
     * @param request 用户名 + 密码
     * @param servletRequest 用于解析客户端 IP（限流 key 的一部分）
     * @return 令牌、用户、角色与租户；凭据错 401，尝试过频 429
     */
    @PostMapping("/login")
    public ApiResponse<PlatformAuthResponse> login(
            @Valid @RequestBody PlatformLoginRequest request,
            HttpServletRequest servletRequest
    ) {
        PlatformLoginOutcome outcome = authenticationFacade.login(
                new PlatformLoginCommand(request.username(), request.password()),
                ClientIpResolver.resolve(servletRequest, trustForwardedFor));
        return ApiResponse.success(new PlatformAuthResponse(
                outcome.token(), outcome.userId(), outcome.username(), outcome.roleKey(), outcome.tenantKey()));
    }

    /**
     * 身份校验（Week 21 SSO 身份源）：只校验凭据，**不建会话**、不返回 token。
     *
     * @param request 用户名 + 密码
     * @param servletRequest 用于解析客户端 IP
     * @return 平台身份（用户、角色、租户）；凭据错 401，尝试过频 429
     */
    @PostMapping("/verify")
    public ApiResponse<PlatformVerifyResponse> verify(
            @Valid @RequestBody PlatformLoginRequest request,
            HttpServletRequest servletRequest
    ) {
        PlatformUser user = authenticationFacade.verify(
                new PlatformLoginCommand(request.username(), request.password()),
                ClientIpResolver.resolve(servletRequest, trustForwardedFor));
        return ApiResponse.success(PlatformVerifyResponse.from(user));
    }

    /**
     * 注销当前登录态。
     *
     * @return 空数据
     */
    @PostMapping("/logout")
    public ApiResponse<Void> logout() {
        StpUtil.checkLogin();
        platformAuthUseCase.logout();
        return ApiResponse.success(null);
    }

    /**
     * 当前登录用户（含租户）。
     *
     * @return 用户视图；未登录 401
     */
    @GetMapping("/me")
    public ApiResponse<PlatformMeResponse> me() {
        StpUtil.checkLogin();
        PlatformMeView view = platformAuthUseCase.me(StpUtil.getLoginIdAsLong());
        return ApiResponse.success(PlatformMeResponse.from(view));
    }
}
