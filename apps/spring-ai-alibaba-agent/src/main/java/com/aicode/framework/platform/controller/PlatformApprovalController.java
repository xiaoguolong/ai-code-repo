package com.aicode.framework.platform.controller;

import cn.dev33.satoken.stp.StpUtil;
import com.aicode.core.domain.exception.InvalidChatRequestException;
import com.aicode.framework.dto.ApiResponse;
import com.aicode.framework.platform.application.ApprovalDecision;
import com.aicode.framework.platform.application.PlatformApprovalDecisionUseCase;
import com.aicode.framework.platform.application.PlatformApprovalUseCase;
import com.aicode.framework.platform.domain.model.ApprovalStatus;
import com.aicode.framework.platform.domain.model.PlatformApproval;
import com.aicode.framework.platform.dto.ApprovalDecisionRequest;
import com.aicode.framework.platform.dto.PlatformApprovalListResponse;
import com.aicode.framework.platform.dto.PlatformApprovalResponse;
import jakarta.validation.Valid;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RequestParam;
import org.springframework.web.bind.annotation.RestController;

import java.util.List;
import java.util.Locale;

/**
 * 患者分析人工审批 API（Week 23）。
 *
 * <p>普通用户只看自己的待办；admin 可看全部。审批详情不返回报告正文，报告按
 * {@code executionId} 走既有执行记录接口取。</p>
 */
@RestController
@RequestMapping("/api/v1/platform/approvals")
public class PlatformApprovalController {

    private final PlatformApprovalUseCase platformApprovalUseCase;
    private final PlatformApprovalDecisionUseCase platformApprovalDecisionUseCase;

    public PlatformApprovalController(
            PlatformApprovalUseCase platformApprovalUseCase,
            PlatformApprovalDecisionUseCase platformApprovalDecisionUseCase
    ) {
        this.platformApprovalUseCase = platformApprovalUseCase;
        this.platformApprovalDecisionUseCase = platformApprovalDecisionUseCase;
    }

    /**
     * 列出审批任务。
     *
     * @param status 状态：PENDING（默认）/ APPROVED / REJECTED；非法值 400
     * @param limit  条数上限，默认 20、上限 100
     * @return 审批任务列表
     */
    @GetMapping
    public ApiResponse<PlatformApprovalListResponse> list(
            @RequestParam(defaultValue = "PENDING") String status,
            @RequestParam(defaultValue = "20") int limit
    ) {
        StpUtil.checkLogin();
        long callerUserId = StpUtil.getLoginIdAsLong();
        List<PlatformApproval> approvals = platformApprovalUseCase.list(
                callerUserId, parseStatus(status), limit);
        return ApiResponse.success(PlatformApprovalListResponse.from(approvals));
    }

    /**
     * 查询审批详情（本人或 admin）。
     *
     * @param approvalId 审批任务 ID
     * @return 审批任务
     */
    @GetMapping("/{approvalId}")
    public ApiResponse<PlatformApprovalResponse> get(@PathVariable String approvalId) {
        StpUtil.checkLogin();
        long callerUserId = StpUtil.getLoginIdAsLong();
        return ApiResponse.success(
                PlatformApprovalResponse.from(platformApprovalUseCase.get(callerUserId, approvalId)));
    }

    /**
     * 批准 / 拒绝审批任务。
     *
     * @param approvalId 审批任务 ID
     * @param request    决策请求
     * @return 审批终态 + 执行终态 + memoryId
     */
    @PostMapping("/{approvalId}/decision")
    public ApiResponse<PlatformApprovalListResponse.Decision> decide(
            @PathVariable String approvalId,
            @Valid @RequestBody ApprovalDecisionRequest request
    ) {
        StpUtil.checkLogin();
        long callerUserId = StpUtil.getLoginIdAsLong();
        ApprovalDecision decision = platformApprovalDecisionUseCase.decide(
                callerUserId, approvalId, Boolean.TRUE.equals(request.approved()), request.comment());
        return ApiResponse.success(PlatformApprovalListResponse.Decision.from(decision));
    }

    private ApprovalStatus parseStatus(String status) {
        String normalized = status == null ? "" : status.trim().toUpperCase(Locale.ROOT);
        try {
            return ApprovalStatus.valueOf(normalized);
        } catch (IllegalArgumentException ex) {
            throw new InvalidChatRequestException("status must be one of PENDING, APPROVED, REJECTED");
        }
    }
}
