package com.aicode.enterprise.controller;

import com.aicode.enterprise.domain.port.IngestJobPort;
import com.aicode.enterprise.dto.ApiResponse;
import com.aicode.enterprise.infrastructure.sso.AdminTokenGuard;
import org.springframework.web.bind.annotation.DeleteMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.RequestHeader;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;

/**
 * 入库作业运维 API（Week 33，A1 运维令牌）。
 *
 * <p>仅 {@code X-Admin-Token}；提供"清空某作业 prompt 原文"的治理入口
 * （永久保留不等于不可删）。</p>
 */
@RestController
@RequestMapping("/api/v1/admin/ingest-jobs")
public class AdminIngestJobController {

    private final IngestJobPort ingestJobPort;
    private final AdminTokenGuard tokenGuard;

    public AdminIngestJobController(IngestJobPort ingestJobPort, AdminTokenGuard tokenGuard) {
        this.ingestJobPort = ingestJobPort;
        this.tokenGuard = tokenGuard;
    }

    /**
     * 清空某作业所有步骤的 prompt 原文。
     *
     * @param token 运维令牌
     * @param jobId 作业
     * @return 清空的步骤数
     */
    @DeleteMapping("/{jobId}/prompt")
    public ApiResponse<Integer> purgePrompt(
            @RequestHeader(value = "X-Admin-Token", required = false) String token,
            @PathVariable String jobId
    ) {
        tokenGuard.verify(token);
        return ApiResponse.success(ingestJobPort.clearPrompt(jobId));
    }
}
