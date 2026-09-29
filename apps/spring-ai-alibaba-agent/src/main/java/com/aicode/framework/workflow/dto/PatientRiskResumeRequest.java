package com.aicode.framework.workflow.dto;

import jakarta.validation.constraints.NotNull;

/**
 * 人工审核恢复请求。approved=true 继续执行；false 终止流程。
 */
public record PatientRiskResumeRequest(
        @NotNull(message = "approved 不能为空") Boolean approved
) {
}
