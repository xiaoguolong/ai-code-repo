package com.aicode.framework.platform.dto;

import java.util.Map;

/**
 * 平台 Agent 运行请求。
 */
public record PlatformRunRequest(
        Map<String, Object> input
) {
}
