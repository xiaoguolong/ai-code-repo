package com.aicode.gateway.infrastructure.feign;

import com.fasterxml.jackson.annotation.JsonIgnoreProperties;

import java.util.Map;

/**
 * 平台 `/runs` 响应 data 的 Feign 侧形态（Week 19）。
 *
 * <p>网关<b>不解析</b>执行记录的业务字段（那是平台契约），只原样透传；
 * 用 {@code Map<String,Object>} 承载可避免网关随平台字段演进而频繁改动。</p>
 *
 * @param payload 执行记录字段
 */
@JsonIgnoreProperties(ignoreUnknown = true)
public record FeignRunPayload(Map<String, Object> payload) {
}
