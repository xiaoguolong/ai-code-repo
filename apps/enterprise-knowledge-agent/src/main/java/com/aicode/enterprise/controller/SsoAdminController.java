package com.aicode.enterprise.controller;

import com.aicode.enterprise.application.SsoAdminUseCase;
import com.aicode.enterprise.domain.model.SsoUserLink;
import com.aicode.enterprise.dto.ApiResponse;
import com.aicode.enterprise.dto.SsoLinkListResponse;
import com.aicode.enterprise.dto.SsoLinkRequest;
import com.aicode.enterprise.dto.SsoLinkResponse;
import com.aicode.enterprise.dto.SsoUnlinkResponse;
import com.aicode.enterprise.infrastructure.sso.AdminTokenGuard;
import jakarta.validation.Valid;
import org.springframework.web.bind.annotation.DeleteMapping;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestHeader;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RequestParam;
import org.springframework.web.bind.annotation.RestController;

import java.util.List;

/**
 * SSO 映射运维 API（Week 23，A1 运维令牌）。
 *
 * <p>不在 Sa-Token 登录体系内：只认 {@code X-Admin-Token}，且端点只在回环监听的
 * 8081 上暴露；令牌未配置时一律 403（fail closed）。</p>
 */
@RestController
@RequestMapping("/api/v1/admin/sso-links")
public class SsoAdminController {

    private final SsoAdminUseCase useCase;
    private final AdminTokenGuard tokenGuard;

    public SsoAdminController(SsoAdminUseCase useCase, AdminTokenGuard tokenGuard) {
        this.useCase = useCase;
        this.tokenGuard = tokenGuard;
    }

    /**
     * 列出 SSO 映射。
     *
     * @param token 运维令牌
     * @param limit 条数上限，默认 50、上限 200
     * @return 映射列表
     */
    @GetMapping
    public ApiResponse<SsoLinkListResponse> list(
            @RequestHeader(value = "X-Admin-Token", required = false) String token,
            @RequestParam(defaultValue = "50") int limit
    ) {
        tokenGuard.verify(token);
        List<SsoUserLink> links = useCase.list(limit);
        return ApiResponse.success(SsoLinkListResponse.from(links));
    }

    /**
     * 建立/重建 SSO 映射。
     *
     * @param token   运维令牌
     * @param request 平台用户 + 本地用户
     * @return 建链后的映射
     */
    @PostMapping
    public ApiResponse<SsoLinkResponse> link(
            @RequestHeader(value = "X-Admin-Token", required = false) String token,
            @Valid @RequestBody SsoLinkRequest request
    ) {
        tokenGuard.verify(token);
        SsoUserLink link = useCase.link(
                request.platformUserId(), request.enterpriseUserId(), request.tenantKey());
        return ApiResponse.success(SsoLinkResponse.from(link));
    }

    /**
     * 解绑 SSO 映射。
     *
     * @param token           运维令牌
     * @param platformUserId  平台用户主键
     * @param deleteLocalUser true = 同时删除本地用户（避免下次登录 409 死结）
     * @return 解绑结果
     */
    @DeleteMapping("/{platformUserId}")
    public ApiResponse<SsoUnlinkResponse> unlink(
            @RequestHeader(value = "X-Admin-Token", required = false) String token,
            @PathVariable long platformUserId,
            @RequestParam(defaultValue = "false") boolean deleteLocalUser
    ) {
        tokenGuard.verify(token);
        SsoAdminUseCase.SsoUnlinkResult result = useCase.unlink(platformUserId, deleteLocalUser);
        return ApiResponse.success(SsoUnlinkResponse.from(result));
    }
}
