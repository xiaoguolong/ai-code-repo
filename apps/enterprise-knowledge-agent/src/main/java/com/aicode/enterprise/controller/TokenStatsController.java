package com.aicode.enterprise.controller;

import cn.dev33.satoken.stp.StpUtil;
import com.aicode.enterprise.application.TokenStatsUseCase;
import com.aicode.enterprise.domain.model.TokenStats;
import com.aicode.enterprise.dto.ApiResponse;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;

/**
 * Token 统计接入。只委托用例，不直接访问审计端口。
 */
@RestController
@RequestMapping("/api/v1/token-stats")
public class TokenStatsController {

    private final TokenStatsUseCase tokenStatsUseCase;

    public TokenStatsController(TokenStatsUseCase tokenStatsUseCase) {
        this.tokenStatsUseCase = tokenStatsUseCase;
    }

    /**
     * @return 200，data 为当前用户累计请求数与 Token
     */
    @GetMapping
    public ApiResponse<TokenStats> stats() {
        Long userId = StpUtil.getLoginIdAsLong();
        return ApiResponse.success(tokenStatsUseCase.summary(userId));
    }
}
