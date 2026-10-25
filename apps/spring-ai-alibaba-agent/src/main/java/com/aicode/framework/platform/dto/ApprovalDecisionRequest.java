package com.aicode.framework.platform.dto;

import jakarta.validation.constraints.NotNull;
import jakarta.validation.constraints.Size;

/**
 * 审批决策请求（Week 23）。
 *
 * @param approved true=批准（写随访记忆），false=拒绝；**必填**，缺失时 400
 * @param comment  审批意见，可空，最长 500
 */
public record ApprovalDecisionRequest(
        @NotNull(message = "approved must not be null") Boolean approved,
        @Size(max = 500, message = "comment must not exceed 500 characters") String comment
) {
}
