package com.aicode.framework.observability.controller;

import cn.dev33.satoken.stp.StpUtil;
import com.aicode.framework.dto.ApiResponse;
import com.aicode.framework.observability.application.PlatformMonitoringUseCase;
import com.aicode.framework.observability.dto.MonitoringOverviewResponse;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;

/**
 * 全栈监控自检 API（Week 20）。
 *
 * <p>把 OTel / Prometheus / Grafana / SkyWalking / Langfuse / 网关指标六个组件的状态
 * 与跨后端关联信息一次返回，替代此前要分别调 {@code trace-context}、{@code langfuse-status}、
 * {@code skywalking-status} 才能拼出全貌的做法。</p>
 *
 * <p>权限：仅管理员（会暴露内部地址与拓扑）。Controller 只做登录校验与协议转换，
 * 管理员判定在用例层（规范 5.2）。</p>
 */
@RestController
@RequestMapping("/api/v1/platform/observability")
public class PlatformMonitoringController {

    private final PlatformMonitoringUseCase platformMonitoringUseCase;

    /**
     * @param platformMonitoringUseCase 监控概览用例
     */
    public PlatformMonitoringController(PlatformMonitoringUseCase platformMonitoringUseCase) {
        this.platformMonitoringUseCase = platformMonitoringUseCase;
    }

    /**
     * 监控概览（管理员）。
     *
     * @return 统一信封包装的概览；未登录 401，非管理员 403
     */
    @GetMapping("/monitoring-overview")
    public ApiResponse<MonitoringOverviewResponse> monitoringOverview() {
        StpUtil.checkLogin();
        return ApiResponse.success(MonitoringOverviewResponse.from(
                platformMonitoringUseCase.overview(StpUtil.getLoginIdAsLong())));
    }
}
