package com.aicode.gateway.dto;

import java.util.Map;

/**
 * Agent 执行结果响应（Week 19）。
 *
 * <p>网关<b>不重写</b>平台返回的执行记录字段（那是平台契约），只做一层包封，
 * 保证「平台加字段网关不用跟着发布」。</p>
 *
 * @param execution 平台执行记录字段原样透传
 */
public record AgentRunResponse(Map<String, Object> execution) {
}
