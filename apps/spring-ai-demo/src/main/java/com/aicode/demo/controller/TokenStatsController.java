package com.aicode.demo.controller;

import com.aicode.demo.application.TokenStatsUseCase;
import com.aicode.demo.domain.model.TokenStats;
import com.aicode.demo.dto.ApiResponse;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;

/**
 * Token 累计查询。只委托用例，不直接访问审计端口。
 */
@RestController
@RequestMapping("/api/v1/token-stats")
public class TokenStatsController {

    private final TokenStatsUseCase tokenStatsUseCase;

    public TokenStatsController(TokenStatsUseCase tokenStatsUseCase) {
        this.tokenStatsUseCase = tokenStatsUseCase;
    }

    /**
     * @return 200，data 为累计请求数与 Token
     */
    @GetMapping
    public ApiResponse<TokenStats> stats() {
        return ApiResponse.success(tokenStatsUseCase.summary());
    }
}
