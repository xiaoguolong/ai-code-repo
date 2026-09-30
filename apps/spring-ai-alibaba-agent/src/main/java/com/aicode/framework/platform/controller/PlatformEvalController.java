package com.aicode.framework.platform.controller;

import cn.dev33.satoken.stp.StpUtil;
import com.aicode.framework.dto.ApiResponse;
import com.aicode.framework.platform.application.PlatformEvalUseCase;
import com.aicode.framework.platform.dto.EvalDatasetDetailResponse;
import com.aicode.framework.platform.dto.EvalDatasetResponse;
import com.aicode.framework.platform.dto.EvalReportResponse;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;

import java.util.List;

/**
 * 平台 Agent 评估 API：测试集查询与批量自动评分。
 */
@RestController
@RequestMapping("/api/v1/platform/eval")
public class PlatformEvalController {

    private final PlatformEvalUseCase platformEvalUseCase;

    public PlatformEvalController(PlatformEvalUseCase platformEvalUseCase) {
        this.platformEvalUseCase = platformEvalUseCase;
    }

    @GetMapping("/datasets")
    public ApiResponse<List<EvalDatasetResponse>> listDatasets() {
        StpUtil.checkLogin();
        List<EvalDatasetResponse> datasets = platformEvalUseCase.listDatasets().stream()
                .map(EvalDatasetResponse::from)
                .toList();
        return ApiResponse.success(datasets);
    }

    @GetMapping("/datasets/{datasetKey}")
    public ApiResponse<EvalDatasetDetailResponse> getDataset(@PathVariable String datasetKey) {
        StpUtil.checkLogin();
        return ApiResponse.success(
                EvalDatasetDetailResponse.from(platformEvalUseCase.getDataset(datasetKey)));
    }

    @PostMapping("/datasets/{datasetKey}/runs")
    public ApiResponse<EvalReportResponse> runDataset(@PathVariable String datasetKey) {
        StpUtil.checkLogin();
        long userId = StpUtil.getLoginIdAsLong();
        return ApiResponse.success(
                EvalReportResponse.from(platformEvalUseCase.runDataset(userId, datasetKey)));
    }

    @GetMapping("/runs/{runId}")
    public ApiResponse<EvalReportResponse> getRun(@PathVariable String runId) {
        StpUtil.checkLogin();
        long userId = StpUtil.getLoginIdAsLong();
        return ApiResponse.success(
                EvalReportResponse.from(platformEvalUseCase.getRun(userId, runId)));
    }
}
